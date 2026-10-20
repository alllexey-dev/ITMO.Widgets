package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Outside any Surface, [ItmoTheme]'s content colour is the scheme's `onSurface`, so the dark theme draws no black text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItmoThemeContentColorTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `content colour is onSurface in both modes`() {
        val content = mutableMapOf<Boolean, Color>()
        val onSurface = mutableMapOf<Boolean, Color>()
        compose.setContent {
            for (dark in listOf(true, false)) {
                ItmoTheme(dark = dark, colorSource = ColorSource.Static) {
                    content[dark] = LocalContentColor.current
                    onSurface[dark] = MaterialTheme.colorScheme.onSurface
                }
            }
        }
        compose.waitForIdle()

        assertEquals(onSurface, content)
        assertEquals(setOf(true, false), content.keys)
    }
}
