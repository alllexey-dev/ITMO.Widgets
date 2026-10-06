package dev.alllexey.itmowidgets.feature.update.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.PendingAppUpdate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Decides once per process whether to open the update screen.
 *
 * The offer is buffered rather than dropped: the check can finish while the
 * activity is between states, and an update that was found must not be lost
 * because nobody was listening at that exact moment.
 */
class AppUpdateGateViewModel(
    private val pendingAppUpdate: PendingAppUpdate
) : ViewModel() {

    private val updates = Channel<AppUpdate>(Channel.CONFLATED)
    val events: Flow<AppUpdate> = updates.receiveAsFlow()
    private var checked = false

    fun checkForUpdate() {
        if (checked) return
        checked = true
        viewModelScope.launch {
            pendingAppUpdate()?.let { update -> updates.send(update) }
        }
    }
}
