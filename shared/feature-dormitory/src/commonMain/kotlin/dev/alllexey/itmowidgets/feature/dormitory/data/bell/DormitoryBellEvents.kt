package dev.alllexey.itmowidgets.feature.dormitory.data.bell

import dev.alllexey.itmowidgets.core.bell.BellEvent
import dev.alllexey.itmowidgets.core.bell.BellEventStore
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.payments.PaymentBellEvents
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryAnswer
import kotlin.time.Clock

/**
 * The dormitory's bell events, `noBackup/dormitory_bell.json`: what changed between two stored answers, whoever asked
 * (the screen or the check `Общежитие и оплата`), so the bell works with the notifications off. A queue place that
 * moved, a dormitory assigned (or another one) and a Платёж that became paid; the first answer is a baseline.
 */
class DormitoryBellEvents internal constructor(file: AtomicTextFile, clock: Clock, dispatchers: AppDispatchers) :
    BellEventStore(file, clock, dispatchers) {

    constructor(directories: AppDirectories, clock: Clock, dispatchers: AppDispatchers) :
        this(AtomicTextFile(directories.noBackup / FILE_NAME), clock, dispatchers)

    suspend fun record(previous: DormitoryAnswer?, current: DormitoryAnswer) {
        if (previous != null) add(between(previous, current))
    }

    private fun between(previous: DormitoryAnswer, current: DormitoryAnswer): List<BellEvent> {
        val at = current.fetchedAt.toEpochMilliseconds()
        val was = previous.status?.queuePlace
        val now = current.status?.queuePlace
        val queue = if (was != null && now != null && was != now) {
            BellEvent("$FEATURE:queue:$at", QUEUE_MOVED, at, listOf(was.toString(), now.toString()))
        } else {
            null
        }
        val dormitory = current.status?.dormitory?.takeIf { it.name != previous.status?.dormitory?.name }?.let {
            BellEvent("$FEATURE:assigned:$at", ASSIGNED, at, listOf(it.name))
        }
        return listOfNotNull(queue, dormitory) + PaymentBellEvents.paid(FEATURE, previous.contracts, current.contracts, current.fetchedAt)
    }

    companion object {
        const val FILE_NAME = "dormitory_bell.json"
        const val QUEUE_MOVED = "queue-moved"
        const val ASSIGNED = "dormitory-assigned"
        private const val FEATURE = "dormitory"
    }
}
