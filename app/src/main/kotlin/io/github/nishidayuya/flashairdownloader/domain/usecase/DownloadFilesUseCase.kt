package io.github.nishidayuya.flashairdownloader.domain.usecase

import io.github.nishidayuya.flashairdownloader.data.flashair.FlashAirApi
import io.github.nishidayuya.flashairdownloader.data.flashair.model.FlashAirEntry
import io.github.nishidayuya.flashairdownloader.data.local.DownloadRecordDao
import io.github.nishidayuya.flashairdownloader.data.local.DownloadRecordEntity
import io.github.nishidayuya.flashairdownloader.domain.model.FlashAirFailure
import io.github.nishidayuya.flashairdownloader.domain.model.SyncFailure
import io.github.nishidayuya.flashairdownloader.domain.model.SyncPlan
import io.github.nishidayuya.flashairdownloader.domain.model.toEpochSeconds
import io.github.nishidayuya.flashairdownloader.domain.storage.DownloadSession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Clock
import javax.inject.Inject

/**
 * Fetches the files a [SyncPlan] asks for.
 *
 * One failing file never stops the run: it is retried a few times, then recorded
 * as a failure and the rest continues (docs/design.md 7). Each finished file is
 * recorded straight away, so an interrupted run does not repeat itself.
 */
class DownloadFilesUseCase @Inject constructor(
    private val api: FlashAirApi,
    private val records: DownloadRecordDao,
    private val clock: Clock,
) {
    sealed interface Event {
        data class Started(val entry: FlashAirEntry) : Event

        data class Transferred(val bytes: Long) : Event

        data class Finished(val entry: FlashAirEntry, val alreadyPresent: Boolean) : Event

        data class Failed(val entry: FlashAirEntry, val failure: FlashAirFailure) : Event
    }

    data class Result(val downloaded: Int, val alreadyPresent: Int, val failures: List<SyncFailure>)

    /**
     * @param concurrency how many files to fetch at once; the card's HTTP server
     *   copes with very few (docs/design.md 2.5).
     * @param awaitConnection called before each attempt, so that losing the
     *   card's Wi-Fi pauses the run instead of failing every remaining file.
     */
    suspend operator fun invoke(
        plan: SyncPlan,
        session: DownloadSession,
        concurrency: Int = 1,
        awaitConnection: suspend () -> Unit = {},
        onEvent: suspend (Event) -> Unit = {},
    ): Result {
        val run = Run(plan, session, awaitConnection, onEvent)
        coroutineScope {
            // A worker pool rather than one coroutine per file: the plan can hold
            // thousands of entries, and only a couple may be in flight.
            val queue = Channel<FlashAirEntry>(Channel.RENDEZVOUS)
            repeat(concurrency.coerceAtLeast(1)) {
                launch {
                    for (entry in queue) run.transfer(entry)
                }
            }
            plan.files.forEach { queue.send(it) }
            queue.close()
        }
        return run.result()
    }

    /** One sync run: the shared context of every file being transferred. */
    private inner class Run(
        private val plan: SyncPlan,
        private val session: DownloadSession,
        private val awaitConnection: suspend () -> Unit,
        private val onEvent: suspend (Event) -> Unit,
    ) {
        private val mutex = Mutex()
        private var downloaded = 0
        private var alreadyPresent = 0
        private val failures = mutableListOf<SyncFailure>()

        fun result() = Result(downloaded, alreadyPresent, failures.toList())

        suspend fun transfer(entry: FlashAirEntry) {
            val directory = relativeDirectory(entry)
            awaitConnection()

            if (session.list(directory)[entry.name] == entry.size) {
                // The file is already sitting in the destination -- most likely
                // the records were reset. Recording it is enough.
                record(entry, localUri = null)
                mutex.withLock { alreadyPresent++ }
                onEvent(Event.Finished(entry, alreadyPresent = true))
                return
            }

            onEvent(Event.Started(entry))
            val failure = attempt(entry, directory) ?: return
            val classified = FlashAirFailure.of(failure)
            mutex.withLock { failures += SyncFailure(entry.path, classified) }
            onEvent(Event.Failed(entry, classified))
        }

        /** @return the last failure, or null once the file is through. */
        private suspend fun attempt(entry: FlashAirEntry, directory: String): IOException? {
            var lastFailure: IOException? = null
            for (attempt in 0 until MAX_ATTEMPTS) {
                if (attempt > 0) {
                    delay(RETRY_BACKOFF_MILLIS shl (attempt - 1))
                    awaitConnection()
                }
                try {
                    val uri = download(directory, entry)
                    record(entry, uri)
                    mutex.withLock { downloaded++ }
                    onEvent(Event.Finished(entry, alreadyPresent = false))
                    return null
                } catch (failure: IOException) {
                    lastFailure = failure
                }
            }
            return requireNotNull(lastFailure)
        }

        /** Streams one file into the destination, reporting bytes as they arrive. */
        private suspend fun download(directory: String, entry: FlashAirEntry): String =
            session.write(directory, entry.name) { sink ->
                api.openFile(entry.path).use { response ->
                    response.body.source().use { source ->
                        while (true) {
                            // Copying is blocking work with no suspension point of
                            // its own, so cancelling the sync would otherwise not
                            // be noticed until the whole file was through.
                            currentCoroutineContext().ensureActive()
                            val read = source.read(sink.buffer, CHUNK_SIZE)
                            if (read == -1L) break
                            sink.emitCompleteSegments()
                            onEvent(Event.Transferred(read))
                        }
                    }
                }
            }

        private suspend fun record(entry: FlashAirEntry, localUri: String?) {
            records.upsert(
                DownloadRecordEntity(
                    cardId = plan.cardId,
                    path = entry.path,
                    size = entry.size,
                    modifiedAtEpoch = entry.modifiedAt?.toEpochSeconds(),
                    downloadedAtEpoch = clock.instant().epochSecond,
                    localUri = localUri,
                ),
            )
        }

        /**
         * Where the file goes, relative to the destination the user picked: the
         * card's own layout below the plan's root is mirrored, so
         * "/DCIM/100__TSB" under root "/DCIM" becomes "100__TSB".
         */
        private fun relativeDirectory(entry: FlashAirEntry): String =
            entry.directory.removePrefix(plan.root).trim('/')
    }

    private companion object {
        const val MAX_ATTEMPTS = 3

        /** 1s, then 2s, then 4s (docs/design.md 7). */
        const val RETRY_BACKOFF_MILLIS = 1_000L
        const val CHUNK_SIZE = 64L * 1024
    }
}
