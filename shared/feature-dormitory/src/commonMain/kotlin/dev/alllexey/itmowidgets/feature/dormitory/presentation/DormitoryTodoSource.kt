package dev.alllexey.itmowidgets.feature.dormitory.presentation

import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.today.PaymentTodos
import dev.alllexey.itmowidgets.core.today.TodayTodo
import dev.alllexey.itmowidgets.core.today.TodayTodoSource
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryRepository
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStage
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStatus
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * The dormitory's part of `Нужно сделать`, read from the stored answer only, so it shows offline and sends nothing:
 * `Выберите дату заселения` while `Место предоставлено`, with the time the app first saw it; one item per Документ
 * для заселения ITMO has not confirmed while the stage needs documents; then the Договоры найма's Долг and Платежи
 * due within a week ([PaymentTodos]). Each opens `Общежитие` over Сегодня.
 */
class DormitoryTodoSource(
    private val repository: DormitoryRepository,
    private val time: AcademicTimeProvider,
) : TodayTodoSource {

    override fun observe(): Flow<List<TodayTodo>> = repository.observe()
        .map { answer ->
            if (answer == null) return@map emptyList()
            val stage = answer.status?.let { todos(it, it.placeOfferedSeenAt ?: answer.fetchedAt) }.orEmpty()
            stage + PaymentTodos.of(answer.contracts, time.today(), FEATURE, AppRoutes.Dormitory)
        }
        .distinctUntilChanged()

    /** [seenAt]: when the app first saw `Место предоставлено`, else the stored answer's time. */
    private fun todos(status: DormitoryStatus, seenAt: Instant): List<TodayTodo> {
        val date = if (status.stage == DormitoryStage.PLACE_OFFERED) listOf(chooseDate(seenAt)) else emptyList()
        val documents = if (status.stage?.needsDocuments == true) {
            status.documents.filterNot { it.confirmed }.map { document ->
                todo(
                    id = "$DOCUMENT_ID${document.name}",
                    icon = AppIcon.ASSIGNMENT,
                    title = UiText.Res(DormitoryStrings.dormitory_todo_document_title, listOf(document.name)),
                    reason = UiText.Res(DormitoryStrings.dormitory_todo_document_reason, listOf(document.status)),
                )
            }
        } else {
            emptyList()
        }
        return date + documents
    }

    private fun chooseDate(seenAt: Instant) = todo(
        id = DATE_ID,
        icon = AppIcon.CALENDAR_MONTH,
        title = UiText.Res(DormitoryStrings.dormitory_todo_date_title),
        reason = UiText.Res(
            DormitoryStrings.dormitory_todo_date_reason,
            listOf(DormitoryPresenter.seenText(seenAt, time)),
        ),
    )

    private fun todo(id: String, icon: AppIcon, title: UiText, reason: UiText) = TodayTodo(
        id = id,
        icon = icon,
        title = title,
        reason = reason,
        action = UiText.Res(DormitoryStrings.dormitory_todo_open),
        target = EntryRoute(AppTab.HOME, overlay = AppRoutes.Dormitory),
    )

    private companion object {
        const val FEATURE = "dormitory"
        const val DATE_ID = "dormitory-date"
        const val DOCUMENT_ID = "dormitory-document-"
    }
}
