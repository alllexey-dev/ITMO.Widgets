package dev.alllexey.itmowidgets.designsystem.platform

import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import dev.alllexey.itmowidgets.designsystem.theme.ColorSource
import dev.alllexey.itmowidgets.designsystem.theme.ItmoIosColors
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoShapes
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoSpacing
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What each platform style hands to the kit and to Material. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoPlatformStyleThemeTest {
    private class Seen(
        val style: ItmoPlatformStyle,
        val spacing: ItmoSpacing,
        val shapes: ItmoShapes,
        val typography: Typography,
        val iosColors: ItmoIosColors,
        val expressive: Boolean,
        val minTouch: Dp,
    )

    private fun seenIn(style: ItmoPlatformStyle, dark: Boolean = false, expressive: Boolean = false): Seen {
        var seen: Seen? = null
        runComposeUiTest {
            setContent {
                ItmoTheme(dark, ColorSource.Static, expressive, platformStyle = style) {
                    seen = Seen(
                        ItmoTheme.platformStyle,
                        ItmoTheme.spacing,
                        ItmoTheme.shapes,
                        MaterialTheme.typography,
                        ItmoTheme.iosColors,
                        ItmoTheme.expressive,
                        LocalMinimumInteractiveComponentSize.current,
                    )
                }
            }
            waitForIdle()
        }
        return checkNotNull(seen)
    }

    @Test
    fun theIosStyleBringsAppleTypeAndUiKitMetrics() {
        val ios = seenIn(ItmoPlatformStyle.Ios)

        assertEquals(ItmoPlatformStyle.Ios, ios.style)
        assertEquals(ItmoSpacing.Ios, ios.spacing)
        assertEquals(ItmoShapes.Ios, ios.shapes)
        assertEquals(17.sp, ios.typography.bodyLarge.fontSize)
        assertEquals(22.sp, ios.typography.bodyLarge.lineHeight)
        assertEquals(ItmoPlatformStyle.Ios.minTouchTarget, ios.minTouch)
        assertEquals(ItmoPlatformStyle.Ios.minTouchTarget, ios.spacing.touchTarget)
    }

    @Test
    fun theMaterialStyleKeepsTheMaterialTokens() {
        val material = seenIn(ItmoPlatformStyle.Material)

        assertEquals(ItmoSpacing.Default, material.spacing)
        assertEquals(ItmoShapes.Default, material.shapes)
        assertEquals(16.sp, material.typography.bodyLarge.fontSize)
        assertEquals(ItmoPlatformStyle.Material.minTouchTarget, material.minTouch)
    }

    @Test
    fun theIosStyleIgnoresTheExpressiveSwitch() {
        assertFalse(seenIn(ItmoPlatformStyle.Ios, expressive = true).expressive)
        assertTrue(seenIn(ItmoPlatformStyle.Material, expressive = true).expressive)
    }

    @Test
    fun theIosColoursFollowTheNightMode() {
        assertEquals(ItmoIosColors.Light, seenIn(ItmoPlatformStyle.Ios).iosColors)
        assertEquals(ItmoIosColors.Dark, seenIn(ItmoPlatformStyle.Ios, dark = true).iosColors)
    }
}
