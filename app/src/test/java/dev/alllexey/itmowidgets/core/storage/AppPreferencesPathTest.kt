package dev.alllexey.itmowidgets.core.storage

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** `StorageModule` opens `app_preferences` by okio path; the file and its contents stay those of 2.2. */
@RunWith(RobolectricTestRunner::class)
// A plain Application: these tests need no app graph, and the manifest's one cannot boot under Robolectric.
@Config(sdk = [35], application = Application::class)
class AppPreferencesPathTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the okio path is the file androidx put the preferences in`() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(
            context.preferencesDataStoreFile("app_preferences").absoluteFile,
            AndroidAppDirectories(context).preferencesDataStoreFile("app_preferences").toFile().absoluteFile
        )
    }

    @Test
    fun `createWithPath reads all 40 keys of the 2_2 capture as the file factory does`() = runTest {
        val byFile = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { capturedCopy("file") })
        val byPath = PreferenceDataStoreFactory.createWithPath(
            scope = backgroundScope,
            produceFile = { capturedCopy("path").toOkioPath() }
        )

        val expected = byFile.data.first().asMap()
        assertEquals(40, expected.size)
        assertEquals(expected, byPath.data.first().asMap())
    }

    private fun capturedCopy(name: String): File =
        CAPTURED_22.copyTo(File(folder.root, "$name/app_preferences.preferences_pb"))

    private companion object {
        /** G-04's capture of 2.2, read in place; `UpgradeFrom22Test` pins its SHA-256. */
        val CAPTURED_22: File = listOf(File("."), File("app"))
            .map { File(it, "src/androidTest/assets/upgrade-2.2/files/datastore/app_preferences.preferences_pb") }
            .first { it.isFile }
    }
}
