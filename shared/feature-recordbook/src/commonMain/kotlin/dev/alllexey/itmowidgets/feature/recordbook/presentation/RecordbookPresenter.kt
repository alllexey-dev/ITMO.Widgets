package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookBarsMerge
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.formatRecordbookNumber
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookAssessmentKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookPlurals
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** BARS as the list knows it. */
internal sealed interface RecordbookBarsData {
    /** Not connected in the recordbook. */
    data object Off : RecordbookBarsData

    /** Connected; the answer for this period has not come yet. */
    data object Pending : RecordbookBarsData

    /** The journals of this period, answered [at]. */
    data class Applied(val journals: List<RecordbookSubject>, val at: Instant) : RecordbookBarsData

    /** The session ended; [journals] of an earlier answer at [at] stay, dimmed. */
    data class Expired(val journals: List<RecordbookSubject>?, val at: Instant?) : RecordbookBarsData

    data class Failed(val error: AppError) : RecordbookBarsData
}

/** A connected sheet's total of a subject and when it was read. */
internal data class RecordbookSheetTotal(val value: String, val updatedAt: Instant?)

/** What the list has loaded; [RecordbookPresenter] puts it into words. */
internal data class RecordbookInput(
    val selection: RecordbookSelection,
    val official: List<RecordbookSubject>,
    val bars: RecordbookBarsData,
    val sport: RecordbookSportState?,
    val attention: Map<Long, RecordbookAttentionReason>,
    val unread: Set<String>,
    val sheets: Map<Long, RecordbookSheetTotal>,
    val view: RecordbookView?,
    val offerHidden: Boolean,
    val sourcesId: Long?,
    val today: LocalDate,
    val timeZone: TimeZone,
)

/** The recordbook list in words: pure, so the rules are tested without a ViewModel. */
internal object RecordbookPresenter {

    /** `2 курс, 2025/2026`. */
    fun subtitle(selection: RecordbookSelection): UiText =
        UiText.Res(RecordbookStrings.recordbook_subtitle, listOf(selection.period.course, selection.period.studyYear))

    /** The header's action and its menu; a programme is named only when there are several. */
    fun periods(programs: List<RecordbookProgram>, selection: RecordbookSelection): RecordbookPeriods =
        RecordbookPeriods(
            label = semester(selection.period.semester),
            groups = programs.filter { it.periods.isNotEmpty() }.map { program ->
                RecordbookPeriodGroup(
                    program = program.name.takeIf { programs.size > 1 },
                    options = program.periods.sortedByDescending { it.semester }.map { period ->
                        RecordbookPeriodOption(
                            programId = program.id,
                            semester = period.semester,
                            title = semester(period.semester),
                            detail = UiText.Res(RecordbookStrings.recordbook_subtitle, listOf(period.course, period.studyYear)),
                            selected = program.id == selection.program.id && period.semester == selection.period.semester,
                        )
                    },
                )
            },
        )

    /** The session once any subject has a final result; the term before. */
    fun defaultView(official: List<RecordbookSubject>): RecordbookView =
        if (official.any { it.normalizedRate != RecordbookRate.InProgress && !it.absent }) RecordbookView.SESSION
        else RecordbookView.SEMESTER

    fun content(input: RecordbookInput): RecordbookBody.Content {
        val view = input.view ?: defaultView(input.official)
        val journals = when (val bars = input.bars) {
            is RecordbookBarsData.Applied -> bars.journals
            is RecordbookBarsData.Expired -> bars.journals
            else -> null
        }
        val shown = journals?.let { RecordbookBarsMerge.apply(input.official, it) } ?: input.official
        val entries = input.official.zip(shown) { official, merged -> Entry(official, merged) }
        val rows = entries.associate { it.official.entryId to row(it, input) }
        val sections = when (view) {
            RecordbookView.SEMESTER -> listOfNotNull(
                section(RecordbookStrings.recordbook_attention_section, entries.filter { it.official.entryId in input.attention }, rows),
                section(RecordbookStrings.recordbook_disciplines_section, entries.filter { it.official.entryId !in input.attention }, rows),
            )
            RecordbookView.SESSION -> listOfNotNull(
                section(RecordbookStrings.recordbook_attention_section, entries.filter { it.official.entryId in input.attention }, rows),
                section(
                    RecordbookStrings.recordbook_section_ahead,
                    entries.filter { it.official.entryId !in input.attention && it.shown.status != RecordbookSubjectStatus.PASSED },
                    rows,
                ),
                section(
                    RecordbookStrings.recordbook_section_passed,
                    entries.filter { it.official.entryId !in input.attention && it.shown.status == RecordbookSubjectStatus.PASSED },
                    rows,
                ),
            )
        }
        return RecordbookBody.Content(
            view = view,
            summary = if (view == RecordbookView.SESSION) summary(shown) else null,
            barsNotice = when (val bars = input.bars) {
                is RecordbookBarsData.Expired -> RecordbookBarsNotice.Expired
                is RecordbookBarsData.Failed -> RecordbookBarsNotice.Failed(bars.error)
                else -> null
            },
            sections = sections,
            barsOffer = input.bars == RecordbookBarsData.Off && !input.offerHidden,
            sources = input.sourcesId?.let { id -> entries.firstOrNull { it.official.entryId == id } }
                ?.takeUnless { it.official.isPhysicalEducation }
                ?.let { sources(it, input) },
        )
    }

    /** A subject as My ITMO lists it and as the list shows it, with BARS laid over when it has a journal. */
    private data class Entry(val official: RecordbookSubject, val shown: RecordbookSubject) {
        val fromBars: Boolean get() = shown.barsJournal != null
    }

    private fun section(title: String, entries: List<Entry>, rows: Map<Long, RecordbookRow>): RecordbookSection? =
        entries.takeIf { it.isNotEmpty() }
            ?.let { RecordbookSection(UiText.Res(title), it.map { entry -> rows.getValue(entry.official.entryId) }) }

    private fun row(entry: Entry, input: RecordbookInput): RecordbookRow {
        val subject = entry.shown
        val reason = input.attention[subject.entryId]
        val metaTone = when (reason) {
            null -> RecordbookTone.NEUTRAL
            is RecordbookAttentionReason.SportShort -> RecordbookTone.WARN
            else -> RecordbookTone.BAD
        }
        val stale = entry.fromBars && input.bars is RecordbookBarsData.Expired
        val source = source(entry, input)
        return RecordbookRow(
            id = subject.entryId,
            name = subject.name,
            meta = reason?.let(::reasonText) ?: meta(subject, input),
            metaTone = metaTone,
            value = value(entry, input, if (reason == null) RecordbookTone.PRIMARY else metaTone),
            source = sourceLabel(source, if (stale) (input.bars as RecordbookBarsData.Expired).at else null, input),
            stale = stale,
            unread = subjectNameKey(subject.name) in input.unread,
            target = if (subject.isPhysicalEducation) RecordbookTarget.Sport else RecordbookTarget.Subject(args(input.selection, subject)),
            hasSources = !subject.isPhysicalEducation,
        )
    }

    /** BARS over a sheet over My ITMO: the fresher number of a subject still in progress. */
    private fun source(entry: Entry, input: RecordbookInput): RecordbookSource = when {
        entry.official.isPhysicalEducation -> RecordbookSource.SPORT
        entry.fromBars -> RecordbookSource.BARS
        sheetShown(entry, input) -> RecordbookSource.SHEET
        else -> RecordbookSource.MY_ITMO
    }

    private fun sheetShown(entry: Entry, input: RecordbookInput): Boolean =
        input.sheets[entry.official.disciplineId] != null && !entry.official.absent &&
            entry.official.normalizedRate == RecordbookRate.InProgress

    private fun value(entry: Entry, input: RecordbookInput, tone: RecordbookTone): RecordbookValue {
        val subject = entry.shown
        if (subject.absent) return RecordbookValue.Result(UiText.Res(RecordbookStrings.recordbook_absent), RecordbookTone.BAD)
        result(subject)?.let { return it }
        if (subject.isPhysicalEducation) {
            val score = (input.sport as? RecordbookSportState.Content)?.score ?: return RecordbookValue.None
            val scale = maxOf(SPORT_SCALE, score.total).toFloat()
            return RecordbookValue.Sport(score.total.toString(), score.attendances / scale, score.creditedBonus / scale)
        }
        if (source(entry, input) == RecordbookSource.SHEET) {
            val text = input.sheets.getValue(subject.disciplineId).value
            val points = text.replace(',', '.').replace(" ", "").toDoubleOrNull()
            return RecordbookValue.Points(text, share(points), tone)
        }
        val score = subject.score?.takeIf { it.isFinite() && it >= 0 } ?: return RecordbookValue.None
        return RecordbookValue.Points(formatRecordbookNumber(score), share(score), tone)
    }

    /** A final result as a pill: passed in green, failed in red. */
    private fun result(subject: RecordbookSubject): RecordbookValue.Result? = when (val rate = subject.normalizedRate) {
        RecordbookRate.InProgress -> null
        RecordbookRate.Credit -> RecordbookValue.Result(UiText.Res(RecordbookStrings.recordbook_rate_credit), RecordbookTone.OK)
        is RecordbookRate.Grade -> RecordbookValue.Result(
            UiText.Dynamic(rate.code),
            if (subject.status == RecordbookSubjectStatus.PASSED) RecordbookTone.OK else RecordbookTone.BAD,
        )
    }

    private fun share(points: Double?): Float = ((points ?: 0.0) / MAX_POINTS).coerceIn(0.0, 1.0).toFloat()

    /** The kind, with the exam date while it is ahead; physical education adds that its points come from sport. */
    private fun meta(subject: RecordbookSubject, input: RecordbookInput): UiText {
        val kind = UiText.Dynamic(subject.controlType.trim())
        if (subject.isPhysicalEducation) return UiText.Res(RecordbookStrings.recordbook_sport_meta, listOf(kind))
        val date = subject.examDate?.toLocalDateTime(input.timeZone)?.date
        if (date != null && date >= input.today && subject.status != RecordbookSubjectStatus.PASSED) {
            return UiText.Joined(listOf(kind, UiText.Dynamic(date.format(DateTexts.DAY_MONTH))), " ")
        }
        return kind
    }

    private fun reasonText(reason: RecordbookAttentionReason): UiText = when (reason) {
        RecordbookAttentionReason.Failed -> UiText.Res(RecordbookStrings.recordbook_reason_failed)
        RecordbookAttentionReason.Absent -> UiText.Res(RecordbookStrings.recordbook_absent)
        is RecordbookAttentionReason.BelowMinimum ->
            UiText.Res(RecordbookStrings.recordbook_reason_below_minimum, listOf(reason.controlName))
        is RecordbookAttentionReason.SportShort ->
            UiText.Plural(RecordbookPlurals.recordbook_reason_sport, reason.remaining, listOf(reason.remaining))
    }

    /** `БАРС`; an old BARS answer adds its time or date: `БАРС, 4 сент.`. */
    private fun sourceLabel(source: RecordbookSource, staleAt: Instant?, input: RecordbookInput): UiText {
        val label = UiText.Res(
            when (source) {
                RecordbookSource.BARS -> RecordbookStrings.recordbook_source_bars
                RecordbookSource.MY_ITMO -> RecordbookStrings.recordbook_source_myitmo
                RecordbookSource.SHEET -> RecordbookStrings.recordbook_source_sheet
                RecordbookSource.SPORT -> RecordbookStrings.recordbook_source_sport
            },
        )
        val at = staleAt?.toLocalDateTime(input.timeZone) ?: return label
        return UiText.Res(RecordbookStrings.recordbook_source_since, listOf(label, moment(at, input.today)))
    }

    /** `11:05` today, `4 сент.` before. */
    private fun moment(at: LocalDateTime, today: LocalDate): String =
        if (at.date == today) at.time.format(DateTexts.TIME)
        else "${at.date.format(DateTexts.DAY)} ${at.date.format(DateTexts.MONTH_SHORT)}"

    private fun summary(subjects: List<RecordbookSubject>): RecordbookSummary {
        val passed = subjects.filter { it.status == RecordbookSubjectStatus.PASSED }
        val graded = passed.filter { !it.isPhysicalEducation && it.assessmentKind != RecordbookAssessmentKind.CREDIT }
            .mapNotNull { it.score?.takeIf(Double::isFinite) }
        return RecordbookSummary(
            passed = UiText.Res(RecordbookStrings.recordbook_session_passed, listOf(passed.size)),
            of = UiText.Res(RecordbookStrings.recordbook_session_of, listOf(subjects.size)),
            share = if (subjects.isEmpty()) 0f else passed.size.toFloat() / subjects.size,
            average = graded.takeIf { it.isNotEmpty() }?.let {
                UiText.Res(RecordbookStrings.recordbook_session_average, listOf(formatRecordbookNumber(it.average())))
            },
        )
    }

    private fun sources(entry: Entry, input: RecordbookInput): RecordbookSources {
        val shown = source(entry, input)
        val sheet = input.sheets[entry.official.disciplineId]
        val official = entry.official.score?.takeIf { it.isFinite() }?.let(::formatRecordbookNumber)
        val barsValue = entry.shown.takeIf { entry.fromBars }?.score?.takeIf { it.isFinite() }?.let(::formatRecordbookNumber)
        val bars = RecordbookSourceRow(
            source = RecordbookSource.BARS,
            title = UiText.Res(RecordbookStrings.recordbook_sources_bars),
            note = UiText.Res(
                when {
                    input.bars == RecordbookBarsData.Off -> RecordbookStrings.recordbook_sources_bars_off
                    input.bars is RecordbookBarsData.Expired -> RecordbookStrings.recordbook_sources_bars_expired
                    entry.fromBars -> RecordbookStrings.recordbook_sources_bars_note
                    else -> RecordbookStrings.recordbook_sources_bars_missing
                },
            ),
            value = barsValue,
            shown = shown == RecordbookSource.BARS,
        )
        val myItmo = RecordbookSourceRow(
            source = RecordbookSource.MY_ITMO,
            title = UiText.Res(RecordbookStrings.recordbook_sources_myitmo),
            note = UiText.Res(RecordbookStrings.recordbook_sources_myitmo_note),
            value = official,
            shown = shown == RecordbookSource.MY_ITMO,
        )
        val table = RecordbookSourceRow(
            source = RecordbookSource.SHEET,
            title = UiText.Res(RecordbookStrings.recordbook_sources_sheet),
            note = when {
                sheet == null -> UiText.Res(RecordbookStrings.recordbook_sources_sheet_off)
                sheet.updatedAt == null -> UiText.Res(RecordbookStrings.recordbook_sources_sheet_note)
                else -> UiText.Res(
                    RecordbookStrings.recordbook_sources_sheet_updated,
                    listOf(moment(sheet.updatedAt.toLocalDateTime(input.timeZone), input.today)),
                )
            },
            value = sheet?.value,
            shown = shown == RecordbookSource.SHEET,
        )
        return RecordbookSources(
            id = entry.official.entryId,
            caption = UiText.Joined(
                listOf(UiText.Dynamic(entry.official.name), UiText.LowercaseFirst(UiText.Dynamic(entry.official.controlType.trim()))),
                ", ",
            ),
            rows = listOf(bars, myItmo, table).sortedByDescending { it.shown },
            note = UiText.Res(
                when (shown) {
                    RecordbookSource.BARS -> RecordbookStrings.recordbook_sources_why_bars
                    RecordbookSource.SHEET -> RecordbookStrings.recordbook_sources_why_sheet
                    else -> RecordbookStrings.recordbook_sources_why_myitmo
                },
            ),
            target = RecordbookTarget.Subject(args(input.selection, entry.shown)),
        )
    }

    private fun args(selection: RecordbookSelection, subject: RecordbookSubject) = RecordbookSubjectArgs(
        entryId = subject.entryId,
        programId = selection.program.id,
        semester = selection.period.semester,
        studyYear = selection.period.studyYear,
        barsPlan = subject.barsJournal?.planId,
        barsType = subject.barsJournal?.type,
        barsIdentifier = subject.barsJournal?.identifier,
    )

    private fun semester(number: Int): UiText = UiText.Res(RecordbookStrings.subject_semester, listOf(number))

    private const val MAX_POINTS = 100.0
    private const val SPORT_SCALE = 100
}
