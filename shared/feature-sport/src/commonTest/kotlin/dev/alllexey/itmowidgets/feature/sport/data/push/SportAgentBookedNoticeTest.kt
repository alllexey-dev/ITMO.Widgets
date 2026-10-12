package dev.alllexey.itmowidgets.feature.sport.data.push

import dev.alllexey.itmowidgets.client.push.SportAgentBookedPayload
import dev.alllexey.itmowidgets.client.sport.model.SportLessonDto
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class SportAgentBookedNoticeTest {
    private val lesson = SportLessonDto(
        id = 9001, sectionId = 41, sectionName = " Плавание ", sectionLevel = 1, level = 1, typeId = 2,
        buildingId = 13, roomName = "Бассейн", start = Instant.parse("2026-10-07T07:00:00Z"),
        end = Instant.parse("2026-10-07T08:30:00Z"), timeSlotId = 3, teacherIsu = 200001, teacherFio = "Тренер",
    )

    @Test
    fun anAgentsBookingIsASportSuccessThatNamesTheAgentInMoscowTime() {
        val notification = SportAgentBookedPayload(listOf(lesson), " nas ").toAppNotification()!!

        assertEquals(AppNotificationChannels.SPORT, notification.channel)
        assertEquals(9001L.hashCode(), notification.id)
        assertEquals(UiText.Res(CoreStrings.notification_sport_success), notification.title)
        assertEquals(
            UiText.Res(CoreStrings.notification_sport_agent_booked, listOf("Плавание", "7 окт., 10:00", "nas")),
            notification.text,
        )
        assertEquals(NotificationDestination.Sport, notification.destination)
    }

    @Test
    fun aPushWithoutALessonShowsNothing() {
        assertNull(SportAgentBookedPayload(emptyList(), "nas").toAppNotification())
    }
}
