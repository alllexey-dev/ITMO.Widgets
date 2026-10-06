package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SportLessonListTest {

    @Test
    fun theListAreaFollowsTheSignState() {
        val lessons = listOf(SportLessonSamples.open, SportLessonSamples.full)
        assertEquals(SportLessonListState.Loading, SportSignUiState.Loading.lessonListState())
        assertEquals(SportLessonListState.Loading,
            SportSignUiState.Content(displayedLessons = lessons, initialLoading = true).lessonListState())
        assertEquals(SportLessonListState.Error(AppError.Network), SportSignUiState.Error(AppError.Network).lessonListState())
        assertEquals(SportLessonListState.Empty(filtered = false), SportSignUiState.Content().lessonListState())
        assertEquals(SportLessonListState.Empty(filtered = true),
            SportSignUiState.Content(hasActiveFilters = true).lessonListState())
        assertEquals(SportLessonListState.Lessons(lessons, setOf(14L)),
            SportSignUiState.Content(displayedLessons = lessons, busyLessonIds = setOf(14L)).lessonListState())
    }

    @Test
    fun theFirstLoadShowsPlaceholderCardsOnly() = runComposeUiTest {
        setContent { List(SportLessonListState.Loading) }

        onNodeWithTag(SportLessonListTestTags.SKELETON).assertExists()
        onAllNodesWithTag(SportLessonCardTestTags.CARD).assertCountEquals(0)
    }

    @Test
    fun anEmptyDaySuggestsAnotherDayOrFewerFiltersWithOrWithoutFilters() = runComposeUiTest {
        var state: SportLessonListState by mutableStateOf(SportLessonListState.Empty(filtered = false))
        setContent { List(state) }
        onNodeWithText("В этот день занятий нет").assertExists()
        onNodeWithText("Попробуйте другой день или снимите фильтры.").assertExists()

        state = SportLessonListState.Empty(filtered = true)
        waitForIdle()
        onNodeWithTag(SportLessonListTestTags.EMPTY).assertExists()
        onNodeWithText("В этот день занятий нет").assertExists()
    }

    @Test
    fun anErrorWithNothingToKeepOffersARetry() = runComposeUiTest {
        var retries = 0
        setContent { List(SportLessonListState.Error(AppError.Network), onRetry = { retries++ }) }

        onNodeWithText("Не удалось загрузить").assertExists()
        assertTouchTargets()
        onNodeWithText("Повторить").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun onlyTheBusyLessonHasItsActionDisabled() = runComposeUiTest {
        val busy = SportLessonSamples.open
        val other = SportLessonSamples.scarce
        setContent { List(SportLessonListState.Lessons(listOf(busy, other), busyLessonIds = setOf(busy.lessonId))) }

        onAllNodesWithTag(SportLessonCardTestTags.CARD).assertCountEquals(2)
        action(busy.sectionName.shorten()).assertIsNotEnabled()
        action(other.sectionName.shorten()).assertIsEnabled()
    }

    @Test
    fun aPredictionAndTheRealLessonWithTheSameIdAreTwoItems() = runComposeUiTest {
        val real = SportLessonSamples.open
        val predicted = real.copy(isLessonReal = false, available = 0, canSignIn = false)
        assertNotEquals(sportLessonKey(real), sportLessonKey(predicted))

        setContent { List(SportLessonListState.Lessons(listOf(real, predicted))) }
        onAllNodesWithTag(SportLessonCardTestTags.CARD).assertCountEquals(2)
    }

    private fun ComposeUiTest.action(title: String) = onNode(
        hasTestTag(SportLessonCardTestTags.ACTION) and
            hasAnyAncestor(hasTestTag(SportLessonCardTestTags.CARD) and hasText(title, substring = true)),
    )

    @Composable
    private fun List(state: SportLessonListState, onRetry: () -> Unit = {}) {
        ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
            SportLessonList(state, SportLessonSamples.time, SportLessonActions(), onRetry)
        }
    }
}
