package dev.alllexey.itmowidgets.feature.sport.data.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.ItmoWidgetsImpl
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.notification.*
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.testing.myItmoStub
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportActionRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics

class SportSignPushHandlerTest {
    @Test fun `both payload types book then satisfy notify and refresh every projection`() = runTest {
        for (auto in listOf(false, true)) {
            val fixture = Fixture(auto)
            fixture.run()
            assertEquals(listOf("markSport${if (auto) "Auto" else "Free"}SignEntrySatisfiedByLesson:42"), fixture.queue)
            assertEquals(1, fixture.notifications.size)
            val notification = fixture.notifications.single()
            assertEquals(UiText.Resource(R.string.notification_sport_success), notification.title)
            assertEquals(NotificationDestination.Sport, notification.destination)
            assertEquals(AppNotificationChannels.SPORT, notification.channel)
            assertEquals(UiText.Resource(R.string.notification_sport_lesson, listOf("Секция", "21 сент., 12:00")), notification.text)
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
            assertEquals(listOf("cancelSport${if (auto) "Auto" else "Free"}SignEntryByLesson:42"), fixture.queue)
            assertEquals(UiText.Resource(R.string.notification_sport_failure), fixture.notifications.single().title)
        }
        // Preserve the legacy signature's context-length condition, not a generic substring test.
        val shortContext = Fixture(false)
        shortContext.response = errorJson(NO_CAPACITY_MESSAGE + "x".repeat(20))
        shortContext.run()
        assertTrue(shortContext.queue.single().startsWith("cancel"))
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
        assertEquals(listOf("markSportFreeSignEntrySatisfiedByLesson:42"), fixture.queue)
        assertEquals(2, fixture.signCalls)
        assertEquals(1, fixture.pendingRefresh)
    }

    @Test fun `disabled community services do not book or refresh`() = runTest {
        val fixture = Fixture(false)
        fixture.run(enabled = false)
        assertEquals(0, fixture.signCalls)
        assertEquals(0, fixture.bookingsRefresh)
    }

    private class Fixture(auto: Boolean) {
        val settings = AppSettingsStorage(MemoryPreferences())
        val queue = mutableListOf<String>()
        val notifications = mutableListOf<AppNotification>()
        var response = """{"error_code":0,"result":[42]}"""
        var networkFailure = false
        var failFirst = false
        var failNotifier = false
        var signCalls = 0
        var bookingsRefresh = 0
        var pendingRefresh = 0
        var widgetRefresh = 0
        private val myItmo = myItmoStub {
            signCalls++
            if (networkFailure || (failFirst && signCalls == 1)) throw IOException("Synthetic network failure")
            response
        }
        private val gson = ItmoWidgetsImpl(myItmo).gson
        private val api = proxy<ItmoWidgetsApi> { method, args ->
            queue += "$method:${args!![0]}"
            ApiResponse.success("OK")
        }
        private val handler = SportSignPushHandler(auto, gson,
            SportActionRepositoryImpl(settings, myItmo.api, api), api,
            proxy<SportBookingRepository> { method, _ ->
                check(method == "refreshSportBookings")
                bookingsRefresh++; Unit
            }, proxy<PendingSportBookingsRepository> { method, _ ->
                check(method == "refresh")
                pendingRefresh++; Unit
            }, ScheduleWidgetRefreshRequester { widgetRefresh++ },
            object : AppNotifier {
                override fun show(notification: AppNotification) {
                    if (failNotifier) error("Synthetic notification failure")
                    notifications += notification
                }
                override fun clear() = Unit
            }, Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC), RecordingDiagnostics())

        suspend fun run(vararg lessons: String, enabled: Boolean = true) {
            settings.setCustomServicesEnabled(enabled)
            val payload = "{\"sportLessons\":[${lessons.ifEmpty { arrayOf(lesson(42)) }.joinToString(",")}] }"
            handler.handle(com.google.gson.JsonParser.parseString(payload))
        }
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    companion object {
        private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method.name, args) } as T

        private fun errorJson(message: String) = """{"error_code":2,"error_message":"$message","result":null}"""
        private fun lesson(id: Long, expired: Boolean = false): String {
            val date = if (expired) "2026-09-01" else "2026-09-21"
            return """{"id":$id,"sectionName":"  Секция  ","start":"${date}T12:00:00+03:00","end":"${date}T13:00:00+03:00"}"""
        }
    }
}
