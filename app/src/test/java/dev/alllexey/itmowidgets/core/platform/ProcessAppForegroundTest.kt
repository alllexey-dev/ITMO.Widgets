package dev.alllexey.itmowidgets.core.platform

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** A return is the process starting again after a stop; the state it is in when collection starts is not one. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProcessAppForegroundTest {

    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    @Test
    fun `only a start after a stop is a return`() = runTest(UnconfinedTestDispatcher()) {
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        var returns = 0
        val collection = ProcessAppForeground(owner.lifecycle).returns().onEach { returns++ }.launchIn(backgroundScope)

        owner.lifecycle.currentState = Lifecycle.State.STARTED
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        assertEquals("pausing and resuming in the foreground", 0, returns)

        owner.lifecycle.currentState = Lifecycle.State.CREATED
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        assertEquals(1, returns)

        owner.lifecycle.currentState = Lifecycle.State.CREATED
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        assertEquals(2, returns)
        collection.cancel()
    }
}
