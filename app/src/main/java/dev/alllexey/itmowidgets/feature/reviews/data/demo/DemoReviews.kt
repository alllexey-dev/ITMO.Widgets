package dev.alllexey.itmowidgets.feature.reviews.data.demo

import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoPerson
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.SummaryConfidence
import dev.alllexey.itmowidgets.core.reviews.SummaryScale
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleKind
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleValue
import dev.alllexey.itmowidgets.core.reviews.SummaryTag
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
import java.time.LocalDate
import java.time.YearMonth

/** Reviews of the demo teachers in different tones, with AI summaries for the teachers who have enough of them. */
object DemoReviews {

    fun reviews(isu: Int, today: LocalDate): TeacherReviews {
        val teacher = DemoPeople.TEACHERS.firstOrNull { it.isu == isu }
            ?: return TeacherReviews(isu, emptyList(), null, canWrite = false, canVote = false, canReport = false, knownTeacher = false)
        val month = YearMonth.from(today)
        return TeacherReviews(
            isu = isu,
            reviews = REVIEWS[isu].orEmpty().mapIndexed { index, review -> review.toModel(teacher, index, month) },
            mine = null,
            canWrite = true,
            canVote = true,
            canReport = true,
            knownTeacher = true,
            summary = SUMMARIES[isu]
        )
    }

    /** Tones of the teachers whose summary is confident enough to show one. */
    fun levels(isus: Set<Int>): Map<Int, TeacherLevel> =
        SUMMARIES.filterKeys { it in isus }.filterValues { it.showsLevel }.mapValues { it.value.level }

    private data class DemoReview(
        val subject: String?,
        val monthsAgo: Long,
        val text: String,
        val score: Int,
        val author: DemoPerson?,
        val verified: Boolean = true
    ) {
        fun toModel(teacher: DemoPerson, index: Int, month: YearMonth) = TeacherReview(
            id = "00000000-0000-4000-8000-%012d".format(teacher.isu.toLong() * 100 + index),
            subject = subject,
            written = ReviewDate.Month(month.minusMonths(monthsAgo)),
            text = text,
            score = score,
            myVote = 0,
            origin = ReviewOrigin.Community(verified = verified, author = author?.summary(), reportedByMe = false)
        )
    }

    private fun scales(vararg values: Pair<SummaryScaleKind, Pair<SummaryScaleValue, String?>>) =
        values.map { (kind, value) -> SummaryScale(kind, value.first, value.second) }

    private val REVIEWS: Map<Int, List<DemoReview>> = mapOf(
        DemoPeople.MATH_TEACHER.isu to listOf(
            DemoReview(
                DemoStudy.MATH.name, 1,
                "Лекции очень структурные: каждое доказательство разбирается до конца, а в конце занятия есть короткое " +
                    "резюме. На практике спрашивает теорию, так что конспект лучше учить сразу.",
                14, DemoPeople.IVAN
            ),
            DemoReview(
                DemoStudy.MATH.name, 2,
                "Требования понятные, баллы за контрольные выставляет быстро. Экзамен сложный, но всё, что на нём было, " +
                    "разбирали на лекциях.",
                9, null
            ),
            DemoReview(
                DemoStudy.MATH.name, 5,
                "Темп высокий, если пропустить пару занятий, догонять тяжело. Консультации перед контрольными спасают.",
                4, null
            ),
            DemoReview(
                "Линейная алгебра", 13,
                "Строгий, но справедливый. Опоздания не любит, на вопросы после пары отвечает подробно.",
                2, DemoPeople.DMITRY, verified = false
            )
        ),
        DemoPeople.DISCRETE_TEACHER.isu to listOf(
            DemoReview(
                DemoStudy.DISCRETE.name, 1,
                "Домашние задания объёмные, зато после них контрольная кажется простой. Дедлайны жёсткие, переносов нет.",
                6, DemoPeople.MARIA
            ),
            DemoReview(
                DemoStudy.DISCRETE.name, 3,
                "Объясняет материал по слайдам и почти не отвлекается на вопросы. Задачи интересные, но разбирать их " +
                    "приходится самому.",
                -2, null
            )
        ),
        DemoPeople.ALGORITHMS_TEACHER.isu to listOf(
            DemoReview(
                DemoStudy.ALGORITHMS.name, 1,
                "Лучший курс семестра. Лабораторные на реальных задачах, на защите спрашивает, почему выбран именно этот " +
                    "алгоритм, а не просто проверяет код.",
                17, DemoPeople.IVAN
            ),
            DemoReview(
                DemoStudy.ALGORITHMS.name, 2,
                "Отвечает в чате быстро, даже вечером. За досрочную сдачу лабораторных даёт дополнительные баллы.",
                8, null
            ),
            DemoReview(
                "Программирование", 12,
                "Много лабораторных, сидеть над ними приходится каждую неделю. Но к концу курса пишешь заметно лучше.",
                5, DemoPeople.DMITRY
            )
        ),
        DemoPeople.DATABASES_TEACHER.isu to listOf(
            DemoReview(
                DemoStudy.DATABASES.name, 1,
                "Очень понятно рассказывает про нормализацию и индексы, на каждой лабораторной есть время спросить. " +
                    "Посещение не отмечает, но пропускать не хочется.",
                11, DemoPeople.MARIA
            ),
            DemoReview(
                DemoStudy.DATABASES.name, 2,
                "Задания на лабораторных похожи на рабочие задачи: проектируем схему для настоящего сервиса. Проверяет " +
                    "аккуратно и объясняет ошибки.",
                7, null
            )
        ),
        DemoPeople.ENGLISH_TEACHER.isu to listOf(
            DemoReview(
                DemoStudy.ENGLISH.name, 2,
                "Занятия разговорные, много работы в парах. Для зачёта нужно сдать все эссе вовремя и выступить с докладом.",
                3, null
            )
        ),
        DemoPeople.VOLLEYBALL_COACH.isu to listOf(
            DemoReview(
                "Физическая культура", 4,
                "Тренировки весёлые, новичков ставят в отдельную группу. Отметку о посещении ставит сразу после занятия.",
                6, DemoPeople.IVAN
            )
        )
    )

    private val SUMMARIES: Map<Int, TeacherSummary> = mapOf(
        DemoPeople.MATH_TEACHER.isu to TeacherSummary(
            reviewCount = 4,
            description = "Строгий и последовательный лектор: подробно доказывает теоремы и требует знания теории. " +
                "Экзамен считают сложным, но честным.",
            pros = listOf("структурные лекции", "понятные требования", "быстрая проверка контрольных"),
            cons = listOf("высокий темп", "сложный экзамен"),
            tags = listOf(SummaryTag.CLEAR_REQUIREMENTS, SummaryTag.ASKS_THEORY, SummaryTag.HARD_EXAM),
            scales = scales(
                SummaryScaleKind.EXPLAINS to (SummaryScaleValue.HIGH to "Разбирает доказательства до конца"),
                SummaryScaleKind.ATTITUDE to (SummaryScaleValue.MEDIUM to "Строгий, но отвечает на вопросы"),
                SummaryScaleKind.FAIRNESS to (SummaryScaleValue.HIGH to "Экзамен по материалу лекций"),
                SummaryScaleKind.STRICTNESS to (SummaryScaleValue.HIGH to "Спрашивает теорию на практике"),
                SummaryScaleKind.WORKLOAD to (SummaryScaleValue.MEDIUM to "Нагрузка ровная, без авралов")
            ),
            level = TeacherLevel.POSITIVE,
            confidence = SummaryConfidence.HIGH
        ),
        DemoPeople.DISCRETE_TEACHER.isu to TeacherSummary(
            reviewCount = 2,
            description = "Отзывы расходятся: задачи хвалят, а подачу материала по слайдам и жёсткие дедлайны критикуют.",
            pros = listOf("интересные задачи"),
            cons = listOf("лекции по слайдам", "объёмные домашние задания"),
            tags = listOf(SummaryTag.HEAVY_HOMEWORK, SummaryTag.STRICT_DEADLINES, SummaryTag.READS_SLIDES),
            scales = scales(
                SummaryScaleKind.EXPLAINS to (SummaryScaleValue.LOW to "Материал читается по слайдам"),
                SummaryScaleKind.ATTITUDE to (SummaryScaleValue.NOT_ENOUGH_DATA to null),
                SummaryScaleKind.FAIRNESS to (SummaryScaleValue.MEDIUM to "Контрольные по домашним заданиям"),
                SummaryScaleKind.STRICTNESS to (SummaryScaleValue.HIGH to "Дедлайны не переносятся"),
                SummaryScaleKind.WORKLOAD to (SummaryScaleValue.HIGH to "Домашние задания каждую неделю")
            ),
            level = TeacherLevel.MIXED,
            confidence = SummaryConfidence.MEDIUM
        ),
        DemoPeople.ALGORITHMS_TEACHER.isu to TeacherSummary(
            reviewCount = 3,
            description = "Студенты ценят практичные лабораторные и вдумчивые защиты; нагрузка большая, но полезная.",
            pros = listOf("практичные лабораторные", "быстрые ответы", "бонусные баллы"),
            cons = listOf("много лабораторных"),
            tags = listOf(SummaryTag.MANY_LABS, SummaryTag.BONUS_POINTS, SummaryTag.QUICK_REPLIES, SummaryTag.INTERESTING_CLASSES),
            scales = scales(
                SummaryScaleKind.EXPLAINS to (SummaryScaleValue.HIGH to "Объясняет выбор решений"),
                SummaryScaleKind.ATTITUDE to (SummaryScaleValue.HIGH to "Отвечает в чате даже вечером"),
                SummaryScaleKind.FAIRNESS to (SummaryScaleValue.HIGH to "Оценивает понимание, а не только код"),
                SummaryScaleKind.STRICTNESS to (SummaryScaleValue.MEDIUM to "Вопросы на защите по существу"),
                SummaryScaleKind.WORKLOAD to (SummaryScaleValue.HIGH to "Лабораторные каждую неделю")
            ),
            level = TeacherLevel.VERY_POSITIVE,
            confidence = SummaryConfidence.HIGH
        ),
        DemoPeople.DATABASES_TEACHER.isu to TeacherSummary(
            reviewCount = 2,
            description = "Понятно объясняет и подробно разбирает ошибки; задания близки к рабочим задачам.",
            pros = listOf("понятные объяснения", "задачи из практики"),
            cons = emptyList(),
            tags = listOf(SummaryTag.ATTENDANCE_OPTIONAL, SummaryTag.CLEAR_REQUIREMENTS),
            scales = scales(
                SummaryScaleKind.EXPLAINS to (SummaryScaleValue.HIGH to "Разбирает нормализацию и индексы"),
                SummaryScaleKind.ATTITUDE to (SummaryScaleValue.HIGH to "На лабораторных есть время спросить"),
                SummaryScaleKind.FAIRNESS to (SummaryScaleValue.NOT_ENOUGH_DATA to null),
                SummaryScaleKind.STRICTNESS to (SummaryScaleValue.LOW to "Посещение не отмечает"),
                SummaryScaleKind.WORKLOAD to (SummaryScaleValue.MEDIUM to "Одна лабораторная в неделю")
            ),
            level = TeacherLevel.POSITIVE,
            confidence = SummaryConfidence.MEDIUM
        )
    )
}
