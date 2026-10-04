package dev.alllexey.itmowidgets.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.core.diagnostics.AndroidAppLog
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyItmoStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tokenFile by lazy {
        File(context.cacheDir, "myitmo-storage-test/tokens.enc")
    }
    private val storage by lazy { storage(PrefixTokenCipher) }

    @Before
    fun clearBefore() {
        tokenFile.parentFile?.deleteRecursively()
    }

    @After
    fun clearAfter() {
        tokenFile.parentFile?.deleteRecursively()
    }

    @Test
    fun writesEncryptedFileAndReadsPlainValue() {
        storage.setAccessToken("test-access-token")

        assertEquals("test-access-token", storage.getAccessToken())
        assertFalse(tokenFile.readText().contains("test-access-token"))
    }

    @Test
    fun replacesSessionWithRefreshTokenOnly() {
        storage.setAccessToken("old-access-token")
        storage.setAccessExpiresAt(42L)

        storage.replaceWithRefreshToken("new-refresh-token")

        assertNull(storage.getAccessToken())
        assertEquals(0L, storage.getAccessExpiresAt())
        assertEquals("new-refresh-token", storage.getRefreshToken())
    }

    @Test
    fun readsTheTwoPointTwoTokenStateAndWritesItBackByteIdentically() {
        for (sample in listOf(SIGNED_IN_22, REFRESH_ONLY_22)) {
            tokenFile.parentFile?.mkdirs()
            tokenFile.writeText(sample)
            val restored = storage(PlainTokenCipher)

            restored.setAccessExpiresAt(restored.getAccessExpiresAt())

            assertEquals(sample, tokenFile.readText())
        }
    }

    @Test
    fun decodesEveryFieldOfTheTwoPointTwoTokenState() {
        tokenFile.parentFile?.mkdirs()
        tokenFile.writeText(SIGNED_IN_22)

        val restored = storage(PlainTokenCipher)

        assertEquals("upgrade22-access", restored.getAccessToken())
        assertEquals(1_784_851_200_000L, restored.getAccessExpiresAt())
        assertEquals("Иван?>~", restored.getRefreshToken())
        assertEquals(1_787_443_200_000L, restored.getRefreshExpiresAt())
        assertEquals("hdr.Иван?>~ü.sig", restored.getIdToken())
    }

    @Test
    fun acceptsPaddedFieldsLikeTheTwoPointTwoDecoder() {
        tokenFile.parentFile?.mkdirs()
        tokenFile.writeText("dXBncmFkZTIyLWFjY2Vzcw==\n0\n0JjQstCw0L0_Pn4=\n0\n~")

        val restored = storage(PlainTokenCipher)

        assertEquals("upgrade22-access", restored.getAccessToken())
        assertEquals("Иван?>~", restored.getRefreshToken())
        assertTrue(tokenFile.exists())
    }

    private fun storage(cipher: TokenCipher) = MyItmoStorage(
        tokenFile = tokenFile,
        tokenCipher = cipher,
        clock = Clock.fixed(
            Instant.parse("2026-07-24T00:00:00Z"),
            ZoneOffset.UTC
        ),
        log = AndroidAppLog()
    )

    /** Plaintext as 2.2 serialized it: unpadded URL-safe Base64 fields, covering every remainder and `-`/`_`. */
    private companion object {
        const val SIGNED_IN_22 = "dXBncmFkZTIyLWFjY2Vzcw\n1784851200000\n0JjQstCw0L0_Pn4\n1787443200000\n" +
            "aGRyLtCY0LLQsNC9Pz5-w7wuc2ln"
        const val REFRESH_ONLY_22 = "~\n0\n0JjQstCw0L0_Pn4\n0\n~"
    }

    private object PlainTokenCipher : TokenCipher {
        override fun encrypt(value: String): String = value

        override fun decrypt(value: String): String = value
    }

    private object PrefixTokenCipher : TokenCipher {
        override fun encrypt(value: String): String {
            return "encrypted:${value.reversed()}"
        }

        override fun decrypt(value: String): String {
            return value.removePrefix("encrypted:").reversed()
        }
    }
}
