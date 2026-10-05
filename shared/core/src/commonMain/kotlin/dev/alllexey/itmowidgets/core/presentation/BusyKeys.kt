package dev.alllexey.itmowidgets.core.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Per-row actions: a key (a row id) is busy from the tap until its action completes, however it completes.
 *
 * The key is claimed synchronously, so a second tap on the same row is ignored before the first action is even
 * dispatched. Rows derive their progress and disabled state from [busy].
 */
class BusyKeys<K>(private val scope: CoroutineScope) {

    private val keys = MutableStateFlow<Set<K>>(emptySet())

    val busy: StateFlow<Set<K>> = keys.asStateFlow()

    operator fun contains(key: K): Boolean = key in keys.value

    /** Runs [action] for [key] unless the key is already busy; returns `null` when the tap was ignored. */
    fun launch(key: K, action: suspend () -> Unit): Job? {
        if (!claim(key)) return null
        val job = scope.launch(start = CoroutineStart.LAZY) { action() }
        job.invokeOnCompletion { keys.update { it - key } }
        job.start()
        return job
    }

    private fun claim(key: K): Boolean {
        while (true) {
            val claimed = keys.value
            if (key in claimed) return false
            if (keys.compareAndSet(claimed, claimed + key)) return true
        }
    }
}
