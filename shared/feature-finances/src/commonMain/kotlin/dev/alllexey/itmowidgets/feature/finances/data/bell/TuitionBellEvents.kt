package dev.alllexey.itmowidgets.feature.finances.data.bell

import dev.alllexey.itmowidgets.core.bell.BellEventStore
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.payments.PaymentBellEvents
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionAnswer
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionFeature
import kotlin.time.Clock

/**
 * The tuition's bell events, `noBackup/tuition_bell.json`: a Платёж that became paid between two stored answers,
 * whoever asked (the screen or the check `Общежитие и оплата`); the first answer is a baseline.
 */
class TuitionBellEvents internal constructor(file: AtomicTextFile, clock: Clock, dispatchers: AppDispatchers) :
    BellEventStore(file, clock, dispatchers) {

    constructor(directories: AppDirectories, clock: Clock, dispatchers: AppDispatchers) :
        this(AtomicTextFile(directories.noBackup / FILE_NAME), clock, dispatchers)

    suspend fun record(previous: TuitionAnswer?, current: TuitionAnswer) {
        if (previous != null) add(PaymentBellEvents.paid(TuitionFeature.KEY, previous.contracts, current.contracts, current.fetchedAt))
    }

    companion object {
        const val FILE_NAME = "tuition_bell.json"
    }
}
