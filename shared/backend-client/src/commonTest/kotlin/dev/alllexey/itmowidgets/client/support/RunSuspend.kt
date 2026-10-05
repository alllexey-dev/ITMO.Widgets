package dev.alllexey.itmowidgets.client.support

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/** Runs a suspending test body; the client has no clock or delay, so no virtual time is needed. */
fun runSuspend(block: suspend CoroutineScope.() -> Unit) {
    runBlocking(block = block)
}
