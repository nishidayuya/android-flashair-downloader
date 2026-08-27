package io.github.nishidayuya.flashairdownloader.ui.sync

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nishidayuya.flashairdownloader.domain.model.SyncProgress
import io.github.nishidayuya.flashairdownloader.sync.SyncController
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val controller: SyncController,
) : ViewModel() {
    /** The same flow the notification reads, so the two can never disagree. */
    val progress: StateFlow<SyncProgress> = controller.progress

    fun cancel() = controller.cancel()

    fun acknowledge() = controller.acknowledge()
}
