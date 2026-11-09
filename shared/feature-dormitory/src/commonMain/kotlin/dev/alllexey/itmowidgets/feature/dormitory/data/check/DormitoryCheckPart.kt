package dev.alllexey.itmowidgets.feature.dormitory.data.check

import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.payments.check.PaymentsCheckPart
import dev.alllexey.itmowidgets.core.payments.check.PaymentsCheckState
import dev.alllexey.itmowidgets.core.payments.check.PaymentsNotice
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryAnswer
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryFeature
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryRepository
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStage
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import kotlinx.coroutines.flow.first

/**
 * The dormitory's part of the check `Общежитие и оплата`: any Этап общежития keeps the short cadence, the Договоры
 * найма bring their Платежи reminders, and `Место предоставлено` is one notice per time the app first saw it, so a
 * stage that leaves and comes back is told again. The repository writes the bell events of every refresh.
 */
class DormitoryCheckPart(private val repository: DormitoryRepository) : PaymentsCheckPart {

    override val feature: String = DormitoryFeature.KEY

    override val destination: NotificationDestination = NotificationDestination.Dormitory

    override suspend fun stored(): PaymentsCheckState? = repository.observe().first()?.let { answer ->
        PaymentsCheckState(
            // The older part: Договоры kept from an earlier answer are due as old as they are.
            fetchedAt = answer.updatedAt,
            // Any Этап общежития, `Заселён` included, keeps the short cadence.
            pending = answer.status != null,
            contracts = answer.contracts,
            notices = listOfNotNull(placeOffered(answer)),
        )
    }

    override suspend fun refresh(): AppResult<Unit> = when (val result = repository.refresh()) {
        is AppResult.Success -> AppResult.Success(Unit)
        is AppResult.Failure -> result
    }

    private fun placeOffered(answer: DormitoryAnswer): PaymentsNotice? {
        val status = answer.status?.takeIf { it.stage == DormitoryStage.PLACE_OFFERED } ?: return null
        val seenAt = status.placeOfferedSeenAt ?: return null
        val title = UiText.Res(DormitoryStrings.dormitory_notification_offered_title)
        return PaymentsNotice(
            key = "${DormitoryFeature.KEY}-place-offered-${seenAt.toEpochMilliseconds()}",
            slot = "${DormitoryFeature.KEY}-place-offered",
            title = title,
            text = UiText.Res(DormitoryStrings.dormitory_place_offered_text),
            publicTitle = title,
            destination = destination,
        )
    }
}
