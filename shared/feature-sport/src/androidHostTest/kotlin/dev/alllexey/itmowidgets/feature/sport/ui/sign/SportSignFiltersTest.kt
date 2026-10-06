package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The filters header that replaces `item_sport_filters_header` in `FiltersHeaderAdapter`. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SportSignFiltersTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = SportSignFilterActions(
        onSelectSports = { calls += "sports ${it.map(SectionName::raw).sorted()}" },
        onSelectBuilding = { calls += "building $it" },
        onSelectTeacher = { calls += "teacher $it" },
        onSelectTime = { calls += "time $it" },
        onShowOnlyAvailable = { calls += "available $it" },
        onShowAutoSign = { calls += "auto $it" },
        onShowOnlyFriends = { calls += "friends $it" },
        onReset = { calls += "reset" },
    )

    @Test
    fun `each toggle and the reset chip call back once`() {
        show(SportSignFiltersSamples.allSet())

        compose.onNodeWithTag(SportSignFiltersTestTags.AVAILABLE).assertIsNotSelected().performClick()
        compose.onNodeWithTag(SportSignFiltersTestTags.AUTO_SIGN).assertIsNotSelected().performClick()
        compose.onNodeWithTag(SportSignFiltersTestTags.FRIENDS).assertIsSelected().performClick()
        compose.onNodeWithTag(SportSignFiltersTestTags.RESET).performScrollTo().performClick()

        assertEquals(listOf("available true", "auto true", "friends false", "reset"), calls)
    }

    @Test
    fun `a dropdown pick calls back once and any means null`() {
        show(SportSignFiltersSamples.allSet())
        val buildings = SportSignFiltersSamples.buildings

        compose.onNodeWithTag(SportSignFiltersTestTags.BUILDING).performClick()
        compose.onNodeWithText(buildings[1]).performClick()
        compose.onNodeWithTag(SportSignFiltersTestTags.TEACHER).performClick()
        compose.onNodeWithText("Любой преподаватель").performClick()
        compose.onNodeWithTag(SportSignFiltersTestTags.TIME).performClick()
        compose.onNodeWithText(SportSignFiltersSamples.timeSlots[0]).performClick()

        assertEquals(listOf("building ${buildings[1]}", "teacher null", "time 08:20"), calls)
    }

    @Test
    fun `the section field opens the picker and done selects once`() {
        show(SportSignFiltersSamples.noneSet())

        compose.onNodeWithTag(SportSignFiltersTestTags.SPORT).performClick()
        compose.onNodeWithText("Плавание").performClick()
        compose.onNodeWithText("Северная ходьба").performClick()
        compose.onNodeWithTag(SportSectionPickerTestTags.CONFIRM).performClick()

        compose.onNodeWithTag(SportSectionPickerTestTags.LIST).assertDoesNotExist()
        assertEquals(listOf("sports [Плавание, Спортивный туризм (северная ходьба)]"), calls)
    }

    @Test
    fun `selectors hide by the display options and reset shows only with active filters`() {
        show(SportSignFiltersSamples.hiddenSelectors())

        compose.onNodeWithTag(SportSignFiltersTestTags.BUILDING).assertIsDisplayed()
        compose.onNodeWithTag(SportSignFiltersTestTags.TEACHER).assertDoesNotExist()
        compose.onNodeWithTag(SportSignFiltersTestTags.TIME).assertDoesNotExist()
        compose.onNodeWithTag(SportSignFiltersTestTags.RESET).assertDoesNotExist()
    }

    @Test
    fun `selected sections show by their short names`() {
        show(SportSignFiltersSamples.noneSet().copy(selectedSportNames = setOf(SportSignFiltersSamples.sections[3])))

        compose.onNodeWithText("Северная ходьба").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `long names fit at 320 dp and font 1_3 with full touch targets`() {
        show(SportSignFiltersSamples.longNames(), fontScale = LARGE_FONT)

        compose.assertNoTextOverflow()
        compose.assertTouchTargets()
    }

    private fun show(state: SportSignUiState.Content, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                // The header scrolls with the lesson list in the screen; here a column stands in for it.
                ItmoTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) { SportSignFilters(state, actions) }
                }
            }
        }
    }

    private companion object {
        const val LARGE_FONT = 1.3f
    }
}
