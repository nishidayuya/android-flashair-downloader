package org.j96.flashairdownloader.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.j96.flashairdownloader.R
import org.j96.flashairdownloader.domain.model.SyncProgress
import org.j96.flashairdownloader.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The notification that makes the transfer a foreground service, and the one
 * that reports the result afterwards (docs/design.md 3.5).
 */
@Singleton
class SyncNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sync_notification_channel),
                // The progress notification is not something to interrupt for.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun progress(progress: SyncProgress): Notification {
        val builder = baseBuilder()
            .setContentTitle(context.getString(R.string.sync_notification_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                0,
                context.getString(R.string.sync_cancel),
                PendingIntent.getService(
                    context,
                    REQUEST_CANCEL,
                    SyncForegroundService.cancelIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        when (progress.state) {
            SyncProgress.State.SCANNING -> {
                builder.setContentText(context.getString(R.string.sync_scanning_count, progress.scannedFiles))
                builder.setIndeterminateProgress()
            }

            SyncProgress.State.DOWNLOADING -> {
                builder.setContentText(progress.currentFile.orEmpty())
                builder.setSubText("${progress.completedFiles} / ${progress.totalFiles}")
                builder.setProgress(progress.totalFiles, progress.completedFiles, progress.totalFiles == 0)
            }

            SyncProgress.State.WAITING_FOR_NETWORK -> {
                builder.setContentText(context.getString(R.string.sync_waiting_for_network))
                builder.setIndeterminateProgress()
            }

            else -> builder.setIndeterminateProgress()
        }
        return builder.build()
    }

    /** Replaces the ongoing notification with the current state of the run. */
    fun update(progress: SyncProgress) {
        manager?.notify(PROGRESS_NOTIFICATION_ID, progress(progress))
    }

    /** Shown once the run is over, since the ongoing notification goes away with the service. */
    fun notifyResult(progress: SyncProgress) {
        val text = when {
            progress.failure != null -> context.getString(R.string.sync_result_failed)
            progress.failures.isNotEmpty() -> context.getString(
                R.string.sync_result_partial,
                progress.completedFiles,
                progress.failures.size,
            )
            else -> context.getString(R.string.sync_result_done, progress.completedFiles)
        }
        manager?.notify(
            RESULT_NOTIFICATION_ID,
            baseBuilder()
                .setContentTitle(context.getString(R.string.sync_notification_title))
                .setContentText(text)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun NotificationCompat.Builder.setIndeterminateProgress() = setProgress(0, 0, true)

    private fun baseBuilder() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

    companion object {
        const val CHANNEL_ID = "sync"
        const val PROGRESS_NOTIFICATION_ID = 1
        const val RESULT_NOTIFICATION_ID = 2

        private const val REQUEST_CANCEL = 1
        private const val REQUEST_OPEN = 2
    }
}
