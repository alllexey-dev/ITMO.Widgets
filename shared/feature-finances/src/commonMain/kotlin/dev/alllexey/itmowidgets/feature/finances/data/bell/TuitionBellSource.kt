package dev.alllexey.itmowidgets.feature.finances.data.bell

import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.payments.PaymentBellEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The tuition in the bell: `Платёж зачислен`, opening `Оплата обучения` over Сегодня. */
class TuitionBellSource(private val events: TuitionBellEvents) : BellSource {

    override fun observe(): Flow<List<BellItem>> = events.observe().map { list -> list.mapNotNull { PaymentBellEvents.item(it, TARGET) } }

    private companion object {
        val TARGET = EntryRoute(AppTab.HOME, overlay = AppRoutes.Tuition)
    }
}
