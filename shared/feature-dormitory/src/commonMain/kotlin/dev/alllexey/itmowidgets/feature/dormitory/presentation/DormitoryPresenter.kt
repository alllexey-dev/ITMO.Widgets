package dev.alllexey.itmowidgets.feature.dormitory.presentation

import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.payments.Contract
import dev.alllexey.itmowidgets.core.payments.ContractsByYear
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.PaymentTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryAnswer
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStage
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStatus
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryPlurals
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Builds every text of the `Общежитие` screen, so Compose and SwiftUI only lay it out. */
internal object DormitoryPresenter {

    /**
     * [answer] is the stored one (null before the first), [failure] the last refresh's error: with an answer it only
     * adds the age, without one it is the screen. [today] (Moscow) classifies the Платежи; [pastYearsExpanded] unfolds
     * `Прошлые годы`.
     */
    fun present(
        answer: DormitoryAnswer?,
        failure: AppError?,
        refreshing: Boolean,
        now: Instant,
        today: LocalDate,
        pastYearsExpanded: Boolean = false,
    ): DormitoryUiState {
        if (answer == null) {
            val content = failure?.let { DormitoryContent.Failed(it.toUiText()) } ?: DormitoryContent.Loading
            return DormitoryUiState(content, refreshing)
        }
        val status = answer.status
        return DormitoryUiState(
            content = if (!answer.hasAnything) {
                DormitoryContent.NoApplication
            } else {
                shown(status).copy(contracts = contracts(answer.contracts, today, pastYearsExpanded))
            },
            refreshing = refreshing,
            stale = failure?.let { updatedText(answer.fetchedAt, now) },
        )
    }

    private fun contracts(contracts: List<Contract>, today: LocalDate, pastYearsExpanded: Boolean): DormitoryContracts? {
        if (contracts.isEmpty()) return null
        val byYear = ContractsByYear.of(contracts, today)
        return DormitoryContracts(
            current = byYear.current.map { card(it, today) },
            pastYears = byYear.past.takeIf { it.isNotEmpty() }?.let { past ->
                PastYears(
                    expanded = pastYearsExpanded,
                    years = past.map { year -> ContractYearGroup(PaymentTexts.academicYear(year.year), year.contracts.map { card(it, today) }) },
                )
            },
        )
    }

    private fun card(contract: Contract, today: LocalDate) = ContractCard(
        id = contract.id,
        title = PaymentTexts.kind(contract.kind),
        subtitle = PaymentTexts.subtitle(contract),
        balance = PaymentTexts.balance(contract, today),
        pay = contract.hasSomethingToPay(),
        payments = contract.payments.map { payment ->
            PaymentRow(
                period = payment.period,
                amount = PaymentTexts.money(payment.amount),
                due = PaymentTexts.due(payment, today),
                paid = PaymentTexts.paidPart(payment),
                pill = PaymentTexts.pill(payment, today),
            )
        },
    )

    /** `Обновлено 5 мин назад`, `Обновлено 3 ч назад`, `Обновлено 2 дня назад`. */
    fun updatedText(at: Instant, now: Instant): UiText {
        val elapsed = now - at
        return when {
            elapsed < 1.minutes -> UiText.Res(DormitoryStrings.dormitory_updated_now)
            elapsed < 1.hours -> UiText.Res(DormitoryStrings.dormitory_updated_minutes, listOf(elapsed.inWholeMinutes.toInt()))
            elapsed < 1.days -> UiText.Res(DormitoryStrings.dormitory_updated_hours, listOf(elapsed.inWholeHours.toInt()))
            else -> elapsed.inWholeDays.toInt().let { UiText.Plural(DormitoryPlurals.dormitory_updated_days, it, listOf(it)) }
        }
    }

    private fun shown(status: DormitoryStatus?) = DormitoryContent.Shown(
        stage = status?.let(::stageCard),
        dormitory = status?.dormitory?.let { place ->
            DormitoryPlace(place.name, place.address, MapDestination(place.name, place.address), place.instructions)
        },
        documents = if (status != null && status.stage?.needsDocuments == true) {
            DormitoryDocuments(
                rows = status.documents.map { DormitoryDocumentRow(it.name, it.status, it.confirmed) },
                upload = status.documents.any { !it.confirmed },
            )
        } else {
            null
        },
    )

    private fun stageCard(status: DormitoryStatus): DormitoryStageCard = when (status.stage) {
        DormitoryStage.QUEUE -> DormitoryStageCard(
            title = UiText.Res(DormitoryStrings.dormitory_stage_queue),
            text = UiText.Res(DormitoryStrings.dormitory_queue_text),
            tone = DormitoryTone.NEUTRAL,
            queuePlace = status.queuePlace?.let { UiText.Res(DormitoryStrings.dormitory_queue_place, listOf(it)) },
        )
        DormitoryStage.PLACE_OFFERED -> DormitoryStageCard(
            title = UiText.Res(DormitoryStrings.dormitory_stage_place_offered),
            text = UiText.Res(DormitoryStrings.dormitory_place_offered_text),
            tone = DormitoryTone.ATTENTION,
            chooseDate = true,
        )
        DormitoryStage.DATE_CHOSEN -> DormitoryStageCard(
            title = UiText.Res(DormitoryStrings.dormitory_stage_date_chosen),
            text = UiText.Res(DormitoryStrings.dormitory_date_chosen_text),
            tone = DormitoryTone.NEUTRAL,
        )
        DormitoryStage.SETTLED -> DormitoryStageCard(
            title = UiText.Res(DormitoryStrings.dormitory_stage_settled),
            text = UiText.Res(DormitoryStrings.dormitory_settled_text),
            tone = DormitoryTone.DONE,
        )
        null -> DormitoryStageCard(
            title = UiText.Res(DormitoryStrings.dormitory_stage_unknown),
            text = UiText.Res(DormitoryStrings.dormitory_unknown_text),
            tone = DormitoryTone.NEUTRAL,
        )
    }

    /** The stage's name for Профиль's row. */
    fun stageName(stage: DormitoryStage?): UiText = when (stage) {
        DormitoryStage.QUEUE -> UiText.Res(DormitoryStrings.dormitory_stage_queue)
        DormitoryStage.PLACE_OFFERED -> UiText.Res(DormitoryStrings.dormitory_stage_place_offered)
        DormitoryStage.DATE_CHOSEN -> UiText.Res(DormitoryStrings.dormitory_stage_date_chosen)
        DormitoryStage.SETTLED -> UiText.Res(DormitoryStrings.dormitory_stage_settled)
        null -> UiText.Res(DormitoryStrings.dormitory_stage_unknown)
    }
}
