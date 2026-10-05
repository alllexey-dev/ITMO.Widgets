package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.diagnostics.AndroidAppLog
import dev.alllexey.itmowidgets.core.storage.AndroidAppDirectories
import dev.alllexey.itmowidgets.core.storage.AndroidKeystoreTokenCipher
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.upgrade.Captured22
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * `no_backup/myitmo_tokens.enc` and `no_backup/bars_tokens.enc`, sealed by 2.2's `v1:` envelope: the session stays,
 * also when 2.2's `android.util.AtomicFile` died mid-write and left a `.bak` (API 26-29) or a `.new` (API 30+).
 */
object TokenFilesUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val noBackup = AndroidAppDirectories(fixture.context).noBackup.toFile()
        val myItmoFile = File(noBackup, "myitmo_tokens.enc")
        assertTrue(myItmoFile.readText().startsWith("v1:"))
        assertFalse(myItmoFile.readText().contains("upgrade22"))

        assertCapturedSession(myItmoFile, fixture)

        val bars = BarsTokenStore(File(noBackup, "bars_tokens.enc"), AndroidKeystoreTokenCipher())
        assertEquals(Captured22.BARS_HEADER, bars.load(Captured22.ISU))
        assertNull(bars.load(Captured22.ISU + 1))

        // API 26-29: AtomicFile moved the valid file to `.bak` and died while rewriting the file itself.
        val captured = myItmoFile.readBytes()
        File(noBackup, "myitmo_tokens.enc.bak").writeBytes(captured)
        myItmoFile.writeText("v1:trunc")
        assertCapturedSession(myItmoFile, fixture)
        assertFalse(File(noBackup, "myitmo_tokens.enc.bak").exists())
        assertTrue(captured.contentEquals(myItmoFile.readBytes()))

        // API 30+: AtomicFile died while writing `.new` beside the valid file.
        File(noBackup, "myitmo_tokens.enc.new").writeText("v1:trunc")
        assertCapturedSession(myItmoFile, fixture)
        assertFalse(File(noBackup, "myitmo_tokens.enc.new").exists())
        assertTrue(captured.contentEquals(myItmoFile.readBytes()))
    }

    private fun assertCapturedSession(myItmoFile: File, fixture: Upgrade22Fixture) {
        val myItmo = MyItmoStorage(myItmoFile, AndroidKeystoreTokenCipher(), fixture.clock, AndroidAppLog())
        assertTrue(myItmo.hasRefreshToken())
        assertEquals("upgrade22-access-token", myItmo.getAccessToken())
        assertEquals(Captured22.TOKEN_EXPIRES_AT, myItmo.getAccessExpiresAt())
        assertEquals("upgrade22-refresh-token", myItmo.getRefreshToken())
        assertEquals(Captured22.TOKEN_EXPIRES_AT, myItmo.getRefreshExpiresAt())
        assertEquals(Captured22.ID_TOKEN, myItmo.getIdToken())
        // A read never rewrites the file; an unreadable one would have been deleted.
        assertTrue(myItmoFile.exists())
    }
}
