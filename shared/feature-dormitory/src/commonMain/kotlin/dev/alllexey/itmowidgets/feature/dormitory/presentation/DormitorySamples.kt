package dev.alllexey.itmowidgets.feature.dormitory.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.SafeHtml
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.dormitory.domain.AssignedDormitory
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryAnswer
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStage
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStatus
import dev.alllexey.itmowidgets.feature.dormitory.domain.SettlementDocument
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Synthetic `Общежитие` screens for the Android previews and the iOS snapshot tests, one per Этап общежития and
 * state, built by the same presenter as the app.
 */
object DormitorySamples {
    private val now = Instant.parse("2026-11-08T09:00:00Z")

    /** The academic time standing at [now], 12:00 in Moscow. */
    private val time = object : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")

        override fun today(): LocalDate = now.toLocalDateTime(timeZone).date

        override fun now(): Instant = now
    }

    private val dormitory = AssignedDormitory(
        name = "Общежитие №1",
        address = "Санкт-Петербург, Примерный пер., 1",
        instructions = SafeHtml.parse(
            "<p>Заселение — в будни с <b>10:00 до 17:00</b> у коменданта.</p>" +
                "<p>С собой:</p><ul><li>паспорт;</li><li>договор найма;</li><li>фото 3×4.</li></ul>",
        ),
    )

    private val documents = listOf(
        SettlementDocument("Флюорография", "Подтверждено", confirmed = true),
        SettlementDocument("Справка о прививках", "Отсутствует", confirmed = false),
    )

    private fun shown(status: DormitoryStatus, failure: AppError? = null, age: Int = 0) = DormitoryPresenter.present(
        DormitoryAnswer(status, now - age.hours), failure, refreshing = false, time = time,
    )

    val queue: DormitoryUiState = shown(DormitoryStatus(DormitoryStage.QUEUE, 42, null, emptyList()))

    /** Seen by the app at 09:00, three hours ago. */
    val placeOffered: DormitoryUiState = shown(
        DormitoryStatus(DormitoryStage.PLACE_OFFERED, null, dormitory, documents, placeOfferedSeenAt = now - 3.hours),
    )

    val dateChosen: DormitoryUiState = shown(
        DormitoryStatus(DormitoryStage.DATE_CHOSEN, null, dormitory, documents.map { it.copy(status = "Подтверждено", confirmed = true) }),
    )

    val settled: DormitoryUiState = shown(DormitoryStatus(DormitoryStage.SETTLED, null, dormitory, emptyList()))

    /** Offline: the stored answer from 2 hours ago. */
    val offline: DormitoryUiState = shown(DormitoryStatus(DormitoryStage.SETTLED, null, dormitory, emptyList()), AppError.Network, age = 2)

    val none: DormitoryUiState = DormitoryPresenter.present(DormitoryAnswer(null, now), null, refreshing = false, time = time)

    val failed: DormitoryUiState = DormitoryPresenter.present(null, AppError.Network, refreshing = false, time = time)

    val loading: DormitoryUiState = DormitoryUiState()
}
