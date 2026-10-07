package dev.alllexey.itmowidgets.feature.recordbook.ui.preview

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookAttentionReason
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSelection
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookUiState
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPeriodOption
import dev.alllexey.itmowidgets.feature.recordbook.ui.recordbookPeriodOptions

/**
 * Synthetic spring 2025/2026 recordbooks for the previews and host tests, the same scenes the XML references of
 * LR-1c showed (`RecordbookPreviewFixtures`): the middle of the semester with BARS on and the session.
 */
internal object RecordbookPreviewSamples {

    const val MATH = "Математический анализ (продвинутый уровень)"
    const val ALGORITHMS = "Алгоритмы и структуры данных"
    const val PE = "Физическая культура и спорт (элективная)"
    const val DESIGN = "Проектирование и разработка распределённых информационных систем"
    const val LANGUAGE = "Иностранный язык"
    const val HISTORY = "История"
    const val MATH_ID = 1L
    const val ALGORITHMS_ID = 2L
    const val PE_ID = 3L
    const val DESIGN_ID = 4L
    const val LANGUAGE_ID = 5L
    const val HISTORY_ID = 6L
    const val BELOW_MINIMUM = "Контрольная работа 1"

    val program = RecordbookProgram(
        1,
        "Программная инженерия",
        listOf(RecordbookPeriod("2025/2026", 2, 1, true), RecordbookPeriod("2025/2026", 1, 1, false)),
    )
    val selection = RecordbookSelection(program, program.periods.first())
    /**
     * 68 points, 32 short: LR-1c's scene had 64 and 36, but the screenshot harness resolves Compose plurals in the
     * JVM's locale, where 36 took the `other` form; 32 reads the same in `few` and `other`.
     */
    val sport = RecordbookSportState.Content("Весна 2025/2026", SportScoreSummary(52, 16), endsAt = null, current = true)

    private val mathJournal = BarsJournalReference(7, "flow", "6", 2025, 2)
    private val designJournal = BarsJournalReference(8, "flow", "7", 2025, 2)

    /** The middle of the semester after the BARS overlay: two unread marks, a sheet total and short sport. */
    fun content(): RecordbookUiState.Content {
        val subjects = listOf(
            subject(MATH_ID, MATH, "Экзамен", 74.0, journal = mathJournal),
            subject(ALGORITHMS_ID, ALGORITHMS, "Экзамен", 58.5),
            subject(PE_ID, PE, "Зачёт", null),
            subject(DESIGN_ID, DESIGN, "Дифференцированный зачёт", 63.5, journal = designJournal),
            subject(LANGUAGE_ID, LANGUAGE, "Зачёт", 52.0),
            subject(HISTORY_ID, HISTORY, "Экзамен", null),
        )
        return RecordbookUiState.Content(
            programs = listOf(program),
            selection = selection,
            subjects = subjects,
            sport = sport,
            barsApplied = true,
            attention = mapOf(
                MATH_ID to RecordbookAttentionReason.BelowMinimum(BELOW_MINIMUM),
                PE_ID to RecordbookAttentionReason.SportShort(sport.score.remaining),
            ),
            newSubjects = setOf(subjectNameKey(MATH), subjectNameKey(DESIGN)),
            sheetTotals = mapOf(HISTORY_ID to "41,5"),
            barsEnabled = true,
        )
    }

    /** The session: final grades as badges, a failed exam and a no-show under attention, the pass count on top. */
    fun session(): RecordbookUiState.Content {
        val subjects = listOf(
            subject(MATH_ID, MATH, "Экзамен", 48.5, rate = "2/FX"),
            subject(ALGORITHMS_ID, ALGORITHMS, "Экзамен", 76.5, rate = "4/C"),
            subject(PE_ID, PE, "Зачёт", null, rate = "зачет"),
            subject(DESIGN_ID, DESIGN, "Дифференцированный зачёт", 93.0, rate = "5/A"),
            subject(LANGUAGE_ID, LANGUAGE, "Зачёт", 62.0, rate = "зачет"),
            subject(HISTORY_ID, HISTORY, "Экзамен", 41.0).copy(absent = true),
        )
        return RecordbookUiState.Content(
            programs = listOf(program),
            selection = selection,
            subjects = subjects,
            sport = sport,
            attention = mapOf(
                MATH_ID to RecordbookAttentionReason.Failed,
                HISTORY_ID to RecordbookAttentionReason.Absent,
            ),
        )
    }

    fun loading() = RecordbookUiState.Loading(listOf(program), selection)

    fun empty() = RecordbookUiState.Content(listOf(program), selection, subjects = emptyList())

    fun error() = RecordbookUiState.Error(AppError.Network, listOf(program), selection)

    fun periodOptions(): List<RecordbookPeriodOption> = recordbookPeriodOptions(listOf(program))

    fun subject(
        id: Long,
        name: String,
        kind: String,
        score: Double?,
        rate: String? = null,
        journal: BarsJournalReference? = null,
    ) = RecordbookSubject(name, id, id, kind, score, rate, null, null, true, "Иванова Мария Сергеевна", barsJournal = journal)
}
