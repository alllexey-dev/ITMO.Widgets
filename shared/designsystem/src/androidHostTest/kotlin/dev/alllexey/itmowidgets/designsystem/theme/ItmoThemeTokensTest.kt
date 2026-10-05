package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoEmphasizedTypography
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoMotion
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoShapes
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoSpacing
import dev.alllexey.itmowidgets.designsystem.tokens.ShapeTokens
import dev.alllexey.itmowidgets.designsystem.tokens.TypeScaleTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [ItmoTheme] hands its shapes, type and motion to Material and to its own accessors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItmoThemeTokensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `material and kit read the same tokens`() {
        var shapes: Shapes? = null
        var typography: Typography? = null
        var motionScheme: MotionScheme? = null
        var itmoShapes: ItmoShapes? = null
        var spacing: ItmoSpacing? = null
        var emphasized: ItmoEmphasizedTypography? = null
        var motion: ItmoMotion? = null
        var kitTypography: Typography? = null
        compose.setContent {
            ItmoTheme(colorSource = ColorSource.Static) {
                shapes = MaterialTheme.shapes
                typography = MaterialTheme.typography
                motionScheme = MaterialTheme.motionScheme
                itmoShapes = ItmoTheme.shapes
                spacing = ItmoTheme.spacing
                emphasized = ItmoTheme.emphasizedTypography
                motion = ItmoTheme.motion
                kitTypography = ItmoTheme.typography
            }
        }
        compose.waitForIdle()

        assertEquals(RoundedCornerShape(ShapeTokens.LargeIncreased), checkNotNull(shapes).largeIncreased)
        assertEquals(RoundedCornerShape(ShapeTokens.ExtraLarge), checkNotNull(shapes).extraLarge)
        assertEquals(ItmoShapes.Default, itmoShapes)
        assertEquals(ItmoSpacing.Default, spacing)
        assertSame(ItmoMotion.Default, motion)
        assertSame(ItmoMotion.Default.scheme, motionScheme)
        assertSame(typography, kitTypography)
        val headline = TypeScaleTokens.roles.getValue("headlineLarge")
        assertEquals(headline.first.size, checkNotNull(typography).headlineLarge.fontSize.value)
        assertEquals(headline.second.weight, checkNotNull(emphasized).headlineLarge.fontWeight?.weight)
    }
}
