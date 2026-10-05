package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.services.BackendGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The Backend opt-in of one test with the answers of `DefaultBackendGate` (`BackendGateTest`).
 * [beforeAnswer] suspends a [mayCallBackend] read after the flag was taken, for the race tests.
 */
class FakeBackendGate(optedIn: Boolean, private val demo: DemoMode = noDemo()) : BackendGate {
    val optedIn = MutableStateFlow(optedIn)
    var beforeAnswer: (suspend () -> Unit)? = null

    override suspend fun isConnected(): Boolean = demo.isActive() || optedIn.value

    override fun observeConnected(): Flow<Boolean> =
        combine(demo.observeActive(), optedIn) { demo, optedIn -> demo || optedIn }.distinctUntilChanged()

    override suspend fun mayCallBackend(): Boolean {
        val answer = !demo.isActive() && optedIn.value
        beforeAnswer?.invoke()
        return answer
    }

    override suspend fun isOptedIn(): Boolean = optedIn.value
}
