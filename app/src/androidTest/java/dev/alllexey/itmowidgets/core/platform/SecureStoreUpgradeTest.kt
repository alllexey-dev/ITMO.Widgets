package dev.alllexey.itmowidgets.core.platform

import dev.alllexey.itmowidgets.core.diagnostics.AndroidAppLog
import dev.alllexey.itmowidgets.core.storage.AndroidKeystoreTokenCipher
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.upgrade.Captured22
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The token files 2.2 sealed with the Keystore read through [FileSecureStore] as they are, and what it writes back by
 * the same name is what the 2.2 reader `MyItmoStorage` and the BARS session store (on it since L12 KM-11b1) read.
 */
class SecureStoreUpgradeTest {

    private lateinit var fixture: Upgrade22Fixture

    @Before
    fun copyThe22DataDirectory() {
        fixture = Upgrade22Fixture()
    }

    @After
    fun removeTheCopy() {
        fixture.close()
    }

    @Test
    fun sealed22TokenFilesReadThroughSecureStore() {
        val store = FileSecureStore(fixture.noBackupFilesDir.toOkioPath(), AndroidKeystoreTokenCipher())

        for (name in TOKEN_FILES) {
            assertEquals(name, plaintext(name), store.read(name))
        }
    }

    @Test
    fun valuesWrittenBackReadThrough22Readers() {
        val source = FileSecureStore(fixture.noBackupFilesDir.toOkioPath(), AndroidKeystoreTokenCipher())
        val target = File(fixture.cacheDir, "secure-store-target").apply { mkdirs() }
        val store = FileSecureStore(target.toOkioPath(), AndroidKeystoreTokenCipher())

        for (name in TOKEN_FILES) store.write(name, checkNotNull(source.read(name)))

        assertTrue(File(target, MY_ITMO).readText().startsWith("v1:"))
        val myItmo = MyItmoStorage(File(target, MY_ITMO), AndroidKeystoreTokenCipher(), fixture.clock, AndroidAppLog())
        assertEquals("upgrade22-access-token", myItmo.getAccessToken())
        assertEquals("upgrade22-refresh-token", myItmo.getRefreshToken())
        assertEquals(Captured22.TOKEN_EXPIRES_AT, myItmo.getRefreshExpiresAt())
        assertEquals(Captured22.ID_TOKEN, myItmo.getIdToken())
        val bars = BarsTokenStore(store)
        assertEquals(Captured22.BARS_HEADER, runBlocking { bars.load(Captured22.ISU) })
        for (name in TOKEN_FILES) assertEquals(name, plaintext(name), store.read(name))
    }

    private fun plaintext(name: String): String = fixture.assetBytes("plaintext/$name").toString(Charsets.UTF_8)

    private companion object {
        const val MY_ITMO = "myitmo_tokens.enc"
        const val BARS = "bars_tokens.enc"
        val TOKEN_FILES = listOf(MY_ITMO, BARS)
    }
}
