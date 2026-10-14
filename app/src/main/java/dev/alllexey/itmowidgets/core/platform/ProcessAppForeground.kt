package dev.alllexey.itmowidgets.core.platform

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * [AppForeground] from the process lifecycle ([ProcessLifecycleOwner]): a return is the process reaching STARTED again
 * after it stopped, so an activity's own pause (a permission dialog, the sign-in activity) is not one.
 */
class ProcessAppForeground(
    private val lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
) : AppForeground {
    override fun returns(): Flow<Unit> = lifecycle.currentStateFlow
        .map { it.isAtLeast(Lifecycle.State.STARTED) }
        .distinctUntilChanged()
        .drop(1)
        .filter { it }
        .map { }
}
