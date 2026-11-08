package dev.alllexey.itmowidgets.feature.dormitory.data

import dev.alllexey.itmowidgets.core.text.SafeHtml
import dev.alllexey.itmowidgets.feature.dormitory.domain.AssignedDormitory
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryAnswer
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStage
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStatus
import dev.alllexey.itmowidgets.feature.dormitory.domain.SettlementDocument
import dev.alllexey.itmoapi.myitmo.dormitory.DormitoryDocument as ApiDocument
import dev.alllexey.itmoapi.myitmo.dormitory.DormitoryStatus as ApiStatus
import kotlin.time.Instant

/** my.itmo's `statusId`: 1 in the queue, 2 a place offered, 3 a date picked, 4 settled in; others are unknown. */
internal fun stageOf(statusId: Int): DormitoryStage? = when (statusId) {
    1 -> DormitoryStage.QUEUE
    2 -> DormitoryStage.PLACE_OFFERED
    3 -> DormitoryStage.DATE_CHOSEN
    4 -> DormitoryStage.SETTLED
    else -> null
}

/** Observed document statuses: 1 `Отсутствует`, 2 `Подтверждено`. */
private const val DOCUMENT_CONFIRMED = 2

internal fun ApiStatus.toStored(documents: List<ApiDocument>) = StoredStatus(
    statusId = statusId,
    queuePlace = queuePlace,
    dormitory = assignedDormitory?.let { StoredDormitoryPlace(it.name, it.address, it.settlementProcedureText) },
    documents = documents.map { StoredDocument(it.documentTypeName, it.statusName, it.status == DOCUMENT_CONFIRMED) },
)

/**
 * [this] new status with the time `Место предоставлено` was first seen: kept from the [previous] answer while the
 * stage stays, [fetchedAt] when it first appears, dropped once the stage leaves.
 */
internal fun StoredStatus.seenSince(previous: DormitoryStatus?, fetchedAt: Instant): StoredStatus {
    if (stageOf(statusId) != DormitoryStage.PLACE_OFFERED) return copy(placeOfferedSeenAt = null)
    val kept = previous?.takeIf { it.stage == DormitoryStage.PLACE_OFFERED }?.placeOfferedSeenAt
    return copy(placeOfferedSeenAt = (kept ?: fetchedAt).toEpochMilliseconds())
}

internal fun StoredDormitory.toAnswer() = DormitoryAnswer(
    status = status?.let { stored ->
        DormitoryStatus(
            stage = stageOf(stored.statusId),
            queuePlace = stored.queuePlace,
            dormitory = stored.dormitory?.let { AssignedDormitory(it.name, it.address, SafeHtml.parse(it.instructionsHtml)) },
            documents = stored.documents.map { SettlementDocument(it.name, it.status, it.confirmed) },
            placeOfferedSeenAt = stored.placeOfferedSeenAt?.let(Instant::fromEpochMilliseconds),
        )
    },
    fetchedAt = Instant.fromEpochMilliseconds(fetchedAt),
)
