package dev.alllexey.itmowidgets.feature.recordbook

import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.core.platform.FileSecureStore
import dev.alllexey.itmowidgets.core.storage.AndroidKeystoreTokenCipher
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import java.io.File
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test

class BarsTokenStorageTest {
    @Test fun encryptedBarsSessionSurvivesRecreationAndIsRemovedOnClear() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // A directory of its own: the store always names the session `bars_tokens.enc`.
        val directory = File(context.noBackupFilesDir, "bars-test-only").apply { mkdirs() }
        val file = File(directory, "bars_tokens.enc")
        val cipher = AndroidKeystoreTokenCipher()
        fun store() = BarsTokenStore(FileSecureStore(directory.toOkioPath(), cipher))
        val token = "Bearer synthetic-instrumentation-credential"
        try {
            val store = store()
            store.install(123, token)
            assertFalse(file.readText().contains(token))
            assertTrue(file.readText().startsWith("v1:"))
            assertEquals(token, store().load(123))
            assertNull(store().load(999))
            store.clear()
            assertFalse(file.exists())
            assertNull(store().load(123))
        } finally { directory.deleteRecursively() }
    }
}
