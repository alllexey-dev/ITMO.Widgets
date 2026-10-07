package dev.alllexey.itmowidgets.feature.schedule.ui.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonFriendsState
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The cases of the deleted View-only `LessonDetailsVisualTest` checks on the Compose sheet, at 320 dp and font scale
 * 1.3 in the sheet's 802 dp: what the header shows, the teacher's profile, the reserved tone dot, the change block,
 * the meeting link, the map and every state of `Друзья на паре`. The host's own effects (closing before the profile,
 * the intents, the snackbars) stay in `LessonDetailsBottomSheet` and its instrumented test; the looks live in the
 * `LessonDetailsContent_*` goldens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class LessonDetailsSheetTest {

    private val profiles = mutableListOf<Int>()
    private val links = mutableListOf<String>()
    private var maps = 0
    private var retries = 0
    private var closes = 0
    private val actions = LessonDetailsActions(
        onMap = { maps++ },
        onLink = { links += it },
        onProfile = { profiles += it },
        onRetryFriends = { retries++ },
        onClose = { closes++ },
    )

    @Test
    fun theHeaderShowsEveryFactOfTheLesson() = runComposeUiTest {
        setContent { Sheet(LessonDetailsSamples.full, LessonDetailsUiState(friends = LessonFriendsState.Disabled)) }

        listOf(
            "Пара", "Физика", "Практика · Очный", "Понедельник, 7 сентября 2026", "10:00–11:30", "90 мин",
            LessonDetailsSamples.TEACHER_NAME, LessonDetailsSamples.FLOW, "2202 · ул. Ломоносова, 9",
        ).forEach { onNodeWithText(it, useUnmergedTree = true).assertExists() }
        onNodeWithText("Открыть на карте").performScrollTo().performClick()
        assertEquals(1, maps)
        assertTouchTargets()
        assertNoTextOverflow()
    }

    @Test
    fun theTeacherOpensTheirProfileAndATeacherWithoutAnIsuStaysText() = runComposeUiTest {
        var lesson by mutableStateOf(LessonDetailsSamples.full)
        setContent { Sheet(lesson) }
        val teacher = LessonDetailsSamples.TEACHER_NAME

        val click = onNodeWithText(teacher).fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertEquals("Открыть профиль", click.label)
        onNodeWithText(teacher).performClick()
        assertEquals(listOf(LessonDetailsSamples.TEACHER_ISU), profiles)

        lesson = LessonDetailsSamples.plain
        waitForIdle()
        onNodeWithText(teacher).assertHasNoClickAction()
    }

    @Test
    fun theHeaderDoesNotMoveWhenTheToneOrAChangeArrives() = runComposeUiTest {
        var details by mutableStateOf(LessonDetailsUiState(friends = LessonFriendsState.Disabled))
        setContent { Sheet(LessonDetailsSamples.full, details) }
        val before = headerBounds()

        details = details.copy(teacherLevel = TeacherLevel.POSITIVE)
        waitForIdle()
        assertEquals(before, headerBounds())
        onNodeWithText(LessonDetailsSamples.TEACHER_NAME)
            .assert(hasContentDescription("Тон отзывов: скорее положительные", substring = true))

        details = details.copy(change = LessonDetailsSamples.timeAndRoomChange)
        waitForIdle()
        assertEquals(before, headerBounds())
        onNodeWithTag(LessonDetailsTestTags.CHANGES).assertExists()
    }

    @Test
    fun theChangeBlockShowsWasAndNowPerChangedField() = runComposeUiTest {
        var details by mutableStateOf(LessonDetailsUiState(friends = LessonFriendsState.Disabled))
        setContent { Sheet(LessonDetailsSamples.plain, details) }
        onNodeWithTag(LessonDetailsTestTags.CHANGES).assertDoesNotExist()

        details = details.copy(change = LessonDetailsSamples.timeAndRoomChange)
        waitForIdle()
        onNodeWithText("Изменения", useUnmergedTree = true).assertExists()
        onNodeWithText("Время: 08:20–09:50 → 10:00–11:30", useUnmergedTree = true).assertExists()
        onNodeWithText("Аудитория: 1506 · Кронва → 2202 · Ломо", useUnmergedTree = true).assertExists()

        details = details.copy(change = LessonDetailsSamples.roomChange)
        waitForIdle()
        onNodeWithText("Время: 08:20–09:50 → 10:00–11:30", useUnmergedTree = true).assertDoesNotExist()
        assertNoTextOverflow()
    }

    @Test
    fun theLinkOpensAndThePasswordIsAFact() = runComposeUiTest {
        setContent { Sheet(LessonDetailsSamples.full) }

        onNodeWithText("Пароль: 1234", useUnmergedTree = true).assertExists()
        onNodeWithTag(LessonDetailsTestTags.LINK).performScrollTo().performClick()
        assertEquals(listOf("https://example.invalid/meeting"), links)
        onNodeWithTag(LessonDetailsTestTags.NOTE).performScrollTo().assertExists()
    }

    @Test
    fun aLessonWithoutTeacherRoomOrLinkKeepsOnlyTheTime() = runComposeUiTest {
        setContent {
            Sheet(LessonDetailsSamples.minimal, LessonDetailsUiState(friends = LessonFriendsState.Disabled), mapAvailable = false)
        }

        onNodeWithText("Предмет не указан", useUnmergedTree = true).assertExists()
        onNodeWithText("10:00–11:30", useUnmergedTree = true).assertExists()
        onNodeWithText("Открыть на карте").assertDoesNotExist()
        listOf(
            LessonDetailsTestTags.CHANGES, LessonDetailsTestTags.LINK, LessonDetailsTestTags.NOTE,
            LessonDetailsTestTags.FRIENDS,
        ).forEach { onNodeWithTag(it).assertDoesNotExist() }
    }

    @Test
    fun closeAsksTheHost() = runComposeUiTest {
        setContent { Sheet(LessonDetailsSamples.minimal) }

        onAllNodesWithContentDescription("Закрыть").onFirst().performClick()
        assertEquals(1, closes)
    }

    @Test
    fun theLongFlowWraps() = runComposeUiTest {
        setContent { Sheet(LessonDetailsSamples.plain.copy(flowName = LessonDetailsSamples.LONG_FLOW)) }

        onNodeWithText(LessonDetailsSamples.LONG_FLOW, useUnmergedTree = true).assertExists()
        assertNoTextOverflow()
    }

    @Test
    fun friendsLoadFailRetryAndOpenProfiles() = runComposeUiTest {
        var details by mutableStateOf(LessonDetailsUiState(friends = LessonFriendsState.Disabled))
        setContent { Sheet(LessonDetailsSamples.plain, details) }
        onNodeWithTag(LessonDetailsTestTags.FRIENDS).assertDoesNotExist()

        details = LessonDetailsUiState(friends = LessonFriendsState.Loading)
        waitForIdle()
        onNodeWithTag(LessonDetailsTestTags.FRIENDS_PROGRESS, useUnmergedTree = true).assertExists()
        onNodeWithText("Друзья на паре", useUnmergedTree = true).assertExists()

        details = LessonDetailsUiState(friends = LessonFriendsState.Error(AppError.Network))
        waitForIdle()
        onNodeWithText("Нет связи. Проверьте интернет.", useUnmergedTree = true).assertExists()
        onNodeWithText("Повторить").performScrollTo().performClick()
        assertEquals(1, retries)

        details = LessonDetailsUiState(friends = LessonFriendsState.Content(emptyList()))
        waitForIdle()
        onNodeWithText("Никого из друзей на этой паре", useUnmergedTree = true).assertExists()

        details = LessonDetailsUiState(friends = LessonFriendsState.Content(LessonDetailsSamples.friends))
        waitForIdle()
        onNodeWithText("Друзья на паре · 2", useUnmergedTree = true).assertExists()
        assertEquals(2, onAllNodesWithTag(LessonDetailsTestTags.FRIEND).fetchSemanticsNodes().size)
        onNodeWithText("Пользователь ИСУ 300101", useUnmergedTree = true).assertExists()
        onNodeWithText("P3212", useUnmergedTree = true).assertExists()
        onAllNodesWithTag(LessonDetailsTestTags.FRIEND).onFirst().performScrollTo().performClick()
        assertEquals(listOf(LessonDetailsSamples.FRIEND_ISU), profiles)
        assertTouchTargets()
    }

    /** The title's and the teacher row's bounds: what a late tone or change must not move. */
    private fun ComposeUiTest.headerBounds(): Pair<Rect, Rect> =
        onNodeWithText("Физика", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot to
            onNodeWithText(LessonDetailsSamples.TEACHER_NAME, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Composable
    private fun Sheet(
        lesson: LessonDetailsArgs,
        details: LessonDetailsUiState = LessonDetailsUiState(friends = LessonFriendsState.Disabled),
        mapAvailable: Boolean = true,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, NARROW_FONT_SCALE)) {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.size(NARROW_WIDTH, SHEET_HEIGHT)) {
                    LessonDetailsContent(
                        LessonDetailsSheetState(lesson, details, mapAvailable),
                        actions,
                        Modifier.size(NARROW_WIDTH, SHEET_HEIGHT),
                    )
                }
            }
        }
    }

    private companion object {
        const val NARROW_FONT_SCALE = 1.3f
        val NARROW_WIDTH = 320.dp
        val SHEET_HEIGHT = 802.dp
    }
}
