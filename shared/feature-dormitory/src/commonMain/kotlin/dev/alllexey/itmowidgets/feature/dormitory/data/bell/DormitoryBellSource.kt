package dev.alllexey.itmowidgets.feature.dormitory.data.bell

import dev.alllexey.itmowidgets.core.bell.BellEvent
import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.payments.PaymentBellEvents
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The dormitory in the bell: `Место в очереди на общежитие` with `было` and `стало`, `Назначено общежитие` with its
 * name and `Платёж зачислен`; each opens «Общежитие» over Сегодня.
 */
class DormitoryBellSource(private val events: DormitoryBellEvents) : BellSource {

    override fun observe(): Flow<List<BellItem>> = events.observe().map { list -> list.mapNotNull(::item) }

    private fun item(event: BellEvent): BellItem? = when (event.type) {
        DormitoryBellEvents.QUEUE_MOVED -> event.values.takeIf { it.size == 2 }?.let { (was, now) ->
            BellItem(
                id = event.id,
                kind = BellKind.DORMITORY,
                at = event.seenAt,
                title = UiText.Res(DormitoryStrings.dormitory_bell_queue),
                was = UiText.Dynamic(was),
                now = UiText.Dynamic(now),
                target = TARGET,
            )
        }
        DormitoryBellEvents.ASSIGNED -> event.values.singleOrNull()?.let { name ->
            BellItem(
                id = event.id,
                kind = BellKind.DORMITORY,
                at = event.seenAt,
                title = UiText.Res(DormitoryStrings.dormitory_bell_assigned),
                text = UiText.Dynamic(name),
                target = TARGET,
            )
        }
        else -> PaymentBellEvents.item(event, TARGET)
    }

    private companion object {
        val TARGET = EntryRoute(AppTab.HOME, overlay = AppRoutes.Dormitory)
    }
}
