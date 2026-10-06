package dev.alllexey.itmowidgets.feature.sport.data.push

import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking.QueueKind
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.sport.data.Core2Harness
import dev.alllexey.itmowidgets.feature.sport.data.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportActionRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.notification_sport_failure
import dev.alllexey.itmowidgets.shared.core.notification_sport_lesson
import dev.alllexey.itmowidgets.shared.core.notification_sport_success
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.respondJson
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The shared booking decision of the free and auto queue pushes, with the notification each report becomes. */
class SportSignPushBookerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    @Test fun `both payload types book then satisfy notify and refresh every projection`() = runTest {
        for (auto in listOf(false, true)) {
            val fixture = Fixture(auto)
            fixture.run()
            assertEquals(listOf("/api/sport/${if (auto) "auto" else "free"}-sign/lesson/42/mark-satisfied"), fixture.queue)
            assertEquals(1, fixture.notifications.size)
            val notification = fixture.notifications.single()
            assertEquals(UiText.Res(Res.string.notification_sport_success), notification.title)
            assertEquals(NotificationDestination.Sport, notification.destination)
            assertEquals(AppNotificationChannels.SPORT, notification.channel)
            assertEquals(UiText.Res(Res.string.notification_sport_lesson, listOf("Секция", "21 сент., 12:00")), notification.text)
            assertEquals(42L.hashCode(), notification.id)
            assertEquals(1, fixture.bookingsRefresh)
            assertEquals(1, fixture.pendingRefresh)
            assertEquals(1, fixture.widgetRefresh)
        }
    }

    @Test fun `legacy capacity signature leaves the queue active without notification`() = runTest {
        val fixture = Fixture(false)
        fixture.response = errorJson(NO_CAPACITY_MESSAGE + " synthetic-context-more-than-20")
        fixture.run()
        assertTrue(fixture.queue.isEmpty())
        assertTrue(fixture.notifications.isEmpty())
    }

    @Test fun `other MyITMO rejections cancel only the matching kind and notify failure`() = runTest {
        for (auto in listOf(false, true)) {
            val fixture = Fixture(auto)
            fixture.response = errorJson("Synthetic booking rule rejection")
            fixture.run()
            assertEquals(listOf("/api/sport/${if (auto) "auto" else "free"}-sign/lesson/42/cancel"), fixture.queue)
            assertEquals(UiText.Res(Res.string.notification_sport_failure), fixture.notifications.single().title)
        }
        // Preserve the legacy signature's context-length condition, not a generic substring test.
        val shortContext = Fixture(false)
        shortContext.response = errorJson(NO_CAPACITY_MESSAGE + "x".repeat(20))
        shortContext.run()
        assertTrue(shortContext.queue.single().endsWith("/cancel"))
    }

    @Test fun `network and malformed responses never cancel or notify`() = runTest {
        val fixture = Fixture(false)
        fixture.networkFailure = true
        fixture.run()
        assertTrue(fixture.queue.isEmpty())
        assertTrue(fixture.notifications.isEmpty())
        fixture.networkFailure = false
        fixture.response = """{"error_code":0,"result":null}"""
        fixture.run()
        assertTrue(fixture.queue.isEmpty())
        assertTrue(fixture.notifications.isEmpty())
    }

    @Test fun `lessons and notification failures are independent and duplicate expired lessons are skipped`() = runTest {
        val fixture = Fixture(false)
        fixture.failFirst = true
        fixture.failNotifier = true
        fixture.run(lesson(41), lesson(42), lesson(42), lesson(43, expired = true))
        assertEquals(listOf("/api/sport/free-sign/lesson/42/mark-satisfied"), fixture.queue)
        assertEquals(2, fixture.signCalls)
        assertEquals(1, fixture.pendingRefresh)
    }

    @Test fun `a malformed lesson is skipped and the next one is still booked`() = runTest {
        val fixture = Fixture(true)
        fixture.run("""{"id":41,"sectionName":"Секция"}""", "\"not a lesson\"", lesson(42))
        assertEquals(listOf("/api/sport/auto-sign/lesson/42/mark-satisfied"), fixture.queue)
        assertEquals(1, fixture.signCalls)
        assertEquals(listOf("WARNING:SportSignPush:Malformed sport push lesson skipped"), fixture.diagnostics.messages.take(2).distinct())
    }

    @Test fun `an envelope on a non-2xx status is retried later, never cancelled`() = runTest {
        val fixture = Fixture(false)
        fixture.status = 400
        fixture.response = errorJson("Synthetic booking rule rejection")
        fixture.run()
        assertTrue(fixture.queue.isEmpty())
        assertTrue(fixture.notifications.isEmpty())
    }

    @Test fun `disabled community services do not book or refresh`() = runTest {
        val fixture = Fixture(false)
        fixture.run(enabled = false)
        assertEquals(0, fixture.signCalls)
        assertEquals(0, fixture.bookingsRefresh)
    }

    @Test fun `the demo session neither books nor reaches Backend even with the opt-in`() = runTest {
        val fixture = Fixture(true)
        fixture.demo.active.value = true
        fixture.run()
        assertEquals(0, fixture.signCalls)
        assertTrue(fixture.queue.isEmpty())
        assertEquals(0, fixture.bookingsRefresh)
    }

    private inner class Fixture(auto: Boolean) {
        val demo = FakeDemoMode()
        private val gate = FakeBackendGate(optedIn = false, demo = demo)
        private val queueKind = if (auto) QueueKind.AUTO else QueueKind.FREE
        val queue = mutableListOf<String>()
        val notifications = mutableListOf<AppNotification>()
        val diagnostics = RecordingDiagnostics()
        var response = """{"error_code":0,"result":[42]}"""
        var status = 200
        var networkFailure = false
        var failFirst = false
        var failNotifier = false
        var signCalls = 0
        var bookingsRefresh = 0
        var pendingRefresh = 0
        var widgetRefresh = 0
        private val clients = Core2Harness(Core2Harness.session()) { request ->
            if (request.url.host == "my.itmo.ru") {
                signCalls++
                if (networkFailure || (failFirst && signCalls == 1)) throw IOException("Synthetic network failure")
                respondJson(response, io.ktor.http.HttpStatusCode.fromValue(status))
            } else {
                queue += request.url.encodedPath
                respondJson("""{"success":true,"data":"OK","error":null}""")
            }
        }
        private val api = clients.client.sport
        private val booker = SportSignPushBooker(
            SportActionRepositoryImpl(gate, clients.myItmo, api, demo, dispatchers), api,
            proxy<SportBookingRepository> { method, _ ->
                check(method == "refreshSportBookings")
                bookingsRefresh++; Unit
            }, proxy<PendingSportBookingsRepository> { method, _ ->
                check(method == "refresh")
                pendingRefresh++; Unit
            }, ScheduleWidgetRefreshRequester { widgetRefresh++ },
            FakeClock(Instant.parse("2026-09-15T10:00:00Z")), diagnostics, gate, demo)

        suspend fun run(vararg lessons: String, enabled: Boolean = true) {
            gate.optedIn.value = enabled
            val payload = "{\"sportLessons\":[${lessons.ifEmpty { arrayOf(lesson(42)) }.joinToString(",")}] }"
            booker.book(queueKind, kotlinx.serialization.json.Json.parseToJsonElement(payload)) { notice ->
                if (failNotifier) error("Synthetic notification failure")
                notifications += notice.toAppNotification()
            }
        }
    }

    companion object {
        private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method.name, args) } as T

        private fun errorJson(message: String) = """{"error_code":2,"error_message":"$message","result":null}"""
        private fun lesson(id: Long, expired: Boolean = false): String {
            val date = if (expired) "2026-09-01" else "2026-09-21"
            return """{"id":$id,"sectionId":2,"sectionName":"  Секция  ","sectionLevel":1,"level":1,"typeId":2,""" +
                """"buildingId":13,"roomName":"Зал","start":"${date}T12:00:00+03:00","end":"${date}T13:00:00+03:00",""" +
                """"timeSlotId":3,"teacherIsu":900001,"teacherFio":"Тренер"}"""
        }
    }
}
