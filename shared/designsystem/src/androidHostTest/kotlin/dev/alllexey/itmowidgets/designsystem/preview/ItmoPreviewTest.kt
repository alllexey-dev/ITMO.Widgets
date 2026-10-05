package dev.alllexey.itmowidgets.designsystem.preview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.style.TextOverflow
import dev.alllexey.itmowidgets.designsystem.theme.staticColorScheme
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What [ItmoPreview] makes of each appearance, and that the semantics checks bite at the narrow extreme. */
@RunWith(RobolectricTestRunner::class)
class ItmoPreviewTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a seeded appearance differs from the static and the platform scheme`() {
        val (platform, seeded) = schemesIn(PreviewAppearance.Light, PreviewAppearance.GreenNarrow)

        assertNotEquals(platform.primary, seeded.primary)
        assertNotEquals(staticColorScheme(dark = false).primary, seeded.primary)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the dark appearance gives the dark scheme`() {
        val (dark) = schemesIn(PreviewAppearance.Dark)

        assertTrue(dark.background.luminance() < 0.5f)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a long name fits one line at 1_0 on a phone`() {
        render(PreviewAppearance.Light)

        compose.assertNoTextOverflow()
    }

    @Test
    @Config(qualifiers = "w320dp-h891dp")
    fun `a long name overflows at 1_3 in 320 dp`() {
        RuntimeEnvironment.setFontScale(PreviewAppearance.GreenNarrow.fontScale)
        render(PreviewAppearance.GreenNarrow)

        assertThrows(IllegalStateException::class.java) { compose.assertNoTextOverflow() }
    }

    private fun render(appearance: PreviewAppearance) {
        compose.setContent {
            CompositionLocalProvider(LocalPreviewAppearance provides appearance) {
                ItmoPreview {
                    Text(
                        PreviewFixtures.LongPersonName,
                        Modifier.fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** One composition: a compose rule sets its content once per test. */
    private fun schemesIn(vararg appearances: PreviewAppearance): List<ColorScheme> {
        val schemes = arrayOfNulls<ColorScheme>(appearances.size)
        compose.setContent {
            appearances.forEachIndexed { index, appearance ->
                CompositionLocalProvider(LocalPreviewAppearance provides appearance) {
                    ItmoPreview { schemes[index] = MaterialTheme.colorScheme }
                }
            }
        }
        compose.waitForIdle()
        return schemes.map(::checkNotNull)
    }
}
