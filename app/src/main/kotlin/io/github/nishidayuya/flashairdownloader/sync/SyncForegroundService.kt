package io.github.nishidayuya.flashairdownloader.sync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.nishidayuya.flashairdownloader.domain.model.SyncProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps a sync going while the screen is off or the app is in the background.
 *
 * The transfer itself lives in [SyncController]; this service only holds the
 * process up, holds the Wi-Fi and CPU awake, and turns [SyncController.progress]
 * into a notification. WorkManager is deliberately not used: the run is tied to
 * a Wi-Fi network that only exists while the card is in range, which does not
 * survive being rescheduled by the system (docs/design.md 3.5).
 */
@AndroidEntryPoint
class SyncForegroundService : Service() {
    @Inject
    lateinit var controller: SyncController

    @Inject
    lateinit var notifications: SyncNotifications

    private val scope = CoroutineScope(SupervisorJob())
    private var observer: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            controller.cancel()
            return START_NOT_STICKY
        }

        notifications.createChannel()
        ServiceCompat.startForeground(
            this,
            SyncNotifications.PROGRESS_NOTIFICATION_ID,
            notifications.progress(controller.progress.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        if (observer == null) {
            observer = scope.launch {
                acquireLocks()
                try {
                    controller.progress.collect { progress -> onProgress(progress) }
                } finally {
                    releaseLocks()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun onProgress(progress: SyncProgress) {
        when {
            progress.state.isTerminal -> {
                notifications.notifyResult(progress)
                stopSelf()
            }

            progress.state == SyncProgress.State.IDLE -> stopSelf()
            else -> notifications.update(progress)
        }
    }

    override fun onDestroy() {
        observer?.cancel()
        observer = null
        scope.cancel()
        // The locks are released by the collector's finally, but a service that
        // is torn down without the coroutine ever starting must not leak them.
        releaseLocks()
        super.onDestroy()
    }

    /**
     * A long transfer must not be cut short by Wi-Fi power saving or by the CPU
     * going to sleep (docs/design.md 3.5).
     */
    private fun acquireLocks() {
        val wifiManager = getSystemService(WifiManager::class.java)
        // WIFI_MODE_FULL_LOW_LATENCY rather than the deprecated
        // WIFI_MODE_FULL_HIGH_PERF. What the lock still buys on a modern
        // Android is that Wi-Fi power saving stays off while a transfer runs;
        // the low latency part of it only applies while the app is in the
        // foreground with the screen on, and the lock falls back to the high
        // performance behaviour the rest of the time, which is the case that
        // matters here.
        wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, WIFI_LOCK_TAG)
            ?.also { it.acquire() }
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.also { it.acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
    }

    private fun releaseLocks() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val ACTION_CANCEL = "io.github.nishidayuya.flashairdownloader.action.CANCEL_SYNC"
        private const val WIFI_LOCK_TAG = "FlashAirDownloader:sync"
        private const val WAKE_LOCK_TAG = "FlashAirDownloader:sync"

        /** A backstop, so a wedged run cannot hold the CPU awake forever. */
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 6L * 60 * 60 * 1000

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SyncForegroundService::class.java))
        }

        fun cancelIntent(context: Context): Intent =
            Intent(context, SyncForegroundService::class.java).setAction(ACTION_CANCEL)
    }
}
