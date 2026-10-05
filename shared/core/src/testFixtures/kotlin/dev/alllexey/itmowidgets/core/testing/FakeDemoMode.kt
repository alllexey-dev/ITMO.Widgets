package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.demo.DemoMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The demo switch of one test; off unless the test turns it on. */
class FakeDemoMode(active: Boolean = false) : DemoMode {
    val active = MutableStateFlow(active)

    override suspend fun isActive(): Boolean = active.value

    override fun observeActive(): Flow<Boolean> = active
}

/** A session that is not the demo. */
fun noDemo(): DemoMode = FakeDemoMode()
