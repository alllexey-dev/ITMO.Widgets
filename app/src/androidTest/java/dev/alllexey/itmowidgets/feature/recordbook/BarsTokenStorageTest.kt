package dev.alllexey.itmowidgets.feature.recordbook

import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.core.storage.AndroidKeystoreTokenCipher
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BarsTokenStorageTest {
    @Test fun encryptedBarsSessionSurvivesRecreationAndIsRemovedOnClear() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.noBackupFilesDir, "bars-test-only.enc")
        val cipher = AndroidKeystoreTokenCipher()
        val token = "Bearer synthetic-instrumentation-credential"
        try {
            val store = BarsTokenStore(file, cipher)
            store.install(123, token)
            assertFalse(file.readText().contains(token))
            assertEquals(token, BarsTokenStore(file, cipher).load(123))
            assertNull(BarsTokenStore(file, cipher).load(999))
            store.clear()
            assertFalse(file.exists())
            assertNull(BarsTokenStore(file, cipher).load(123))
        } finally { file.delete() }
    }
}
