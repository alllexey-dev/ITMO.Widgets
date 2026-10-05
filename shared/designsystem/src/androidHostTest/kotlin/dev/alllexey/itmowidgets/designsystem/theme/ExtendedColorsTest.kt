package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/** The derived extended colours match what `ConditionTone` and `TeacherLevelTone` compute on the View side. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExtendedColorsTest {

    @Test
    fun `harmonize stays within one per channel of MaterialColors`() {
        val tones = ExtendedColorTokens.Light.teacherLevels() + ExtendedColorTokens.Dark.teacherLevels()
        val primaries = listOf(
            staticColorScheme(dark = false).primary,
            seededColorScheme(SEED, dark = false).primary,
            seededColorScheme(SEED, dark = true).primary,
        )
        primaries.forEach { primary ->
            tones.forEach { tone ->
                val expected = MaterialColors.harmonize(tone.toArgb(), primary.toArgb())
                val actual = harmonize(tone, primary).toArgb()
                assertTrue(
                    "${tone.hex()} towards ${primary.hex()}: MDC ${expected.hex()}, kit ${actual.hex()}",
                    channels(expected).zip(channels(actual)).all { (e, a) -> abs(e - a) <= 1 },
                )
            }
        }
    }

    @Test
    fun `blend equals ColorUtils blendARGB`() {
        val surfaces = listOf(Color(0xFFFFFFFF), Color(0xFF0F0D13), Color(0xFF100E09), Color(0x80123456))
        val accents = ExtendedColorTokens.Light.conditions() + ExtendedColorTokens.Dark.conditions()
        surfaces.forEach { surface ->
            accents.forEach { accent ->
                listOf(0f, 0.12f, 0.5f, 1f).forEach { ratio ->
                    assertEquals(
                        "${accent.hex()} over ${surface.hex()} at $ratio",
                        ColorUtils.blendARGB(surface.toArgb(), accent.toArgb(), ratio).hex(),
                        blendArgb(surface, accent, ratio).toArgb().hex(),
                    )
                }
            }
        }
    }

    @Test
    fun `resolve derives condition containers and teacher levels from the scheme`() {
        listOf(false, true).forEach { dark ->
            val scheme = seededColorScheme(SEED, dark)
            val tokens = ExtendedColorTokens.of(dark)
            val resolved = tokens.resolve(scheme)
            val lowest = scheme.surfaceContainerLowest.toArgb()
            assertEquals(
                ColorUtils.blendARGB(lowest, tokens.sportConditionBlocked.toArgb(), 0.12f).hex(),
                resolved.sportConditionBlockedContainer.toArgb().hex(),
            )
            assertEquals(tokens.sportConditionBlocked, resolved.sportConditionBlocked)
            assertEquals(harmonize(tokens.teacherLevelMixed, scheme.primary), resolved.teacherLevelMixed)
            assertEquals(tokens.lessonTypeLab, resolved.lessonTypeLab)
        }
    }

    private fun ExtendedColorTokens.teacherLevels() = listOf(
        teacherLevelVeryNegative, teacherLevelNegative, teacherLevelMixed, teacherLevelPositive, teacherLevelVeryPositive,
    )

    private fun ExtendedColorTokens.conditions() =
        listOf(sportConditionAllowed, sportConditionWaiting, sportConditionWarning, sportConditionBlocked)

    private fun channels(argb: Int) = listOf(24, 16, 8, 0).map { argb ushr it and 0xFF }

    private fun Color.hex() = toArgb().hex()

    private fun Int.hex() = "%08X".format(this)

    private companion object {
        const val SEED = 0xff087f5b.toInt()
    }
}
