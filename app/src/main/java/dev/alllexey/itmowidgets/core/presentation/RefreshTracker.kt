package dev.alllexey.itmowidgets.core.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs at most one refresh of a screen at a time and exposes whether the user should see it.
 *
 * A second request joins the refresh in flight instead of starting another one; a [RefreshMode.Pull] that joins a
 * silent refresh raises [refreshing] until it ends. Only [RefreshMode.Force] replaces a non-forced refresh: the old
 * one is cancelled and the new one starts after it has finished, so a stale answer never lands after a fresh one.
 *
 * The tracker never catches: a refresh that fails ends the run and propagates to [scope] as any other coroutine.
 * Screen state stays derived from the repository flow combined with [refreshing], never written as a transient
 * `Loading`.
 */
class RefreshTracker(private val scope: CoroutineScope) {

    private val current = MutableStateFlow<Run?>(null)
    private val indicator = MutableStateFlow(false)

    /** `true` while a refresh the user asked for (or joined) is in flight. */
    val refreshing: StateFlow<Boolean> = indicator.asStateFlow()

    /** Starts [refresh] with [mode], or joins the run in flight; returns the job doing the work. */
    fun launch(mode: RefreshMode, refresh: suspend (RefreshMode) -> Unit): Job {
        while (true) {
            val running = current.value
            val next = when {
                running == null -> start(mode, previous = null, refresh)
                mode == RefreshMode.Force && !running.forced -> start(mode, previous = running.job, refresh)
                mode.showsIndicator && !running.visible -> Run(running.job, running.forced, visible = true)
                else -> return running.job
            }
            if (current.compareAndSet(running, next)) {
                if (next.job !== running?.job) running?.job?.cancel()
                next.job.start()
                publish()
                return next.job
            }
            if (next.job !== running?.job) next.job.cancel()
        }
    }

    private fun start(mode: RefreshMode, previous: Job?, refresh: suspend (RefreshMode) -> Unit): Run {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            previous?.join()
            refresh(mode)
        }
        job.invokeOnCompletion { finish(job) }
        return Run(job, forced = mode == RefreshMode.Force, visible = mode.showsIndicator)
    }

    private fun finish(job: Job) {
        while (true) {
            val running = current.value
            if (running?.job !== job || current.compareAndSet(running, null)) break
        }
        publish()
    }

    /** Re-reads [current] after writing, so a concurrent transition can never leave a stale indicator behind. */
    private fun publish() {
        do {
            val running = current.value
            indicator.value = running?.visible == true
        } while (current.value !== running)
    }

    private class Run(val job: Job, val forced: Boolean, val visible: Boolean)
}
