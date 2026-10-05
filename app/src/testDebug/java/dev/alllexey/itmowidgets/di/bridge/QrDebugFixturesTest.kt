package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrCodeRepositoryImpl
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.experimental.LazyApplication
import org.robolectric.annotation.experimental.LazyApplication.LazyLoad

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class QrDebugFixturesTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `a fixture replaces the repository and the clock until its host unloads it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val release = koin.get<QrCodeRepository>()

        val fixture = QrDebugFixtures.load(application, FakeRepository, EpochClock)
        assertSame(FakeRepository, koin.get<QrCodeRepository>())
        assertSame(EpochClock, koin.get<Clock>())

        QrDebugFixtures.unload(application, fixture)
        assertSame(release, koin.get<QrCodeRepository>())
        assertSame(koin.get<QrCodeRepositoryImpl>(), koin.get<QrCodeRepository>())
        assertSame(CoreBridgeEntryPoint.from(application).clock(), koin.get<Clock>())
    }

    @Test
    fun `a replaced fixture is left to the host that replaced it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        val first = QrDebugFixtures.load(application, FakeRepository, EpochClock)
        val second = QrDebugFixtures.load(application, FakeRepository, EpochClock)
        QrDebugFixtures.unload(application, first)
        assertSame(FakeRepository, koin.get<QrCodeRepository>())

        QrDebugFixtures.unload(application, second)
        assertNotSame(FakeRepository, koin.get<QrCodeRepository>())
    }

    private object FakeRepository : QrCodeRepository {
        override suspend fun currentQr(): QrCodeSnapshot? = null
        override fun observeQrHex() = emptyFlow<String>()
        override suspend fun currentQrHex(allowExpired: Boolean): String? = null
        override suspend fun refreshQrHex(force: Boolean): AppResult<Unit> = AppResult.Success(Unit)
        override fun clearCache() = Unit
    }

    private object EpochClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    /** As in `KoinStartTest`: Robolectric's `onCreate()` stops at `FcmWork.syncToken` after Koin and Hilt are up. */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }
}
