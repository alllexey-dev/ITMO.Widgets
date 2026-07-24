package dev.alllexey.itmowidgets.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyItmoStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tokenFile by lazy {
        File(context.cacheDir, "myitmo-storage-test/tokens.enc")
    }
    private val storage by lazy {
        MyItmoStorage(
            tokenFile = tokenFile,
            tokenCipher = PrefixTokenCipher,
            clock = Clock.fixed(
                Instant.parse("2026-07-24T00:00:00Z"),
                ZoneOffset.UTC
            )
        )
    }

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

    private object PrefixTokenCipher : TokenCipher {
        override fun encrypt(value: String): String {
            return "encrypted:${value.reversed()}"
        }

        override fun decrypt(value: String): String {
            return value.removePrefix("encrypted:").reversed()
        }
    }
}
