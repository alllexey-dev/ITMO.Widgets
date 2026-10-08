package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkChip
import dev.alllexey.itmowidgets.core.resources.SubjectLinkChips
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroupKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContext
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SheetLinkOption
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectHubState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonsState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTeacher
import dev.alllexey.itmowidgets.shared.core.links_chats
import dev.alllexey.itmowidgets.shared.core.links_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_controls_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_lessons_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_teachers_title
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

/** `subjectHubItems` keeps `SubjectHubAdapter.submitContent`'s order, groups and counts. */
class SubjectHubItemsTest {

    @Test
    fun `sections follow the page order under the result`() {
        val items = subjectHubItems(fullState())

        assertIs<SubjectHubItem.Hero>(items.first())
        assertEquals(
            listOf(
                CoreRes.string.links_title,
                CoreRes.string.links_chats,
                Res.string.subject_controls_title,
                Res.string.subject_teachers_title,
                Res.string.subject_lessons_title,
            ),
            items.filterIsInstance<SubjectHubItem.Section>().map { it.title },
        )
    }

    @Test
    fun `every section is one connected group`() {
        val items = subjectHubItems(fullState())

        assertEquals(
            listOf(GroupPosition.First, GroupPosition.Middle, GroupPosition.Middle, GroupPosition.Last),
            items.positionsOf<SubjectHubItem>(after = CoreRes.string.links_title),
        )
        assertEquals(listOf(GroupPosition.Single), items.positionsOf<SubjectHubItem.Chat>())
        assertEquals(listOf(GroupPosition.First, GroupPosition.Last), items.positionsOf<SubjectHubItem.Teacher>())
        assertEquals(
            listOf(GroupPosition.First, GroupPosition.Middle, GroupPosition.Last),
            items.positionsOf<SubjectHubItem>(after = Res.string.subject_lessons_title),
        )
    }

    @Test
    fun `the short list of links ends with all links and their count`() {
        val links = subjectHubItems(fullState()).sectionAfter(CoreRes.string.links_title)

        assertIs<SubjectHubItem.Link>(links[0])
        assertIs<SubjectHubItem.Lms>(links[1])
        assertIs<SubjectHubItem.Link>(links[2])
        assertEquals(5, assertIs<SubjectHubItem.AllLinks>(links.last()).count)
    }

    @Test
    fun `a subject without links offers to add one`() {
        val state = fullState().withHub { copy(chips = SubjectLinkChips(emptyList(), 0), linkCount = 0) }

        val links = subjectHubItems(state).sectionAfter(CoreRes.string.links_title)

        assertEquals(listOf(SubjectHubItem.AddLink(GroupPosition.Single)), links)
    }

    @Test
    fun `links and chats are absent without a resource scope`() {
        val state = fullState().withHub { copy(resourceScope = null, chats = emptyList()) }

        val titles = subjectHubItems(state).filterIsInstance<SubjectHubItem.Section>().map { it.title }

        assertEquals(
            listOf(Res.string.subject_controls_title, Res.string.subject_teachers_title, Res.string.subject_lessons_title),
            titles,
        )
    }

    @Test
    fun `without subject links only the LMS page stays and the chats go`() {
        val items = subjectHubItems(fullState(), linksEnabled = false)

        assertEquals(
            listOf(SubjectHubItem.Lms(LMS, GroupPosition.Single)),
            items.sectionAfter(CoreRes.string.links_title),
        )
        assertEquals(emptyList(), items.filter { it is SubjectHubItem.Link || it is SubjectHubItem.Chat })
        assertEquals(
            listOf(
                CoreRes.string.links_title,
                Res.string.subject_controls_title,
                Res.string.subject_teachers_title,
                Res.string.subject_lessons_title,
            ),
            items.filterIsInstance<SubjectHubItem.Section>().map { it.title },
        )
    }

    @Test
    fun `without subject links or an LMS page the links section goes`() {
        val state = fullState().withHub {
            copy(chips = chips.copy(visible = chips.visible.filterIsInstance<SubjectLinkChip.Link>()))
        }

        val titles = subjectHubItems(state, linksEnabled = false)
            .filterIsInstance<SubjectHubItem.Section>().map { it.title }

        assertEquals(
            listOf(Res.string.subject_controls_title, Res.string.subject_teachers_title, Res.string.subject_lessons_title),
            titles,
        )
    }

    @Test
    fun `collapsed lessons end with all lessons and the full count`() {
        val lessons = subjectHubItems(fullState()).sectionAfter(Res.string.subject_lessons_title)

        assertEquals(2, lessons.filterIsInstance<SubjectHubItem.Lesson>().size)
        assertEquals(4, assertIs<SubjectHubItem.AllLessons>(lessons.last()).count)
    }

    @Test
    fun `expanded lessons list every lesson without all lessons`() {
        val state = fullState().withHub { copy(lessonsExpanded = true) }

        val lessons = subjectHubItems(state).sectionAfter(Res.string.subject_lessons_title)

        assertEquals(4, lessons.size)
        assertEquals(lessons, lessons.filterIsInstance<SubjectHubItem.Lesson>())
        assertEquals(GroupPosition.Last, (lessons.last() as SubjectHubItem.Lesson).position)
    }

    @Test
    fun `lessons that are not content take one message or binding row`() {
        val candidate = ScheduleSubject(9, ALGORITHMS, setOf(10))
        fun lessonsOf(lessons: SubjectLessonsState) =
            subjectHubItems(fullState().withHub { copy(lessons = lessons) }).sectionAfter(Res.string.subject_lessons_title)

        assertEquals(listOf(SubjectHubItem.BindingProposal(candidate)), lessonsOf(SubjectLessonsState.Proposed(candidate)))
        assertEquals(
            listOf(SubjectHubItem.BindingChoice(listOf(candidate))),
            lessonsOf(SubjectLessonsState.Ambiguous(listOf(candidate))),
        )
        assertEquals(
            listOf(SubjectHubItem.LessonsMessage(SubjectLessonsState.Loading)),
            lessonsOf(SubjectLessonsState.Loading),
        )
        assertEquals(
            listOf(SubjectHubItem.LessonsMessage(SubjectLessonsState.Unmatched)),
            lessonsOf(SubjectLessonsState.Unmatched),
        )
    }

    @Test
    fun `hidden lessons have no section`() {
        val state = fullState().withHub { copy(lessons = SubjectLessonsState.Hidden) }

        val titles = subjectHubItems(state).filterIsInstance<SubjectHubItem.Section>().map { it.title }

        assertEquals(Res.string.subject_teachers_title, titles.last())
    }

    @Test
    fun `teachers carry their review level by ISU`() {
        val teachers = subjectHubItems(fullState()).filterIsInstance<SubjectHubItem.Teacher>()

        assertEquals(listOf(TeacherLevel.POSITIVE, null), teachers.map { it.level })
    }

    @Test
    fun `control groups get a heading and own group, a lone control after them is spaced`() {
        val controls = subjectHubItems(fullState()).sectionAfter(Res.string.subject_controls_title)

        val labs = assertIs<SubjectHubItem.Group>(controls[0])
        assertEquals(ControlGroupKind.LABS, labs.group.kind)
        val labRows = controls.subList(1, 4).map { assertIs<SubjectHubItem.Control>(it) }
        assertEquals(listOf(GroupPosition.First, GroupPosition.Middle, GroupPosition.Last), labRows.map { it.position })
        assertEquals(listOf(1, 1, 1), labRows.map { it.row.depth })
        assertEquals(listOf(false, false, false), labRows.map { it.spaced })
        val singles = controls.drop(4).map { assertIs<SubjectHubItem.Control>(it) }
        assertEquals(listOf("Коллоквиум", "Экзамен"), singles.map { it.row.control.name })
        assertEquals(listOf(GroupPosition.First, GroupPosition.Last), singles.map { it.position })
        assertEquals(listOf(true, true), singles.map { it.spaced })
        assertEquals(listOf(0, 0), singles.map { it.row.depth })
    }

    @Test
    fun `lone controls before any group are not spaced`() {
        val state = fullState().copy(controls = listOf(control(1, "Текущий контроль"), control(2, "Экзамен")))

        val rows = subjectHubItems(state).filterIsInstance<SubjectHubItem.Control>()

        assertEquals(listOf(false, false), rows.map { it.spaced })
        assertEquals(listOf(GroupPosition.First, GroupPosition.Last), rows.map { it.position })
    }

    @Test
    fun `a control's date is its day in the academic time zone with the subject's teacher`() {
        val late = Instant.parse("2026-03-11T22:30:00Z")
        val state = fullState().copy(controls = listOf(control(1, "Коллоквиум").copy(date = late)))

        val row = subjectHubItems(state).filterIsInstance<SubjectHubItem.Control>().single()

        assertEquals(LocalDate(2026, 3, 12), row.date)
        assertEquals(TEACHER, row.subjectTeacher)
    }

    @Test
    fun `no controls without a sheet show the empty notice`() {
        val state = fullState().copy(controls = emptyList())

        val items = subjectHubItems(state)

        assertEquals(SubjectHubItem.Notice(null), items.filterIsInstance<SubjectHubItem.Notice>().single())
        assertEquals(0, items.count { it == SubjectHubItem.Section(Res.string.subject_controls_title) })
    }

    @Test
    fun `a sheet replaces the empty notice but not a failure`() {
        val hinted = fullState().copy(controls = emptyList()).withHub { copy(sheet = SubjectSheetState.Hint(emptyList())) }

        assertEquals(emptyList(), subjectHubItems(hinted).filterIsInstance<SubjectHubItem.Notice>())
        assertEquals(
            listOf(SubjectHubItem.Notice(AppError.Network.textResource())),
            subjectHubItems(hinted.copy(controlsError = AppError.Network)).filterIsInstance<SubjectHubItem.Notice>(),
        )
    }

    @Test
    fun `a failure to load the controls is a notice even with controls cached`() {
        val state = fullState().copy(controlsError = AppError.Network)

        val items = subjectHubItems(state)

        assertEquals(
            listOf(SubjectHubItem.Notice(AppError.Network.textResource())),
            items.filterIsInstance<SubjectHubItem.Notice>(),
        )
        assertEquals(emptyList(), items.filterIsInstance<SubjectHubItem.Control>())
    }

    @Test
    fun `the hero carries the grade step and the sheet`() {
        val sheet = SubjectSheetState.Hint(listOf(SheetLinkOption(SHEET_URL, null, mine = true)))
        val state = fullState().withHub { copy(sheet = sheet) }

        val hero = assertIs<SubjectHubItem.Hero>(subjectHubItems(state).first())

        assertEquals(state.gradeStep, hero.step)
        assertEquals(sheet, hero.sheet)
    }

    @Test
    fun `physical education starts with the sport overview and has no empty notice`() {
        val sport = RecordbookSportState.Error
        val state = RecordbookSubjectUiState.Content(
            subject = subject(PE, score = null),
            controls = emptyList(),
            sport = sport,
            timeZone = MOSCOW,
        )

        val items = subjectHubItems(state)

        assertEquals(listOf(SubjectHubItem.SportOverview(state.subject, sport)), items)
    }

    private inline fun <reified T : SubjectHubItem> List<SubjectHubItem>.positionsOf(
        after: StringResource? = null,
    ): List<GroupPosition> = (if (after == null) this else sectionAfter(after)).filterIsInstance<T>().map { it.position() }

    /** The rows between the heading [title] and the next heading. */
    private fun List<SubjectHubItem>.sectionAfter(title: StringResource): List<SubjectHubItem> =
        dropWhile { it != SubjectHubItem.Section(title) }
            .drop(1)
            .takeWhile { it !is SubjectHubItem.Section }

    private fun RecordbookSubjectUiState.Content.withHub(change: SubjectHubState.() -> SubjectHubState) =
        copy(hub = hub.change())

    private fun SubjectHubItem.position(): GroupPosition = when (this) {
        is SubjectHubItem.Link -> position
        is SubjectHubItem.Lms -> position
        is SubjectHubItem.AllLinks -> position
        is SubjectHubItem.AddLink -> position
        is SubjectHubItem.Chat -> position
        is SubjectHubItem.Control -> position
        is SubjectHubItem.Lesson -> position
        is SubjectHubItem.AllLessons -> position
        is SubjectHubItem.Teacher -> position
        else -> error("$this is not a group row")
    }

    /** An exam with two links, the LMS page and a third link in the short list, one chat, groups and lessons. */
    private fun fullState(): RecordbookSubjectUiState.Content {
        val scope = ResourceScope(SUBJECT_ID, ALGORITHMS, "2025-2")
        return RecordbookSubjectUiState.Content(
            subject = subject(ALGORITHMS, score = 58.5),
            controls = listOf(
                control(11, "Лабораторная работа 1"),
                control(12, "Лабораторная работа 2"),
                control(13, "Лабораторная работа 3"),
                control(16, "Коллоквиум"),
                control(19, "Экзамен"),
            ),
            sport = null,
            hub = SubjectHubState(
                lessons = SubjectLessonsState.Content((1..4).map(::lesson), SubjectContext.Source.EXACT),
                teachers = listOf(
                    SubjectTeacher(TEACHER, 300001, listOf(1)),
                    SubjectTeacher("Смирнов Алексей", null, listOf(3)),
                ),
                resourceScope = scope,
                chips = SubjectLinkChips(
                    listOf(
                        SubjectLinkChip.Link(link("a", scope, LinkCategory.SCORES)),
                        SubjectLinkChip.Lms(LMS),
                        SubjectLinkChip.Link(link("b", scope, LinkCategory.MATERIALS)),
                    ),
                    moreCount = 3,
                ),
                chats = listOf(link("chat", scope, LinkCategory.CHAT)),
                linkCount = 5,
                canVote = true,
                teacherLevels = mapOf(300001L to TeacherLevel.POSITIVE),
            ),
            timeZone = MOSCOW,
        )
    }

    private fun subject(name: String, score: Double?) =
        RecordbookSubject(name, SUBJECT_ID, SUBJECT_ID, "Экзамен", score, null, null, null, true, TEACHER)

    private fun control(id: Long, name: String) = RecordbookControl(id, name, 5.0, 3.0, 10.0, true, null, null)

    private fun lesson(day: Int) = SubjectLesson(
        pairId = day.toLong(), date = LocalDate(2026, 3, day), start = LocalTime(10, 0), end = LocalTime(11, 30),
        typeId = 1, type = "", subjectId = SUBJECT_ID, subjectName = ALGORITHMS, flowId = 10, teacherIsu = null,
        teacherFio = null, room = null, building = null, formatId = 1,
    )

    private fun link(id: String, scope: ResourceScope, category: LinkCategory) = SubjectLink(
        id = id, scope = scope, category = category, url = "https://example.org/$id", title = null,
        visibility = LinkVisibility.ALL, flowId = null, audienceLabel = null, status = SubjectLinkStatus.PUBLISHED,
        reviewNote = null, score = 0, myVote = 0, isMine = false, reportedByMe = false, author = null,
        updatedAt = Instant.parse("2026-03-01T00:00:00Z"),
    )

    private companion object {
        const val SUBJECT_ID = 2L
        const val ALGORITHMS = "Алгоритмы и структуры данных"
        const val PE = "Физическая культура и спорт (элективная)"
        const val TEACHER = "Иванова Мария Сергеевна"
        const val SHEET_URL = "https://docs.google.com/spreadsheets/d/x"
        const val LMS = "https://lms.itmo.ru/course/1"
        val MOSCOW = TimeZone.of("Europe/Moscow")
    }
}
