package dev.alllexey.itmowidgets.feature.me.moderation.data.push

import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.me.moderation.domain.ModerationNotice
import dev.alllexey.itmowidgets.feature.me.moderation.domain.ModerationNoticesRepository
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationNoticePushHandlerTest {
    private val notifier = RecordingNotifier()
    private val diagnostics = RecordingDiagnostics()
    private val notices = RecordingNotices()
    private var enabled = true
    private val services = object : CustomServicesRepository {
        override fun observeEnabled() = flowOf(enabled)
        override suspend fun isEnabled() = enabled
        override suspend fun setEnabled(enabled: Boolean) { this@ModerationNoticePushHandlerTest.enabled = enabled }
    }
    private val handler = ModerationNoticePushHandler(services, notices, diagnostics)

    @Test fun `Backend's rejected link names the link, the subject and the reason and opens the links`() = runTest {
        handler.handle(vendoredPayload(), notifier)

        val notice = notices.received.single()
        val title = UiText.Res(CoreStrings.notification_moderation_link_rejected)
        assertEquals(
            AppNotification(
                channel = AppNotificationChannels.MODERATION,
                id = notice.id.hashCode(),
                title = title,
                text = UiText.Res(
                    CoreStrings.notification_moderation_link_body_note,
                    listOf("Ведомость", "Математический анализ", "Ссылка не про предмет"),
                ),
                destination = NotificationDestination.SubjectLinks(SubjectLinksArgs(501, "Математический анализ", "2026-1")),
                publicTitle = title,
            ),
            notifier.shown.single(),
        )
    }

    @Test fun `a review opens the teacher's profile`() = runTest {
        handler.handle(
            Json.parseToJsonElement(
                """{"id":"00000000-0000-4000-8000-000000000902","outcome":"APPROVED","decidedAt":"2026-10-05T07:00:00Z",""" +
                    """"review":{"id":"00000000-0000-4000-8000-000000000201","teacherIsu":200001,"teacherName":"Преподаватель"}}""",
            ),
            notifier,
        )

        assertEquals(NotificationDestination.UserProfile(200001), notifier.shown.single().destination)
        assertEquals(UiText.Res(CoreStrings.notification_moderation_review_approved), notifier.shown.single().title)
    }

    @Test fun `nothing without the opt-in, an unknown outcome is skipped and a malformed push is only recorded`() = runTest {
        enabled = false
        handler.handle(vendoredPayload(), notifier)
        assertTrue(notifier.shown.isEmpty())

        enabled = true
        handler.handle(
            Json.parseToJsonElement(
                """{"id":"00000000-0000-4000-8000-000000000903","outcome":"ESCALATED","decidedAt":"2026-10-05T07:00:00Z",""" +
                    """"review":{"id":"00000000-0000-4000-8000-000000000201","teacherIsu":200001}}""",
            ),
            notifier,
        )
        handler.handle(Json.parseToJsonElement("""{"outcome":"APPROVED"}"""), notifier)

        assertTrue(notifier.shown.isEmpty())
        assertTrue(notices.received.isEmpty())
        assertEquals(listOf("WARNING:ModerationNoticePush:Invalid moderation notice push"), diagnostics.messages)
    }

    private fun vendoredPayload(): JsonElement {
        val fixture = File("../shared/backend-client/src/commonTest/resources/contract/fcm/MODERATION_NOTICE_PAYLOAD.json")
        return Json.parseToJsonElement(fixture.readText()).jsonObject.getValue("data").jsonObject.getValue("payload")
    }

    private class RecordingNotices : ModerationNoticesRepository {
        val received = mutableListOf<ModerationNotice>()
        override val notices: StateFlow<List<ModerationNotice>?> = MutableStateFlow(null)
        override suspend fun refresh(): AppResult<List<ModerationNotice>> = AppResult.Success(emptyList())
        override fun received(notice: ModerationNotice) { received += notice }
    }

    private class RecordingNotifier : AppNotifier {
        val shown = mutableListOf<AppNotification>()
        override fun show(notification: AppNotification) { shown += notification }
        override fun cancel(channel: String, id: Int) = Unit
        override fun clear() = Unit
    }
}
