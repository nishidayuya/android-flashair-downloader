package org.j96.flashairdownloader.ui.sync

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import org.j96.flashairdownloader.domain.model.SyncProgress
import org.j96.flashairdownloader.sync.SyncController
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
