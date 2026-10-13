package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonState
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.LessonTimelines
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkRanking
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.displayTitle
import dev.alllexey.itmowidgets.core.resources.host
import dev.alllexey.itmowidgets.core.resources.linkIcon
import dev.alllexey.itmowidgets.core.resources.title
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.tone
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.shortPersonName
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlEntry
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroup
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroupKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookControlGroups
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookGradeScale
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.formatRecordbookNumber
import dev.alllexey.itmowidgets.feature.recordbook.domain.isBelowMinimum
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookAssessmentKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/** What the subject page knows about the subject's scores, before it is put into words. */
internal data class SubjectScoresInput(
    val subject: RecordbookSubject,
    val fromBars: Boolean,
    val barsEnabled: Boolean,
    val barsError: AppError?,
    val controls: List<RecordbookControl>,
    val controlsError: AppError?,
    val sport: RecordbookSportState?,
    val sheet: SubjectSheetState?,
    val links: SubjectLinksState?,
    val scope: ResourceScope,
    val today: LocalDate,
    val timeZone: TimeZone,
)

/** The subject page's tabs in words: pure, so the rules are tested without a ViewModel. */
internal object SubjectPresenter {

    // Баллы ---------------------------------------------------------------------------------------------------------

    fun scores(input: SubjectScoresInput): SubjectScores {
        val connected = input.sheet as? SubjectSheetState.Connected
        val groups = groups(input.controls, input.timeZone)
        return SubjectScores(
            hero = hero(input, connected),
            barsNotice = input.barsError?.let {
                if (it == AppError.Unauthorized) RecordbookBarsNotice.Expired else RecordbookBarsNotice.Failed(it)
            },
            sheet = connected?.let { sheetLine(it, input) },
            sport = (input.sport as? RecordbookSportState.Content)?.takeIf { input.subject.isPhysicalEducation }
                ?.let { UiText.Res(RecordbookStrings.recordbook_points_out_of, listOf(it.score.total.toString(), MAX_POINTS_TEXT)) },
            groups = groups,
            controlsError = input.controlsError,
            barsOffer = !input.barsEnabled && groups.isEmpty() && input.controlsError == null &&
                !input.subject.isPhysicalEducation,
            sheetOffer = (input.sheet as? SubjectSheetState.Hint)?.links.orEmpty().map { option ->
                val title = option.title?.let(UiText::Dynamic) ?: UiText.Res(RecordbookStrings.sheet_scores_link_untitled)
                SubjectSheetOption(
                    title = if (option.mine) UiText.Res(RecordbookStrings.sheet_scores_link_mine, listOf(title)) else title,
                    args = sheetArgs(input.scope, option.url, SheetScoresArgs.Step.CONNECT),
                )
            },
            noDetails = input.barsEnabled && groups.isEmpty() && input.controlsError == null &&
                !input.subject.isPhysicalEducation,
        )
    }

    /** The sheet's total while the subject is in progress, else BARS or My ITMO, as on the list. */
    private fun hero(input: SubjectScoresInput, sheet: SubjectSheetState.Connected?): SubjectHero {
        val subject = input.subject
        val inProgress = subject.normalizedRate == RecordbookRate.InProgress && !subject.absent
        val sheetValue = sheet?.score?.value?.takeIf { inProgress && !input.fromBars && it.isNotBlank() }
        val points = sheetValue?.replace(',', '.')?.replace(" ", "")?.toDoubleOrNull()
            ?: subject.score?.takeIf { it.isFinite() && it >= 0 }
        val source = when {
            input.fromBars -> UiText.Res(RecordbookStrings.recordbook_source_bars)
            sheetValue != null -> sheet.updatedAt?.let { at ->
                UiText.Res(RecordbookStrings.subject_source_at, listOf(UiText.Res(RecordbookStrings.recordbook_source_sheet), moment(at, input.today)))
            } ?: UiText.Res(RecordbookStrings.recordbook_source_sheet)
            else -> UiText.Res(RecordbookStrings.recordbook_source_myitmo)
        }
        val credit = subject.assessmentKind == RecordbookAssessmentKind.CREDIT
        val (grade, tone) = when {
            subject.absent -> UiText.Res(RecordbookStrings.recordbook_absent) to RecordbookTone.BAD
            !inProgress -> resultText(subject) to
                if (subject.status == RecordbookSubjectStatus.PASSED) RecordbookTone.OK else RecordbookTone.BAD
            credit || points == null -> null to RecordbookTone.PRIMARY
            else -> RecordbookGradeScale.gradeFor(points)?.let(UiText::Dynamic) to RecordbookTone.PRIMARY
        }
        return SubjectHero(
            points = sheetValue ?: points?.let(::formatRecordbookNumber),
            share = ((points ?: 0.0) / MAX_POINTS).coerceIn(0.0, 1.0).toFloat(),
            grade = grade,
            gradeTone = tone,
            source = source,
            ticks = if (credit) {
                listOf(SubjectScaleTick(UiText.Res(RecordbookStrings.subject_credit_mark), RecordbookGradeScale.CREDIT_SCORE))
            } else {
                RecordbookGradeScale.lowestScores.map { (code, score) -> SubjectScaleTick(UiText.Dynamic(code.takeLast(1)), score) }
            },
            next = if (inProgress && !subject.isPhysicalEducation) next(points, subject.assessmentKind) else null,
        )
    }

    private fun resultText(subject: RecordbookSubject): UiText = when (val rate = subject.normalizedRate) {
        RecordbookRate.Credit -> UiText.Res(RecordbookStrings.recordbook_rate_credit)
        is RecordbookRate.Grade -> UiText.Dynamic(rate.code)
        RecordbookRate.InProgress -> UiText.Res(RecordbookStrings.recordbook_rate_in_progress)
    }

    /** `до 3D ещё 4,5`, `до зачёта ещё 3`. */
    private fun next(points: Double?, kind: RecordbookAssessmentKind): UiText? {
        val step = RecordbookGradeScale.nextStep(points, kind) ?: return null
        val remaining = formatRecordbookNumber(step.remaining)
        return if (step.target == RecordbookGradeScale.CREDIT_TARGET) {
            UiText.Res(RecordbookStrings.subject_credit_next, listOf(remaining))
        } else {
            UiText.Res(RecordbookStrings.subject_grade_next, listOf(step.target, remaining))
        }
    }

    private fun sheetLine(sheet: SubjectSheetState.Connected, input: SubjectScoresInput): SubjectSheetLine {
        val score = sheet.score
        val snapshot = (input.links as? SubjectLinksState.Content)?.snapshot
        val title = snapshot?.let { it.mine + it.shared + it.previous }
            ?.firstOrNull { it.url.trim() == score.url.trim() }?.title
        return SubjectSheetLine(
            title = title?.let(UiText::Dynamic) ?: UiText.Res(RecordbookStrings.sheet_scores_link_untitled),
            caption = UiText.Res(RecordbookStrings.subject_sheet_caption, listOf(score.tabName, score.column.headerPath)),
            problem = when (score.status) {
                SheetStatus.OK -> null
                SheetStatus.NETWORK -> UiText.Res(RecordbookStrings.sheet_scores_offline)
                SheetStatus.CLOSED -> UiText.Res(RecordbookStrings.sheet_scores_closed)
                SheetStatus.ROW_NOT_FOUND -> UiText.Res(RecordbookStrings.sheet_scores_row_not_found)
                SheetStatus.COLUMN_NOT_FOUND -> UiText.Res(RecordbookStrings.sheet_scores_column_not_found)
                SheetStatus.TOO_LARGE -> UiText.Res(RecordbookStrings.sheet_scores_too_large)
            },
            url = score.tabUrl,
            changeTotal = sheetArgs(input.scope, score.url, SheetScoresArgs.Step.TOTAL),
        )
    }

    private fun sheetArgs(scope: ResourceScope, url: String, step: SheetScoresArgs.Step) =
        SheetScoresArgs(scope.subjectId, scope.subjectName, scope.periodKey, url, step)

    /** Groups in server order; lone checkpoints in a row share one untitled group. */
    private fun groups(controls: List<RecordbookControl>, zone: TimeZone): List<SubjectControlGroup> {
        val result = mutableListOf<SubjectControlGroup>()
        val singles = mutableListOf<RecordbookControl>()
        fun flush() {
            if (singles.isEmpty()) return
            result += SubjectControlGroup(null, null, singles.any { it.isBelowMinimum }, singles.map { row(it, zone) })
            singles.clear()
        }
        RecordbookControlGroups.groupControls(controls).forEach { entry ->
            when (entry) {
                is ControlEntry.Single -> singles += entry.control
                is ControlGroup -> {
                    flush()
                    result += SubjectControlGroup(
                        title = entry.kind?.let { UiText.Res(groupTitle(it)) } ?: UiText.Dynamic(entry.title),
                        sum = entry.maximum?.let { maximum ->
                            UiText.Res(
                                RecordbookStrings.subject_group_sum,
                                listOf(
                                    entry.score?.let { UiText.Dynamic(formatRecordbookNumber(it)) }
                                        ?: UiText.Res(RecordbookStrings.recordbook_score_pending),
                                    formatRecordbookNumber(maximum),
                                ),
                            )
                        },
                        below = entry.belowMinimum.isNotEmpty(),
                        rows = entry.controls.map { row(it, zone) },
                    )
                }
            }
        }
        flush()
        return result
    }

    private fun groupTitle(kind: ControlGroupKind): String = when (kind) {
        ControlGroupKind.LABS -> RecordbookStrings.recordbook_group_labs
        ControlGroupKind.TESTS -> RecordbookStrings.recordbook_group_tests
        ControlGroupKind.PRACTICALS -> RecordbookStrings.recordbook_group_practicals
        ControlGroupKind.HOMEWORK -> RecordbookStrings.recordbook_group_homework
    }

    private fun row(control: RecordbookControl, zone: TimeZone): SubjectControlRow {
        val score = control.score?.takeIf { it.isFinite() }
        val maximum = control.maximum?.takeIf { it.isFinite() && it > 0 }
        val share = if (score != null && maximum != null) (score / maximum).coerceIn(0.0, 1.0).toFloat() else null
        val scoreText = score?.let { UiText.Dynamic(formatRecordbookNumber(it)) } ?: UiText.Res(RecordbookStrings.recordbook_score_pending)
        return SubjectControlRow(
            id = control.id,
            name = control.name,
            score = maximum?.let { UiText.Res(RecordbookStrings.recordbook_control_score, listOf(scoreText, formatRecordbookNumber(it))) }
                ?: scoreText,
            graded = score != null,
            share = share,
            tone = when {
                control.isBelowMinimum -> RecordbookTone.BAD
                share != null && share >= GOOD_SHARE -> RecordbookTone.OK
                else -> RecordbookTone.PRIMARY
            },
            minimum = control.minimum?.takeIf { it > 0 && it.isFinite() }
                ?.let { UiText.Res(RecordbookStrings.recordbook_control_minimum, listOf(formatRecordbookNumber(it))) },
            below = control.isBelowMinimum,
            date = control.date?.toLocalDateTime(zone)?.date?.let { UiText.Dynamic(shortDate(it)) },
            absent = control.absent,
        )
    }

    // Пары ----------------------------------------------------------------------------------------------------------

    fun teachers(teachers: List<SubjectTeacher>, levels: Map<Long, TeacherLevel>, connected: Boolean): List<SubjectTeacherRow> =
        teachers.map { teacher ->
            val tone = teacher.isu?.let(levels::get)?.takeIf { connected }?.tone()?.words
            val roles = teacher.roles.take(MAX_ROLES).mapIndexed { index, role ->
                val name = roleName(role)
                if (index == 0) name else UiText.LowercaseFirst(name)
            }
            SubjectTeacherRow(
                name = shortPersonName(teacher.name),
                initials = initials(teacher.name),
                detail = (roles + listOfNotNull(tone)).takeIf { it.isNotEmpty() }?.let { UiText.Joined(it, ", ") },
                isu = UserScreenArgs.profileIsu(teacher.isu),
            )
        }

    fun reviews(teachers: List<SubjectTeacher>, levels: Map<Long, TeacherLevel>, connected: Boolean): SubjectReviews {
        if (!connected) return SubjectReviews.Off
        val first = teachers.firstOrNull { UserScreenArgs.profileIsu(it.isu) != null } ?: return SubjectReviews.Shown
        if (teachers.any { it.isu != null && it.isu in levels }) return SubjectReviews.Shown
        return SubjectReviews.Few(TeacherReviewArgs(UserScreenArgs.profileIsu(first.isu)!!, first.name))
    }

    private fun roleName(typeId: Int): UiText = when (typeId) {
        LECTURE -> UiText.Res(RecordbookStrings.subject_role_lectures)
        LAB -> UiText.Res(RecordbookStrings.subject_role_labs)
        PRACTICE -> UiText.Res(RecordbookStrings.subject_role_practice)
        else -> lessonTypeName(typeId)
    }

    /** `ЕН` for `Ершов Никита Андреевич`: surname and name. */
    private fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter(String::isNotEmpty).take(2).joinToString("") { it.first().uppercase() }

    // Материалы -----------------------------------------------------------------------------------------------------

    /**
     * Chats first, then links by category in the category order, each by rank in [arrange]'s stable order. Without
     * the connection only the gate.
     */
    fun materials(
        links: SubjectLinksState?,
        connected: Boolean,
        canVote: Boolean,
        scope: ResourceScope,
        arrange: (List<SubjectLink>) -> List<SubjectLink>,
    ): SubjectMaterials {
        if (!connected) return SubjectMaterials.Gate
        val args = SubjectLinksArgs(scope.subjectId, scope.subjectName, scope.periodKey)
        val snapshot = when (links) {
            null, SubjectLinksState.Loading -> return SubjectMaterials.Loading
            is SubjectLinksState.Error -> return SubjectMaterials.Failed(links.error)
            is SubjectLinksState.Content -> links.snapshot
        }
        val current = (snapshot.mine + snapshot.shared).distinctBy { it.id }
        val previous = snapshot.previous.count { old -> current.none { it.id == old.id } }
        if (current.isEmpty() && previous == 0) return SubjectMaterials.Empty(args)
        val ranked = arrange(current.sortedWith(SubjectLinkRanking))
        val pinned = ranked.filter { it.id == snapshot.pinnedId }
        val ordered = pinned + ranked.filter { it.id != snapshot.pinnedId }
        return SubjectMaterials.Content(
            args = args,
            chats = ordered.filter { it.category == LinkCategory.CHAT }.map { linkRow(it, canVote, chat = true) },
            sections = LinkCategory.entries.filter { it != LinkCategory.CHAT }.mapNotNull { category ->
                ordered.filter { it.category == category }.takeIf { it.isNotEmpty() }
                    ?.let { SubjectLinkSection(category.title(), it.map { link -> linkRow(link, canVote, chat = false) }) }
            },
            previous = previous,
        )
    }

    private fun linkRow(link: SubjectLink, canVote: Boolean, chat: Boolean): SubjectLinkRow {
        val (status, tone) = when (link.status.takeIf { link.isMine }) {
            SubjectLinkStatus.PENDING -> UiText.Res(RecordbookStrings.subject_link_pending) to RecordbookTone.WARN
            SubjectLinkStatus.REJECTED -> UiText.Res(RecordbookStrings.subject_link_rejected) to RecordbookTone.BAD
            SubjectLinkStatus.HIDDEN -> UiText.Res(RecordbookStrings.subject_link_hidden) to RecordbookTone.BAD
            SubjectLinkStatus.PRIVATE -> UiText.Res(RecordbookStrings.subject_link_private) to RecordbookTone.NEUTRAL
            SubjectLinkStatus.PUBLISHED, null -> null to RecordbookTone.NEUTRAL
        }
        return SubjectLinkRow(
            id = link.id,
            title = link.displayTitle(),
            line = UiText.Joined(listOfNotNull(UiText.Dynamic(link.host()), link.audienceLabel?.let(UiText::Dynamic)), ", "),
            icon = linkIcon(link.category, link.url),
            url = link.url,
            score = link.score.takeUnless { chat },
            myVote = link.myVote,
            canVote = canVote && !link.isMine && !link.local,
            own = link.isMine,
            status = status,
            statusTone = tone,
        )
    }

    // Shared --------------------------------------------------------------------------------------------------------

    /** `ср, 9 сент.`. */
    fun lessonDay(date: LocalDate): UiText = UiText.Dynamic(date.format(DateTexts.SHORT_WEEKDAY_DAY_SHORT_MONTH))

    /** A lesson ahead as the lesson card says it: the same rules as the schedule (`LessonTimelines`). */
    fun lessonItem(lesson: SubjectLesson): SubjectLessonItem =
        SubjectLessonItem(lesson.pairId, lessonDay(lesson.date), LessonTimelines.lesson(lesson.toDayLesson(), LessonState.UPCOMING))

    private fun SubjectLesson.toDayLesson() = DayLesson(
        start = start,
        end = end,
        subject = subjectName,
        typeId = typeId,
        teacher = teacherFio,
        room = room,
        building = building,
        online = formatId == REMOTE_FORMAT,
        target = LessonTarget.Lesson(details ?: fallbackDetails()),
        note = details?.note,
        videoUrl = details?.zoomUrl,
    )

    /** What the lesson sheet can show without the schedule's full lesson. */
    private fun SubjectLesson.fallbackDetails() = LessonDetailsArgs(
        pairId = pairId, date = date.toString(), subjectName = subjectName, typeId = typeId, format = "",
        start = start.toString(), end = end.toString(), teacherFio = teacherFio, teacherIsu = teacherIsu, room = room,
        building = building, buildingId = null, mainBuildingId = null, note = null, zoomUrl = null, zoomPassword = null,
        zoomInfo = null,
    )

    /** `11:05` today, `4 сент.` before. */
    private fun moment(at: LocalDateTime, today: LocalDate): String =
        if (at.date == today) at.time.format(DateTexts.TIME) else shortDate(at.date)

    /** `2 сент.`. */
    private fun shortDate(date: LocalDate): String = "${date.format(DateTexts.DAY)} ${date.format(DateTexts.MONTH_SHORT)}"

    /** MyITMO's `format_id` of a remote lesson. */
    private const val REMOTE_FORMAT = 3
    private const val MAX_POINTS = 100.0
    private const val MAX_POINTS_TEXT = "100"
    private const val GOOD_SHARE = 0.6f
    private const val MAX_ROLES = 2
    private const val LECTURE = 1
    private const val LAB = 2
    private const val PRACTICE = 3
}
