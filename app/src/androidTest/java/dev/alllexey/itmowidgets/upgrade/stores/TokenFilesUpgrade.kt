package dev.alllexey.itmowidgets.upgrade.stores

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

/** `no_backup/myitmo_tokens.enc` and `no_backup/bars_tokens.enc`, sealed by 2.2's `v1:` envelope: the session stays. */
object TokenFilesUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val myItmoFile = File(fixture.noBackupFilesDir, "myitmo_tokens.enc")
        assertTrue(myItmoFile.readText().startsWith("v1:"))
        assertFalse(myItmoFile.readText().contains("upgrade22"))

        val myItmo = MyItmoStorage(myItmoFile, AndroidKeystoreTokenCipher(), fixture.clock)
        assertTrue(myItmo.hasRefreshToken())
        assertEquals("upgrade22-access-token", myItmo.getAccessToken())
        assertEquals(Captured22.TOKEN_EXPIRES_AT, myItmo.getAccessExpiresAt())
        assertEquals("upgrade22-refresh-token", myItmo.getRefreshToken())
        assertEquals(Captured22.TOKEN_EXPIRES_AT, myItmo.getRefreshExpiresAt())
        assertEquals(Captured22.ID_TOKEN, myItmo.getIdToken())
        // A read never rewrites the file; an unreadable one would have been deleted.
        assertTrue(myItmoFile.exists())

        val bars = BarsTokenStore(File(fixture.noBackupFilesDir, "bars_tokens.enc"), AndroidKeystoreTokenCipher())
        assertEquals(Captured22.BARS_HEADER, bars.load(Captured22.ISU))
        assertNull(bars.load(Captured22.ISU + 1))
    }
}
