package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import okio.FileSystem
import okio.Path
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey

class IosAppDirectoriesTest {

    private val temporary = TemporaryDirectory()
    private val log = RecordingAppLog()

    @AfterTest
    fun deleteTemporary() = temporary.delete()

    @Test
    fun laysOutTheAppContainer() {
        val directories = createIn(temporary.root)

        assertEquals(temporary.root / "Application Support" / "files", directories.files)
        assertEquals(temporary.root / "Caches", directories.cache)
        assertEquals(temporary.root / "Application Support" / "no-backup", directories.noBackup)
        listOf(directories.files, directories.cache, directories.noBackup).forEach {
            assertTrue(FileSystem.SYSTEM.metadata(it).isDirectory, "$it")
        }
    }

    @Test
    fun excludesOnlyNoBackupFromBackup() {
        val directories = createIn(temporary.root)

        assertTrue(isExcludedFromBackup(directories.noBackup))
        assertEquals(false, isExcludedFromBackup(directories.files))
        assertEquals(emptyList(), log.lines)
    }

    @Test
    fun keepsTheSinglePreferencesDataStoreInFiles() {
        val directories = createIn(temporary.root)

        assertEquals(
            temporary.root / "Application Support" / "files" / "datastore" / "app_preferences.preferences_pb",
            directories.preferencesDataStoreFile("app_preferences")
        )
    }

    @Test
    fun createsTheSystemDirectories() {
        val directories = IosAppDirectories.create(log)

        listOf(directories.files, directories.cache, directories.noBackup).forEach {
            assertTrue(FileSystem.SYSTEM.metadata(it).isDirectory, "$it")
        }
        assertTrue(isExcludedFromBackup(directories.noBackup))
    }

    private fun createIn(root: Path) = IosAppDirectories.create(
        applicationSupport = root / "Application Support",
        caches = root / "Caches",
        log = log
    )

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun isExcludedFromBackup(directory: Path): Boolean = memScoped {
        val value = alloc<ObjCObjectVar<Any?>>()
        val url = NSURL.fileURLWithPath(directory.toString(), true)
        url.getResourceValue(value.ptr, NSURLIsExcludedFromBackupKey, null)
        (value.value as NSNumber).boolValue
    }
}
