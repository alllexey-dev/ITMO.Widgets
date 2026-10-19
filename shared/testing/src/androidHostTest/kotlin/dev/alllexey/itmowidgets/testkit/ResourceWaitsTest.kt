package dev.alllexey.itmowidgets.testkit

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ResourceWaitsTest {
    @Test
    fun awaitTextFindsATextLoadedOffTheTrackedDispatchers() = runComposeUiTest {
        var label by mutableStateOf("")
        setContent {
            LaunchedEffect(Unit) { label = loadOffTheTrackedDispatchers(LOADED) }
            BasicText(label)
        }

        awaitText(LOADED).assertExists()
    }

    @Test
    fun awaitTextFailsNamingATextThatNeverAppears() = runComposeUiTest {
        setContent { BasicText(LOADED) }

        val failure = assertFailsWith<ComposeTimeoutException> { awaitText(MISSING, timeoutMillis = SHORT_TIMEOUT_MS) }
        assertContains(failure.message.orEmpty(), MISSING)
    }

    @Test
    fun awaitResourceReturnsTheValueOnceItIsLoaded() = runComposeUiTest {
        var message: String? by mutableStateOf(null)
        setContent { LaunchedEffect(Unit) { message = loadOffTheTrackedDispatchers(LOADED) } }

        assertEquals(LOADED, awaitResource("the message is loaded") { message })
    }

    /** What Compose resources' `getString` does: the value arrives from a dispatcher Compose idling does not see. */
    private suspend fun loadOffTheTrackedDispatchers(value: String): String = withContext(Dispatchers.Default) {
        delay(LOAD_DELAY_MS)
        value
    }

    private companion object {
        const val LOADED = "Записали"
        const val MISSING = "Не записали"
        const val LOAD_DELAY_MS = 200L
        const val SHORT_TIMEOUT_MS = 100L
    }
}
