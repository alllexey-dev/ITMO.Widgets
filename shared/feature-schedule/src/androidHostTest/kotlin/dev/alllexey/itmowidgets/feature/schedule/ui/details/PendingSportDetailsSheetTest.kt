package dev.alllexey.itmowidgets.feature.schedule.ui.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.TimeZone

/**
 * The cases of the View-only pending checks of `LessonDetailsVisualTest` on the Compose sheet, at 320 dp and font
 * scale 1.3 in the sheet's 802 dp: what the header shows (and that it has no flow row), the condition card of each
 * queue kind, the teacher's profile, the map and `Открыть в спорте`. The host's own effects (closing before the
 * profile and the sport tab, the map intent, the snackbar) stay in `PendingSportDetailsBottomSheet` and its
 * instrumented test; the looks live in the `PendingSportDetailsContent_*` goldens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class PendingSportDetailsSheetTest {

    private val profiles = mutableListOf<Int>()
    private var maps = 0
    private var sports = 0
    private var closes = 0
    private val actions = PendingSportDetailsActions(
        onMap = { maps++ },
        onProfile = { profiles += it },
        onOpenSport = { sports++ },
        onClose = { closes++ },
    )

    @Test
    fun theHeaderShowsThePredictionWithoutAFlowRow() = runComposeUiTest {
        setContent { Sheet(PendingSportDetailsSamples.predicted) }

        listOf(
            "Занятие", PendingSportDetailsSamples.SECTION, "Автозапись · прогноз", "Понедельник, 7 сентября 2026",
            "18:40–20:10", "90 мин", PendingSportDetailsSamples.TEACHER_NAME, PendingSportDetailsSamples.ROOM,
        ).forEach { onNodeWithText(it, useUnmergedTree = true).assertExists() }
        // The rail icons carry the labels: teacher and place, never the flow.
        onNodeWithContentDescription("Место", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Поток", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Ждём расписание", useUnmergedTree = true).assertExists()
        onNodeWithText("Запишем, если занятие выйдет с теми же условиями.", useUnmergedTree = true).assertExists()
        assertTouchTargets()
        assertNoTextOverflow()
    }

    @Test
    fun eachQueueKindHasItsOwnCondition() = runComposeUiTest {
        setContent { Sheet(PendingSportDetailsSamples.waiting) }
        onNodeWithText("Автозапись · ожидание места", useUnmergedTree = true).assertExists()
        onNodeWithText("Ждём очереди", useUnmergedTree = true).assertExists()
        onNodeWithText("Попробуем записать, когда освободится место.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun anAutoQueueWaitsForMyItmo() = runComposeUiTest {
        setContent { Sheet(PendingSportDetailsSamples.autoQueue) }
        onNodeWithText("Ждём очереди", useUnmergedTree = true).assertExists()
        onNodeWithText("Попробуем записать, когда занятие появится в My ITMO.", useUnmergedTree = true).assertExists()
        assertNoTextOverflow()
    }

    @Test
    fun openInSportAsksTheHost() = runComposeUiTest {
        setContent { Sheet(PendingSportDetailsSamples.waiting) }

        onNodeWithTag(PendingSportDetailsTestTags.OPEN_SPORT).assertExists()
        onNodeWithText("Открыть в спорте").performClick()
        assertEquals(1, sports)
        assertEquals(0, closes)
    }

    @Test
    fun theTeacherOpensTheirProfileAndTheMapOpensTheRoom() = runComposeUiTest {
        setContent { Sheet(PendingSportDetailsSamples.waiting) }
        val teacher = PendingSportDetailsSamples.TEACHER_NAME

        val click = onNodeWithText(teacher).fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertEquals("Открыть профиль", click.label)
        onNodeWithText(teacher).performClick()
        assertEquals(listOf(PendingSportDetailsSamples.TEACHER_ISU), profiles)
        onNodeWithText("Открыть на карте").performScrollTo().performClick()
        assertEquals(1, maps)
    }

    @Test
    fun withoutAnIsuOrARoomTheTeacherIsTextAndTheMapLeaves() = runComposeUiTest {
        setContent { Sheet(PendingSportDetailsSamples.bare) }

        onNodeWithText(PendingSportDetailsSamples.TEACHER_NAME).assertHasNoClickAction()
        onNodeWithText("Открыть на карте").assertDoesNotExist()
        onNodeWithTag(PendingSportDetailsTestTags.CONDITIONS).assertExists()
    }

    @Test
    fun closeAsksTheHost() = runComposeUiTest {
        setContent { Sheet(PendingSportDetailsSamples.waiting) }

        onAllNodesWithContentDescription("Закрыть").onFirst().performClick()
        assertEquals(1, closes)
    }

    @Test
    fun theTimesAreReadInTheAcademicZoneWhateverOffsetTheyWereWrittenAt() {
        val utc = PendingSportDetailsSamples.waiting.copy(start = "2026-09-07T14:00Z", end = "2026-09-07T15:30:15.5Z")

        val state = PendingSportDetailsSheetState(utc, TimeZone.of("Europe/Moscow"))

        assertEquals("2026-09-07T17:00", state.start.toString())
        assertEquals("2026-09-07T18:30:15.500", state.end.toString())
    }

    @Composable
    private fun Sheet(booking: PendingSportDetailsArgs) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, NARROW_FONT_SCALE)) {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.size(NARROW_WIDTH, SHEET_HEIGHT)) {
                    PendingSportDetailsContent(
                        PendingSportDetailsSamples.state(booking),
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
