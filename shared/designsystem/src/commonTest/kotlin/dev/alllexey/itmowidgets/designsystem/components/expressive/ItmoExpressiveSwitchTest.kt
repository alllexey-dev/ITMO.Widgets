package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoExpressiveSwitchTest {
    @Test
    fun switchIsOffByDefaultAndHeroesAnimateLikeComponents() = runComposeUiTest {
        var expressive: Boolean? = null
        var hero: MotionScheme? = null
        var components: MotionScheme? = null
        setContent {
            ItmoTheme {
                expressive = ItmoTheme.expressive
                hero = ItmoTheme.heroMotionScheme
                components = MaterialTheme.motionScheme
            }
        }

        assertEquals(false, expressive)
        assertSame(components, hero)
    }

    @Test
    fun switchOnGivesHeroesTheirOwnSchemeAndKeepsComponentsStandard() = runComposeUiTest {
        var expressive = false
        var hero: MotionScheme? = null
        var components: MotionScheme? = null
        var standard: MotionScheme? = null
        setContent {
            ItmoTheme { standard = MaterialTheme.motionScheme }
            // The iOS style ignores the switch (ItmoPlatformStyleThemeTest).
            ItmoTheme(expressive = true, platformStyle = ItmoPlatformStyle.Material) {
                expressive = ItmoTheme.expressive
                hero = ItmoTheme.heroMotionScheme
                components = MaterialTheme.motionScheme
            }
        }

        assertTrue(expressive)
        assertSame(standard, components)
        assertNotSame(components, hero)
    }

    @Test
    fun loadingIndicatorIsIndeterminateInBothStates() = runComposeUiTest {
        var on by mutableStateOf(false)
        setContent { ItmoTheme(expressive = on) { ItmoLoadingIndicator() } }

        listOf(false, true).forEach {
            on = it
            onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists("expressive = $it")
        }
    }

    @Test
    fun wavyProgressReportsItsValueInBothStates() = runComposeUiTest {
        var on by mutableStateOf(false)
        setContent {
            ItmoTheme(expressive = on) {
                Column {
                    ItmoWavyProgressShape.entries.forEach { ItmoWavyProgress(progress = { 0.6f }, shape = it) }
                }
            }
        }

        listOf(false, true).forEach {
            on = it
            onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.6f, 0f..1f)))
                .assertCountEquals(ItmoWavyProgressShape.entries.size)
        }
    }
}
