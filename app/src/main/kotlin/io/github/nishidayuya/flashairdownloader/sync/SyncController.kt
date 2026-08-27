package io.github.nishidayuya.flashairdownloader.sync

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.nishidayuya.flashairdownloader.data.local.SettingsDataStore
import io.github.nishidayuya.flashairdownloader.di.ApplicationScope
import io.github.nishidayuya.flashairdownloader.domain.model.FlashAirFailure
import io.github.nishidayuya.flashairdownloader.domain.model.SyncFailure
import io.github.nishidayuya.flashairdownloader.domain.model.SyncProgress
import io.github.nishidayuya.flashairdownloader.domain.storage.DownloadStore
import io.github.nishidayuya.flashairdownloader.domain.usecase.BuildSyncPlanUseCase
import io.github.nishidayuya.flashairdownloader.domain.usecase.DownloadFilesUseCase
import io.github.nishidayuya.flashairdownloader.domain.usecase.ProbeCardUseCase
import io.github.nishidayuya.flashairdownloader.domain.usecase.ScanRemoteFilesUseCase
import io.github.nishidayuya.flashairdownloader.net.FlashAirNetworkProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a sync and publishes its progress.
 *
 * The work belongs to the process, not to a screen or to the service: the UI can
 * come and go, and [SyncForegroundService] only keeps the process alive and
 * mirrors [progress] into a notification (docs/design.md 5, 7).
 */
@Singleton
class SyncController @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val networkProvider: FlashAirNetworkProvider,
    private val probeCard: ProbeCardUseCase,
    private val scanRemoteFiles: ScanRemoteFilesUseCase,
    private val buildSyncPlan: BuildSyncPlanUseCase,
    private val downloadFiles: DownloadFilesUseCase,
    private val store: DownloadStore,
    private val settings: SettingsDataStore,
    private val clock: Clock,
) {
    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    private var job: Job? = null
    private var startedAtMillis = 0L

    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (isRunning) return
        _progress.value = SyncProgress(state = SyncProgress.State.PROBING)
        startedAtMillis = clock.millis()
        SyncForegroundService.start(context)
        job = scope.launch {
            try {
                run()
            } catch (cancellation: CancellationException) {
                _progress.update { it.copy(state = SyncProgress.State.CANCELLED, currentFile = null) }
                throw cancellation
            } catch (failure: IOException) {
                Log.w(TAG, "the sync could not go on", failure)
                _progress.update {
                    it.copy(
                        state = SyncProgress.State.FAILED,
                        failure = FlashAirFailure.of(failure),
                        currentFile = null,
                    )
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Puts the progress back to idle once the user has seen the result. */
    fun acknowledge() {
        if (!isRunning && _progress.value.state.isTerminal) {
            _progress.value = SyncProgress()
        }
    }

    private suspend fun run() {
        val session = store.openSession()
        if (session == null) {
            _progress.update {
                it.copy(state = SyncProgress.State.FAILED, failure = FlashAirFailure.STORAGE_ERROR)
            }
            return
        }

        val card = probeCard()
        val root = settings.targetDirectory.first()

        _progress.update { it.copy(state = SyncProgress.State.SCANNING) }
        val scan = scanRemoteFiles(root) { directory, filesSoFar ->
            _progress.update { it.copy(scannedDirectory = directory, scannedFiles = filesSoFar) }
        }

        val plan = buildSyncPlan(
            cardId = card.id,
            root = root,
            scan = scan,
            extensionFilter = settings.extensionFilter.first(),
        )
        _progress.update {
            it.copy(
                state = SyncProgress.State.DOWNLOADING,
                totalFiles = plan.files.size,
                totalBytes = plan.totalBytes,
                unchangedFiles = plan.unchangedCount,
                stoppedEarly = plan.stoppedEarly,
            )
        }

        val result = downloadFiles(
            plan = plan,
            session = session,
            concurrency = settings.maxParallelDownloads.first(),
            awaitConnection = ::awaitConnection,
            onEvent = { event -> onDownloadEvent(event) },
        )
        _progress.update {
            it.copy(
                state = SyncProgress.State.FINISHED,
                completedFiles = result.downloaded + result.alreadyPresent,
                alreadyPresentFiles = result.alreadyPresent,
                failures = result.failures,
                currentFile = null,
            )
        }
    }

    /** Suspends while the card's Wi-Fi is away, so a sync pauses instead of failing. */
    private suspend fun awaitConnection() {
        if (networkProvider.network.value != null) return
        _progress.update { it.copy(state = SyncProgress.State.WAITING_FOR_NETWORK) }
        networkProvider.network.filterNotNull().first()
        _progress.update { it.copy(state = SyncProgress.State.DOWNLOADING) }
    }

    private fun onDownloadEvent(event: DownloadFilesUseCase.Event) {
        _progress.update { current ->
            when (event) {
                is DownloadFilesUseCase.Event.Started ->
                    current.copy(currentFile = event.entry.path)

                is DownloadFilesUseCase.Event.Transferred ->
                    current.withTransferred(event.bytes)

                is DownloadFilesUseCase.Event.Finished -> current.copy(
                    completedFiles = current.completedFiles + 1,
                    alreadyPresentFiles = current.alreadyPresentFiles +
                        if (event.alreadyPresent) 1 else 0,
                )

                is DownloadFilesUseCase.Event.Failed ->
                    current.copy(
                        failures = current.failures + SyncFailure(event.entry.path, event.failure),
                    )
            }
        }
    }

    private fun SyncProgress.withTransferred(bytes: Long): SyncProgress {
        val transferred = transferredBytes + bytes
        val elapsedMillis = (clock.millis() - startedAtMillis).coerceAtLeast(1)
        return copy(
            transferredBytes = transferred,
            bytesPerSecond = transferred * MILLIS_PER_SECOND / elapsedMillis,
        )
    }

    private companion object {
        const val TAG = "FlashAirSync"
        const val MILLIS_PER_SECOND = 1_000L
    }
}
