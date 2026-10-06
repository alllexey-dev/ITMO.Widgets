package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import java.io.File
import java.time.Clock
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** `MyItmoStorage` as the MyItmoApi 2.x `TokenStorage` over the 2.2 `myitmo_tokens.enc`. */
class MyItmoStorageTokenStorageTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val tokenFile by lazy { File(folder.root, "myitmo_tokens.enc") }

    @Test
    fun `reads the 2_2 token state field for field`() = runTest {
        tokenFile.writeText(SIGNED_IN_22)
        val storage = storage()

        val tokens = requireNotNull(storage.read())

        assertEquals(storage.getAccessToken(), tokens.accessToken)
        assertEquals(storage.getAccessExpiresAt(), tokens.accessExpiresAt.toEpochMilliseconds())
        assertEquals(storage.getRefreshToken(), tokens.refreshToken)
        assertEquals(storage.getRefreshExpiresAt(), tokens.refreshExpiresAt.toEpochMilliseconds())
        assertEquals(storage.getIdToken(), tokens.idToken)
        assertEquals("upgrade22-access", tokens.accessToken)
        assertEquals(Instant.fromEpochMilliseconds(1_787_443_200_000L), tokens.refreshExpiresAt)
    }

    @Test
    fun `writes the 2_2 token state back byte for byte through 2_x`() = runTest {
        tokenFile.writeText(SIGNED_IN_22)
        val storage = storage()

        storage.write(storage.read())

        assertEquals(SIGNED_IN_22, tokenFile.readText())
    }

    @Test
    fun `a 2_x write is what the stored fields and a new process hold`() = runTest {
        val storage = storage()

        storage.write(TOKENS)

        assertEquals("access", storage.getAccessToken())
        assertEquals(TOKENS.accessExpiresAt.toEpochMilliseconds(), storage.getAccessExpiresAt())
        assertEquals("refresh", storage.getRefreshToken())
        assertEquals(TOKENS.refreshExpiresAt.toEpochMilliseconds(), storage.getRefreshExpiresAt())
        assertEquals("header.payload.signature", storage.getIdToken())
        assertTrue(storage.hasRefreshToken())
        assertEquals("refresh", storage().getRefreshToken())
    }

    @Test
    fun `a refresh-token-only state is no 2_x session`() = runTest {
        tokenFile.writeText(REFRESH_ONLY_22)
        val storage = storage()

        assertNull(storage.read())
        assertTrue(storage.hasRefreshToken())
    }

    @Test
    fun `writing no tokens signs out`() = runTest {
        val storage = storage()
        storage.write(TOKENS)

        storage.write(null)

        assertNull(storage.read())
        assertNull(storage.getRefreshToken())
        assertFalse(storage.hasRefreshToken())
        assertNull(storage().read())
    }

    private fun storage() = MyItmoStorage(
        tokenFile = tokenFile,
        tokenCipher = PlainTokenCipher,
        clock = Clock.fixed(java.time.Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC),
        log = RecordingAppLog()
    )

    private object PlainTokenCipher : TokenCipher {
        override fun encrypt(value: String): String = value

        override fun decrypt(value: String): String = value
    }

    private companion object {
        /** Plaintext as 2.2 serialized it: unpadded URL-safe Base64 fields, covering every remainder and `-`/`_`. */
        const val SIGNED_IN_22 = "dXBncmFkZTIyLWFjY2Vzcw\n1784851200000\n0JjQstCw0L0_Pn4\n1787443200000\n" +
            "aGRyLtCY0LLQsNC9Pz5-w7wuc2ln"
        const val REFRESH_ONLY_22 = "~\n0\n0JjQstCw0L0_Pn4\n0\n~"

        val TOKENS = TokenSet(
            accessToken = "access",
            accessExpiresAt = Instant.parse("2026-07-24T00:05:00Z"),
            refreshToken = "refresh",
            refreshExpiresAt = Instant.parse("2026-07-24T00:10:00Z"),
            idToken = "header.payload.signature"
        )
    }
}
