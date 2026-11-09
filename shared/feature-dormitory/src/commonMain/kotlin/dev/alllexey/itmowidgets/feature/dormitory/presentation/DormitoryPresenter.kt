package dev.alllexey.itmowidgets.feature.dormitory.presentation

import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.ContractList
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.text.updatedAgo
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryAnswer
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStage
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStatus
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import kotlin.time.Instant
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

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
        time: AcademicTimeProvider,
        pastYearsExpanded: Boolean = false,
    ): DormitoryUiState {
        if (answer == null) {
            val content = failure?.let { DormitoryContent.Failed(it.toUiText()) } ?: DormitoryContent.Loading
            return DormitoryUiState(content, refreshing)
        }
        return DormitoryUiState(
            content = if (answer.hasAnything) {
                shown(answer.status, answer.fetchedAt, time, ContractList.of(answer.contracts, time.today(), pastYearsExpanded))
            } else {
                DormitoryContent.NoApplication
            },
            refreshing = refreshing,
            stale = failure?.let { updatedAgo(answer.updatedAt, time.now()) },
        )
    }

    private fun shown(
        status: DormitoryStatus?,
        fetchedAt: Instant,
        time: AcademicTimeProvider,
        contracts: ContractList?,
    ) = DormitoryContent.Shown(
        stage = status?.let { stageCard(it, fetchedAt, time) },
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
        contracts = contracts,
    )

    private fun stageCard(status: DormitoryStatus, fetchedAt: Instant, time: AcademicTimeProvider): DormitoryStageCard {
        val title = stageName(status.stage)
        return when (status.stage) {
            DormitoryStage.QUEUE -> DormitoryStageCard(
                title = title,
                text = UiText.Res(DormitoryStrings.dormitory_queue_text),
                tone = DormitoryTone.NEUTRAL,
                queuePlace = status.queuePlace?.let { UiText.Res(DormitoryStrings.dormitory_queue_place, listOf(it)) },
            )
            DormitoryStage.PLACE_OFFERED -> DormitoryStageCard(
                title = title,
                text = UiText.Res(DormitoryStrings.dormitory_place_offered_text),
                tone = DormitoryTone.ATTENTION,
                chooseDate = true,
                seen = UiText.Res(
                    DormitoryStrings.dormitory_place_offered_seen,
                    listOf(seenText(status.placeOfferedSeenAt ?: fetchedAt, time)),
                ),
            )
            DormitoryStage.DATE_CHOSEN -> DormitoryStageCard(
                title = title,
                text = UiText.Res(DormitoryStrings.dormitory_date_chosen_text),
                tone = DormitoryTone.NEUTRAL,
            )
            DormitoryStage.SETTLED -> DormitoryStageCard(
                title = title,
                text = UiText.Res(DormitoryStrings.dormitory_settled_text),
                tone = DormitoryTone.DONE,
            )
            null -> DormitoryStageCard(
                title = title,
                text = UiText.Res(DormitoryStrings.dormitory_unknown_text),
                tone = DormitoryTone.NEUTRAL,
            )
        }
    }

    /** When `Место предоставлено` was first seen: `09:40` today, `7 нояб., 09:40` before. */
    fun seenText(at: Instant, time: AcademicTimeProvider): String {
        val local = at.toLocalDateTime(time.timeZone)
        return if (local.date == time.today()) local.time.format(DateTexts.TIME) else local.format(DateTexts.DAY_SHORT_MONTH_TIME)
    }

    /** The stage's name: the stage card's title and the subtitle of Профиль's row. */
    fun stageName(stage: DormitoryStage?): UiText = when (stage) {
        DormitoryStage.QUEUE -> UiText.Res(DormitoryStrings.dormitory_stage_queue)
        DormitoryStage.PLACE_OFFERED -> UiText.Res(DormitoryStrings.dormitory_stage_place_offered)
        DormitoryStage.DATE_CHOSEN -> UiText.Res(DormitoryStrings.dormitory_stage_date_chosen)
        DormitoryStage.SETTLED -> UiText.Res(DormitoryStrings.dormitory_stage_settled)
        null -> UiText.Res(DormitoryStrings.dormitory_stage_unknown)
    }
}
