package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The section picker that replaces `dialog_searchable_list` and `MultiSelectSearchableAdapter`; the sport picker cases
 * of `SelectionRowsTest` map here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class SportSectionPickerTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `rows are ordered selected first then used then by label as today`() {
        val options = sportSectionOptions(
            available = listOf(BADMINTON, SWIMMING, VOLLEYBALL, NORDIC_WALKING, TENNIS),
            selected = setOf(TENNIS, VOLLEYBALL),
            used = setOf(NORDIC_WALKING),
        )

        assertEquals(
            listOf("Волейбол", "Настольный теннис", "Северная ходьба", "Бадминтон", "Плавание"),
            options.map { it.label },
        )
    }

    @Test
    fun `the used check and the confirmed names go through the shortened label`() {
        val options = sportSectionOptions(
            available = listOf(SWIMMING, NORDIC_WALKING),
            selected = emptySet(),
            used = setOf(NORDIC_WALKING),
        )

        assertEquals(listOf(NORDIC_WALKING, SWIMMING), options.map { it.name })
        assertEquals(setOf(NORDIC_WALKING), options.confirmedSelection(setOf(NORDIC_WALKING)))
    }

    @Test
    fun `search is case-insensitive and ignores the device locale`() {
        val options = sportSectionOptions(listOf(VOLLEYBALL, SWIMMING, ICE_HOCKEY), emptySet(), emptySet())
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr"))
        try {
            assertEquals(listOf("Волейбол"), options.matching("ВОЛЕЙ").map { it.label })
            assertEquals(listOf("Волейбол"), options.matching("волей").map { it.label })
            // The Turkish default would lower "I" to a dotless "ı" and miss.
            assertEquals(listOf("ICE HOCKEY"), options.matching("ice").map { it.label })
            assertEquals(options, options.matching(""))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `a check survives filtering and done reports the selection once`() {
        val confirmed = mutableListOf<Set<SectionName>>()
        var dismissed = 0
        compose.setContent {
            ItmoTheme {
                SportSectionPickerSurface(
                    available = listOf(SWIMMING, DANCES),
                    selected = emptySet(),
                    used = emptySet(),
                    onConfirm = { confirmed += it },
                    onDismiss = { dismissed++ },
                )
            }
        }

        compose.onNodeWithText("Плавание").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag(SportSectionPickerTestTags.SEARCH).performTextInput("танцы")
        compose.onNodeWithText("Плавание").assertDoesNotExist()
        compose.onNodeWithText("Танцы").assertIsOff()
        compose.onNodeWithTag(SportSectionPickerTestTags.SEARCH).performTextReplacement("")
        compose.onNodeWithText("Плавание").assertIsOn()
        compose.onNodeWithTag(SportSectionPickerTestTags.CONFIRM).performClick()

        assertEquals(listOf(setOf(SWIMMING)), confirmed)
        assertEquals(0, dismissed)
    }

    @Test
    fun `cancel dismisses without a selection`() {
        val confirmed = mutableListOf<Set<SectionName>>()
        var dismissed = 0
        compose.setContent {
            ItmoTheme {
                SportSectionPickerSurface(
                    available = listOf(SWIMMING),
                    selected = setOf(SWIMMING),
                    used = emptySet(),
                    onConfirm = { confirmed += it },
                    onDismiss = { dismissed++ },
                )
            }
        }

        compose.onNodeWithText("Плавание").assertIsOn().performClick()
        compose.onNodeWithTag(SportSectionPickerTestTags.CANCEL).performClick()

        assertEquals(emptyList<Set<SectionName>>(), confirmed)
        assertEquals(1, dismissed)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `long names fit at 320 dp and font 1_3 with full touch targets`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                ItmoTheme {
                    SportSectionPickerSurface(
                        available = SportSignFiltersSamples.longSections,
                        selected = setOf(SportSignFiltersSamples.longSections[0]),
                        used = emptySet(),
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
        }

        compose.assertNoTextOverflow()
        compose.assertTouchTargets()
    }

    private companion object {
        const val LARGE_FONT = 1.3f
        val BADMINTON = SectionName("Бадминтон")
        val SWIMMING = SectionName("Плавание")
        val VOLLEYBALL = SectionName("Волейбол")
        val TENNIS = SectionName("Настольный теннис")
        val DANCES = SectionName("Танцы")
        val NORDIC_WALKING = SectionName("Спортивный туризм (северная ходьба)")
        val ICE_HOCKEY = SectionName("ICE HOCKEY")
    }
}
