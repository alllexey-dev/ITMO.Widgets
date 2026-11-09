package dev.alllexey.itmowidgets.feature.finances.presentation

import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.today.PaymentTodos
import dev.alllexey.itmowidgets.core.today.TodayTodo
import dev.alllexey.itmowidgets.core.today.TodayTodoSource
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Tuition's part of `Нужно сделать`: the Договоры об обучении's Долг and Платежи due within a week ([PaymentTodos]),
 * read from the stored answer only, so they show offline and Сегодня sends nothing. Each opens `Оплата обучения` over
 * Сегодня.
 */
class TuitionTodoSource(
    private val repository: TuitionRepository,
    private val time: AcademicTimeProvider,
) : TodayTodoSource {

    override fun observe(): Flow<List<TodayTodo>> = repository.observe()
        .map { answer -> answer?.let { PaymentTodos.of(it.contracts, time.today(), FEATURE, AppRoutes.Tuition) }.orEmpty() }
        .distinctUntilChanged()

    private companion object {
        const val FEATURE = "tuition"
    }
}
