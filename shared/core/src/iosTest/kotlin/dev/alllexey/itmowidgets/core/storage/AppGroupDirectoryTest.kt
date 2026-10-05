package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.FileSystem

class AppGroupDirectoryTest {

    private val temporary = TemporaryDirectory()
    private val appDirectories = IosAppDirectories.create(
        applicationSupport = temporary.root / "app" / "Application Support",
        caches = temporary.root / "app" / "Caches",
        log = RecordingAppLog()
    )

    @AfterTest
    fun deleteTemporary() = temporary.delete()

    @Test
    fun usesTheFirstGroupWhoseContainerResolves() {
        val altStoreContainer = temporary.root / "group-altstore"
        val log = RecordingAppLog()

        val directory = AppGroupDirectory.resolve(
            identifiers = BundleIdentifiers("group.example.app", null, listOf("group.example.app.TEAM")),
            appDirectories = appDirectories,
            log = log,
            containerOf = { id -> altStoreContainer.takeIf { id == "group.example.app.TEAM" } }
        )

        assertEquals(altStoreContainer, directory.root)
        assertTrue(directory.isShared)
        assertTrue(FileSystem.SYSTEM.metadata(altStoreContainer).isDirectory)
        assertEquals(altStoreContainer / "locks", directory.locks)
        assertEquals(altStoreContainer / "session-v1.json", directory.file("session-v1.json"))
        assertEquals(emptyList(), log.lines)
    }

    /** The only test that falls back: the warning is once per process. */
    @Test
    fun fallsBackToTheAppContainerWithoutAGroupContainerAndLogsOnce() {
        val log = RecordingAppLog()
        // The Kotlin/Native test binary has no App Group entitlement, as an unsigned build (SP-23): nothing resolves.
        val identifiers = BundleIdentifiers("group.example.unresolved", null)

        val first = AppGroupDirectory.resolve(identifiers, appDirectories, log)
        val second = AppGroupDirectory.resolve(identifiers, appDirectories, log)

        assertFalse(first.isShared)
        assertEquals(appDirectories.noBackup / "app-group", first.root)
        assertEquals(first.root, second.root)
        assertTrue(FileSystem.SYSTEM.metadata(first.root).isDirectory)
        assertEquals(1, log.lines.count { it.startsWith("WARN:AppGroupDirectory:") }, "${log.lines}")
    }

    @Test
    fun refusesAPathAsAFileName() {
        val directory = AppGroupDirectory(temporary.root, isShared = true)

        listOf("", "locks/x.lock", "..", "../secret").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) { directory.file(name) }
        }
    }
}
