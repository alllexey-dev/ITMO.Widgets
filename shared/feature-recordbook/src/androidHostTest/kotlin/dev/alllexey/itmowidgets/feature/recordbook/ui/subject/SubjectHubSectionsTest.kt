package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SheetLinkOption
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTeacher
import dev.alllexey.itmowidgets.feature.recordbook.sheetScore
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The actions of the subject page's remaining sections (LR-4a2): votes on a link, the sheet total's row and `⋮` menu,
 * the offer to connect a sheet, confirming and rejecting a binding, and teachers who open a profile only with an ISU
 * that a profile accepts. Their looks live in the `SubjectLinkItem*`, `SubjectSheetTotal*`, `SubjectBinding*` and
 * `SubjectTeacherRow*` goldens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SubjectHubSectionsTest {

    @Test
    fun theArrowsOfAnotherStudentsLinkReportTheVote() = runComposeUiTest {
        val votes = mutableListOf<Pair<String, Boolean>>()
        setThemedContent {
            SubjectLinkItem(SubjectHubItem.Link(link(), canVote = true, GroupPosition.Single), {}, {}) { link, up ->
                votes += link.id to up
            }
        }

        onNodeWithContentDescription(VOTE_UP).performClick()
        onNodeWithContentDescription(VOTE_DOWN).performClick()

        assertEquals(listOf(LINK_ID to true, LINK_ID to false), votes)
    }

    @Test
    fun withoutTheRightToVoteTheLinkShowsTheScoreAlone() = runComposeUiTest {
        setThemedContent {
            SubjectLinkItem(SubjectHubItem.Link(link(), canVote = false, GroupPosition.Single), {}, {}) { _, _ -> }
        }

        onAllNodesWithContentDescription(VOTE_UP).assertCountEquals(0)
        onAllNodesWithContentDescription(VOTE_DOWN).assertCountEquals(0)
    }

    @Test
    fun theSheetTotalMenuOpensChangesAndDisconnects() = runComposeUiTest {
        val opened = mutableListOf<String>()
        var changes = 0
        var disconnects = 0
        val state = connected()
        setThemedContent {
            SubjectSheetTotal(
                state,
                SubjectSheetActions(
                    onOpen = { opened += it },
                    onChangeTotal = { changes++ },
                    onDisconnect = { disconnects++ },
                ),
            )
        }

        onNodeWithTag(SubjectSheetTotalTestTags.ROW).performClick()
        chooseFromMenu(OPEN)
        chooseFromMenu(CHANGE_TOTAL)
        chooseFromMenu(DISCONNECT)

        assertEquals(listOf(state.score.tabUrl, state.score.tabUrl), opened)
        assertEquals(1, changes)
        assertEquals(1, disconnects)
    }

    @Test
    fun theSheetMenuButtonIsANamedTouchTarget() = runComposeUiTest {
        setThemedContent { SubjectSheetTotal(connected(), SubjectSheetActions()) }

        onNodeWithContentDescription(SHEET_ACTIONS).assertExists()
        assertTouchTargets()
    }

    @Test
    fun theOfferConnectsTheSubjectsSheetLinks() = runComposeUiTest {
        val links = listOf(SheetLinkOption(SHEET_URL, null, mine = true))
        val offered = mutableListOf<List<SheetLinkOption>>()
        setThemedContent {
            SubjectSheetTotal(SubjectSheetState.Hint(links), SubjectSheetActions(onConnect = { offered += it }))
        }

        onNodeWithText(SHEET_HINT).performClick()

        assertEquals(listOf(links), offered)
    }

    @Test
    fun aProposalIsConfirmedOrRejected() = runComposeUiTest {
        val confirmed = mutableListOf<Long>()
        var rejected = 0
        setThemedContent {
            SubjectBindingProposal(ScheduleSubject(9, SUBJECT, setOf(10)), { confirmed += it }, { rejected++ })
        }

        onNodeWithText(CONFIRM).performClick()
        onNodeWithText(REJECT).performClick()

        assertEquals(listOf(9L), confirmed)
        assertEquals(1, rejected)
    }

    @Test
    fun aChoiceBindsThePickedSubject() = runComposeUiTest {
        val confirmed = mutableListOf<Long>()
        setThemedContent {
            SubjectBindingChoice(
                listOf(ScheduleSubject(21, SUBJECT, setOf(10)), ScheduleSubject(22, "$SUBJECT (поток 2)", setOf(11))),
                onConfirm = { confirmed += it },
            )
        }

        onNodeWithText("$SUBJECT (поток 2)").performClick()

        assertEquals(listOf(22L), confirmed)
    }

    @Test
    fun aTeacherWithAValidIsuOpensTheProfileFromA48DpRow() = runComposeUiTest {
        val opened = mutableListOf<Int>()
        setThemedContent {
            SubjectTeacherRow(teacher(isu = 300001L, TeacherLevel.POSITIVE)) { opened += it }
        }

        onAllNodes(hasClickAction()).assertCountEquals(1)
        assertTouchTargets()
        onNode(hasClickAction()).performClick()

        assertEquals(listOf(300001), opened)
    }

    @Test
    fun aTeacherWithoutAProfileIsuHasNoAction() = runComposeUiTest {
        setThemedContent {
            Column {
                SubjectTeacherRow(teacher(isu = null, level = null)) {}
                SubjectTeacherRow(teacher(isu = 0L, level = null)) {}
                SubjectTeacherRow(teacher(isu = Int.MAX_VALUE + 1L, level = TeacherLevel.MIXED)) {}
            }
        }

        onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun theTeachersNameCarriesTheReviewTone() = runComposeUiTest {
        setThemedContent { SubjectTeacherRow(teacher(isu = 300001L, TeacherLevel.POSITIVE)) {} }

        onNodeWithContentDescription("$TEACHER, тон отзывов: скорее положительные").assertExists()
    }

    private fun ComposeUiTest.chooseFromMenu(label: String) {
        onNodeWithTag(SubjectSheetTotalTestTags.MENU).performClick()
        onNodeWithText(label).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.setThemedContent(content: @Composable () -> Unit) = setContent {
        ItmoTheme(platformStyle = ItmoPlatformStyle.Material) { content() }
    }

    private fun connected() = SubjectSheetState.Connected(
        sheetScore(status = SheetStatus.OK),
        LocalDateTime(LocalDate(2026, 9, 7), LocalTime(12, 0)),
        LocalDate(2026, 9, 7),
    )

    private fun teacher(isu: Long?, level: TeacherLevel?) =
        SubjectHubItem.Teacher(SubjectTeacher(TEACHER, isu, listOf(1)), level, GroupPosition.Single)

    private fun link() = SubjectLink(
        id = LINK_ID, scope = ResourceScope(2, SUBJECT, "2025-2"), category = LinkCategory.MATERIALS,
        url = "https://example.org/materials", title = "Материалы курса", visibility = LinkVisibility.ALL,
        flowId = null, audienceLabel = null, status = SubjectLinkStatus.PUBLISHED, reviewNote = null, score = 3,
        myVote = 0, isMine = false, reportedByMe = false, author = null,
        updatedAt = Instant.parse("2026-03-01T00:00:00Z"),
    )

    private companion object {
        const val LINK_ID = "materials"
        const val SUBJECT = "Алгоритмы и структуры данных"
        const val TEACHER = "Иванова Мария Сергеевна"
        const val SHEET_URL = "https://docs.google.com/spreadsheets/d/x"
        const val VOTE_UP = "Полезная ссылка"
        const val VOTE_DOWN = "Бесполезная ссылка"
        const val SHEET_ACTIONS = "Действия с таблицей"
        const val SHEET_HINT = "Мои баллы из таблицы"
        const val OPEN = "Открыть таблицу"
        const val CHANGE_TOTAL = "Изменить итог"
        const val DISCONNECT = "Отключить"
        const val CONFIRM = "Связать"
        const val REJECT = "Нет"
    }
}
