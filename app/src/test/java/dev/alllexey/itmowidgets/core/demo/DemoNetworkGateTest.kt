package dev.alllexey.itmowidgets.core.demo

import android.content.Context
import android.content.ContextWrapper
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.DefaultBackendDeviceSession
import dev.alllexey.itmowidgets.core.session.DefaultBackendIdentitySync
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.core.testing.unreachable
import dev.alllexey.itmowidgets.core.testing.unreachableMyItmo
import dev.alllexey.itmowidgets.feature.qr.data.demo.DemoQr
import dev.alllexey.itmowidgets.feature.qr.data.remote.QrCodeRemoteDataSourceImpl
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import dev.alllexey.itmowidgets.feature.settings.data.SettingsRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.update.data.AppUpdateRepositoryImpl
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.weblogin.data.WebLoginRepositoryImpl
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Single-call clients of the demo session: the pass, the update offer, web sign-in, privacy and Backend sessions. */
class DemoNetworkGateTest {
    private val demo = FakeDemoMode(active = true)
    private val backend = unreachable<ItmoWidgetsApi>()
    private val settings = AppSettingsStorage(InMemoryPreferencesDataStore()).also {
        kotlinx.coroutines.runBlocking { it.setCustomServicesEnabled(true) }
    }

    @Test
    fun `the pass is a code no turnstile accepts`() = runTest {
        assertEquals(DemoQr.HEX, QrCodeRemoteDataSourceImpl(unreachableMyItmo(), demo).getQrHex())
        assertTrue(DemoQr.HEX.startsWith("DEMO"))
    }

    @Test
    fun `the demo pass fits the code the pass screen draws`() {
        assertEquals(1, QrCodeGenerator().generate(DemoQr.HEX).version)
    }

    @Test
    fun `no update is offered and web sign-in is refused`() = runTest {
        val update = AppUpdateRepositoryImpl(
            backend, Connected, UtilityStorage(InMemoryPreferencesDataStore(), appVersionName = "2.2"), AppVersionName("2.2"),
            Clock.systemUTC(), RecordingDiagnostics(), demo
        )
        val webLogin = WebLoginRepositoryImpl(Connected, backend, demo)

        assertNull(update.loadUpdate())
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), webLogin.preview("ABCD2345"))
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), webLogin.approve(UUID.randomUUID()))
    }

    @Test
    fun `privacy shows the defaults and refuses changes`() = runTest {
        val repository = SettingsRepositoryImpl(settings, backend, demo)

        repository.refreshSharingSettings()

        assertTrue(repository.observeSharingSettings().first() is SharingSettingsState.Content)
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), repository.setScheduleVisibility(SharingVisibility.ALL))
    }

    @Test
    fun `Backend never learns about the demo session`() = runTest {
        val user = object : CurrentUserProvider {
            override suspend fun getCurrentUser() = CurrentUser(DemoPeople.ME_ISU, DemoPeople.ME_NAME, null)
        }
        val utility = UtilityStorage(InMemoryPreferencesDataStore(), appVersionName = "2.2").also {
            it.setFirebaseToken("demo-token")
        }
        val devices = DefaultBackendDeviceSession(settings, utility, backend, "Pixel", user, demo)
        val identity = DefaultBackendIdentitySync(unusedContext(), settings, unreachableMyItmo(), backend, RecordingDiagnostics(), demo)

        devices.registerCurrentDevice()
        devices.unregisterCurrentDevice()

        assertTrue(identity.sync())
    }

    /** A context nobody may touch: allocated without Android's stub constructor, any call on it fails. */
    private fun unusedContext(): Context {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val allocate = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, ContextWrapper::class.java) as Context
    }

    private object Connected : CustomServicesRepository {
        override fun observeEnabled(): Flow<Boolean> = flowOf(true)
        override suspend fun isEnabled() = true
        override suspend fun setEnabled(enabled: Boolean) = Unit
    }
}
