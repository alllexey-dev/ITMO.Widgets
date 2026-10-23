package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.storage.SignInLabelPreferences
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.RecordingAppNotifier
import dev.alllexey.itmowidgets.feature.connections.data.bell.ConnectionsBellSource
import dev.alllexey.itmowidgets.feature.connections.domain.Devices
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoHealth
import dev.alllexey.itmowidgets.feature.connections.presentation.FakeDevicesRepository
import dev.alllexey.itmowidgets.feature.connections.presentation.FakeItmoLogins
import dev.alllexey.itmowidgets.feature.connections.presentation.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The 7-day reminder (UX spec U2): bell items, one notification per sign-in, and Профиль's summary. */
class ConnectionsReminderTest {

    private val time = FixedAcademicTime()
    private val notifier = RecordingAppNotifier()
    private val preferences = SignInLabelPreferences(InMemoryPreferencesDataStore())

    @Test
    fun anEndingSignInRemindsOnceAndAgainAfterARenewal() = runTest {
        val devices = FakeDevicesRepository().apply { next = Devices(listOf(Samples.agent(), Samples.web()), emptyList()) }
        val source = ConnectionsBellSource(devices, preferences, notifier, time)

        assertEquals(AppResult.Success(Unit), source.refresh())
        source.refresh()
        assertEquals(1, notifier.shown.size)
        assertEquals(NotificationDestination.Connection(Samples.agentId.toString()), notifier.shown.single().destination)
        assertEquals(1, source.observe().first().size)

        devices.next = Devices(listOf(Samples.agent(expiresAt = Instant.parse("2026-10-12T09:00:00Z"))), emptyList())
        source.refresh()
        assertEquals(2, notifier.shown.size)
    }

    @Test
    fun aDistantSignInAndTheWebAreQuiet() = runTest {
        val far = Samples.agent(expiresAt = Instant.parse("2026-12-30T09:00:00Z"))
        val devices = FakeDevicesRepository().apply { next = Devices(listOf(far, Samples.web()), emptyList()) }
        val source = ConnectionsBellSource(devices, preferences, notifier, time)

        source.refresh()

        assertTrue(notifier.shown.isEmpty())
        assertTrue(source.observe().first().isEmpty())
    }

    @Test
    fun withoutTheServerTheBellStaysQuietAndHealthy() = runTest {
        val devices = FakeDevicesRepository().apply { failure = AppError.CustomServicesDisabled }

        assertEquals(AppResult.Success(Unit), ConnectionsBellSource(devices, preferences, notifier, time).refresh())
    }

    @Test
    fun profileCountsThisPhoneOtherDevicesAndLiveClientsAndNamesAClosedSignIn() = runTest {
        val closed = Samples.agent(health = ItmoHealth.CLOSED)
        val install = Samples.install(Samples.phoneId, current = true, sid = null)
        val devices = FakeDevicesRepository(Devices(listOf(closed, Samples.web()), listOf(install)))

        val summary = DevicesOverviewImpl(devices, FakeItmoLogins(), time).observe().first()

        assertEquals(3, summary.count, "this phone, the agent and the web")
        assertNotNull(summary.warning)
    }

    @Test
    fun itmoLabelsReadLikeIdItmoRu() {
        assertEquals("Chrome 141, Windows", ItmoLoginsRepositoryImpl.label("Windows", "Chrome/141.0.0"))
        assertEquals("Linux", ItmoLoginsRepositoryImpl.label("Linux", "Other/Unknown"))
        assertEquals("ITMO Widgets agent nas bot 1, Linux", ItmoLoginsRepositoryImpl.label("Linux", "ITMO Widgets agent nas bot/1.2"))
    }
}
