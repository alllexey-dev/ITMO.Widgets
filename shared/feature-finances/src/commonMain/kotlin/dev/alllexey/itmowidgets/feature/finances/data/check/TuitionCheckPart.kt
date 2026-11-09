package dev.alllexey.itmowidgets.feature.finances.data.check

import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.payments.check.PaymentsCheckPart
import dev.alllexey.itmowidgets.core.payments.check.PaymentsCheckState
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionFeature
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionRepository
import kotlinx.coroutines.flow.first

/**
 * The tuition's part of the check `Общежитие и оплата`: the Договоры об обучении bring their Платежи reminders and
 * keep the short cadence while something is left to pay. The repository writes the bell events of every refresh.
 */
class TuitionCheckPart(private val repository: TuitionRepository) : PaymentsCheckPart {

    override val feature: String = TuitionFeature.KEY

    override val destination: NotificationDestination = NotificationDestination.Tuition

    override suspend fun stored(): PaymentsCheckState? = repository.observe().first()?.let { answer ->
        PaymentsCheckState(fetchedAt = answer.fetchedAt, pending = false, contracts = answer.contracts)
    }

    override suspend fun refresh(): AppResult<Unit> = when (val result = repository.refresh()) {
        is AppResult.Success -> AppResult.Success(Unit)
        is AppResult.Failure -> result
    }
}
