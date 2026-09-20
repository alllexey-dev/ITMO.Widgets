package dev.alllexey.itmowidgets.feature.qr.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrHomeCardSourceTest {
    private val repository = FakeRepository()
    private val preferences = FakePreferences()
    private val clock = Clock.fixed(Instant.ofEpochMilli(10_000), ZoneOffset.UTC)
    private val source = QrHomeCardSource(repository, preferences, clock)

    @Test
    fun `a valid code becomes a pass with the spoiler preference`() = runTest {
        repository.code = QrCodeSnapshot("ITMO-TEST", 20_000)
        preferences.spoiler = true

        val pass = (source.observe().first().single() as HomeCard.Qr).pass!!

        assertEquals("ITMO-TEST", pass.hex)
        assertEquals(20_000, pass.expiresAtMillis)
        assertTrue(pass.spoiler)
    }

    @Test
    fun `an expired or missing code keeps the card without a pass`() = runTest {
        repository.code = QrCodeSnapshot("OLD", 10_000)
        assertNull((source.observe().first().single() as HomeCard.Qr).pass)

        repository.code = null
        assertNull((source.observe().first().single() as HomeCard.Qr).pass)
    }

    @Test
    fun `revalidation refreshes only once the code is gone`() = runTest {
        repository.code = QrCodeSnapshot("ITMO-TEST", 20_000)
        source.revalidate()
        assertEquals(0, repository.refreshes.size)

        repository.code = QrCodeSnapshot("OLD", 5_000)
        source.revalidate()
        assertEquals(listOf(false), repository.refreshes)
    }

    @Test
    fun `refresh passes the repository result through`() = runTest {
        repository.result = AppResult.Failure(AppError.Network)

        assertEquals(AppResult.Failure(AppError.Network), source.refresh())
    }

    private class FakePreferences : QrAppearancePreferences {
        var spoiler = false
        override suspend fun useDynamicColors() = true
        override suspend fun isSpoilerEnabled() = spoiler
        override suspend fun spoilerAnimationType() = QrAnimationType.entries.first()
    }

    private class FakeRepository : QrCodeRepository {
        var code: QrCodeSnapshot? = null
        var result: AppResult<Unit> = AppResult.Success(Unit)
        val refreshes = mutableListOf<Boolean>()
        val hex = MutableStateFlow("")
        override suspend fun currentQr() = code
        override suspend fun currentQrHex(allowExpired: Boolean) = code?.hex
        override fun observeQrHex() = hex
        override suspend fun refreshQrHex(force: Boolean): AppResult<Unit> {
            refreshes += force
            return result
        }
        override fun clearCache() { code = null }
    }
}
