package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmowidgets.core.testing.InMemorySecureStore
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class KeychainTokenStorageTest {

    private val secrets = InMemorySecureStore()
    private val log = RecordingAppLog()
    private val storage = KeychainTokenStorage(secrets, FixedClock, log)

    @Test
    fun tokensWrittenAsTheSessionReadAsAMyItmoApiTokenSet() = runTest {
        storage.replaceWithTokens(
            SessionTokens(
                accessToken = "access",
                accessExpiresInSeconds = 300,
                refreshToken = "refresh",
                refreshExpiresInSeconds = 3600,
                idToken = "id"
            )
        )

        val tokens = assertNotNull(storage.read())
        assertEquals("access", tokens.accessToken)
        assertEquals(NOW + 5.minutes, tokens.accessExpiresAt)
        assertEquals("refresh", tokens.refreshToken)
        assertEquals(NOW + 1.hours, tokens.refreshExpiresAt)
        assertEquals("id", tokens.idToken)
    }

    @Test
    fun aTokenSetWrittenByMyItmoApiReadsAsTheSession() = runTest {
        storage.write(TokenSet("access", NOW, "refresh", NOW + 1.hours, "id"))

        assertTrue(storage.hasRefreshToken())
        assertEquals("id", storage.getIdToken())

        storage.write(null)

        assertFalse(storage.hasRefreshToken())
        assertNull(storage.getIdToken())
        assertNull(secrets.read(KeychainTokenStorage.ITEM))
    }

    @Test
    fun theItemHoldsTheFiveFieldsAndroidSealsInMyitmoTokensEnc() = runTest {
        storage.write(TokenSet("access", Instant.fromEpochMilliseconds(1_000), "refresh", Instant.fromEpochMilliseconds(2_000), "id.token"))

        assertEquals("YWNjZXNz\n1000\ncmVmcmVzaA\n2000\naWQudG9rZW4", secrets.read(KeychainTokenStorage.ITEM))

        storage.replaceWithRefreshToken("refresh")

        assertEquals("~\n0\ncmVmcmVzaA\n0\n~", secrets.read(KeychainTokenStorage.ITEM))
    }

    @Test
    fun readsPaddedFieldsAsTheAndroidReaderDoes() = runTest {
        secrets.write(KeychainTokenStorage.ITEM, "YWNjZXNz\n1000\ncmVmcmVzaA==\n2000\naWQ=")

        val tokens = assertNotNull(storage.read())
        assertEquals("refresh", tokens.refreshToken)
        assertEquals("id", tokens.idToken)
    }

    @Test
    fun aRefreshTokenAloneIsASessionButNotYetATokenSet() = runTest {
        storage.replaceWithRefreshToken("refresh")

        assertTrue(storage.hasRefreshToken())
        assertNull(storage.read())
    }

    @Test
    fun anotherStorageOverTheSameItemReadsEveryWriteAtOnce() = runTest {
        val otherProcess = KeychainTokenStorage(secrets, FixedClock, log)
        storage.write(TokenSet("first", NOW, "refresh", NOW + 1.hours, "id"))
        assertEquals("first", otherProcess.read()?.accessToken)

        otherProcess.write(TokenSet("second", NOW, "refresh", NOW + 1.hours, "id"))

        assertEquals("second", storage.read()?.accessToken)
    }

    @Test
    fun anUnreadableItemIsDroppedWithoutLoggingItsValue() = runTest {
        listOf("secret-garbage", "a\nb\nc\nd\ne", "YWNjZXNz\nlater\ncmVmcmVzaA\n0\n~", "%%%\n0\n~\n0\n~").forEach { value ->
            secrets.write(KeychainTokenStorage.ITEM, value)

            assertNull(storage.read(), value)
            assertNull(secrets.read(KeychainTokenStorage.ITEM), value)
        }
        assertEquals(4, log.lines.size)
        assertTrue(log.lines.none { "garbage" in it || "YWNj" in it }, log.lines.toString())
    }

    @Test
    fun aLifetimeBeyondTheRangeSaturates() = runTest {
        storage.replaceWithTokens(SessionTokens("access", Long.MAX_VALUE / 10, "refresh", Long.MAX_VALUE, "id"))

        val tokens = assertNotNull(storage.read())
        assertEquals(Long.MAX_VALUE, tokens.accessExpiresAt.toEpochMilliseconds())
        assertEquals(Long.MAX_VALUE, tokens.refreshExpiresAt.toEpochMilliseconds())
    }

    @Test
    fun clearingRemovesTheItem() {
        storage.replaceWithRefreshToken("refresh")

        storage.clearTokens()

        assertNull(secrets.read(KeychainTokenStorage.ITEM))
        assertFalse(storage.hasRefreshToken())
    }

    private object FixedClock : Clock {
        override fun now(): Instant = NOW
    }

    private companion object {
        val NOW: Instant = Instant.fromEpochMilliseconds(1_790_000_000_000)
    }
}
