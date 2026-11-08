package dev.alllexey.itmowidgets.feature.dormitory.data

import dev.alllexey.itmowidgets.core.payments.AcademicYear
import dev.alllexey.itmowidgets.core.payments.Contract
import dev.alllexey.itmowidgets.core.payments.ContractKind
import dev.alllexey.itmowidgets.core.payments.Money
import dev.alllexey.itmowidgets.core.payments.Payment
import dev.alllexey.itmowidgets.core.text.SafeHtml
import dev.alllexey.itmowidgets.feature.dormitory.domain.AssignedDormitory
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryAnswer
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStage
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStatus
import dev.alllexey.itmowidgets.feature.dormitory.domain.SettlementDocument
import dev.alllexey.itmoapi.myitmo.dormitory.DormitoryContract as ApiContract
import dev.alllexey.itmoapi.myitmo.dormitory.DormitoryDocument as ApiDocument
import dev.alllexey.itmoapi.myitmo.dormitory.DormitoryStatus as ApiStatus
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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

/** A Договор найма of the academic year starting in [year]; ITMO's dates are read as calendar dates in [zone]. */
internal fun ApiContract.toStored(year: Int, zone: TimeZone) = StoredContract(
    id = contractId,
    number = number,
    start = dateStart.toLocalDateTime(zone).date,
    end = dateEnd.toLocalDateTime(zone).date,
    academicYear = year,
    active = active,
    balance = Money.ofRubles(balance).kopecks,
    payments = payments.map {
        StoredPayment(Money.ofRubles(it.sum).kopecks, Money.ofRubles(it.paid).kopecks, it.payUntil.toLocalDateTime(zone).date, it.period)
    },
)

private fun StoredContract.toContract() = Contract(
    id = id,
    kind = ContractKind.RENT,
    number = number,
    start = start,
    end = end,
    academicYear = AcademicYear(academicYear),
    active = active,
    balance = Money(balance),
    payments = payments.map { Payment(Money(it.amount), Money(it.paid), it.dueDate, it.period) },
)

internal fun StoredDormitory.toAnswer() = DormitoryAnswer(
    status = status?.let { stored ->
        DormitoryStatus(
            stage = stageOf(stored.statusId),
            queuePlace = stored.queuePlace,
            dormitory = stored.dormitory?.let { AssignedDormitory(it.name, it.address, SafeHtml.parse(it.instructionsHtml)) },
            documents = stored.documents.map { SettlementDocument(it.name, it.status, it.confirmed) },
        )
    },
    fetchedAt = Instant.fromEpochMilliseconds(fetchedAt),
    contracts = contracts.map { it.toContract() },
)
