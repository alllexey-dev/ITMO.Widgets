package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.platform.WebsiteDataClearer
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.storage.FileCrossProcessLock
import dev.alllexey.itmowidgets.core.storage.TemporaryDirectory
import dev.alllexey.itmowidgets.core.storage.WidgetReloader
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem

@OptIn(ExperimentalCoroutinesApi::class)
class IosSessionDataCleanersTest {

    private val temporary = TemporaryDirectory()
    private val directory = AppGroupDirectory(temporary.root / "group", isShared = true)
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)

    @AfterTest
    fun deleteTemporary() = temporary.delete()

    @Test
    fun theAppGroupCleanerRemovesEverySnapshotButKeepsTheLocks() = runTest(dispatcher) {
        FileCrossProcessLock(directory.locks).withLock(MY_ITMO_REFRESH_LOCK) {}
        SessionSnapshotWriter(AppGroupSnapshotWriter(directory, WidgetReloader {}))
            .write(SessionSnapshot(isu = 123456, demo = false, alertsAllowed = true, servicesEnabled = true))
        FileSystem.SYSTEM.write(directory.file("qr-pass-v1.json")) { writeUtf8("{}") }
        FileSystem.SYSTEM.createDirectories(directory.root / "later-card")

        FileSystem.SYSTEM.write(directory.file(".qr-pass-v1.json.k3x9.tmp")) { writeUtf8("{") }

        AppGroupSessionDataCleaner(directory, dispatchers).clearSessionData()

        assertEquals(listOf(directory.locks), FileSystem.SYSTEM.list(directory.root))
        assertTrue(FileSystem.SYSTEM.exists(directory.locks / "$MY_ITMO_REFRESH_LOCK.lock"))
    }

    @Test
    fun theAppGroupCleanerKeepsTheContainersOwnEntries() = runTest(dispatcher) {
        // What the system puts in every App Group container; without the metadata it drops the container as stale.
        val metadata = directory.file(".com.apple.mobile_container_manager.metadata.plist")
        val library = directory.root / "Library"
        FileSystem.SYSTEM.createDirectories(library / "Caches")
        FileSystem.SYSTEM.createDirectories(library / "Preferences")
        FileSystem.SYSTEM.write(metadata) { writeUtf8("<plist/>") }
        SessionSnapshotWriter(AppGroupSnapshotWriter(directory, WidgetReloader {}))
            .write(SessionSnapshot(isu = 123456, demo = false, alertsAllowed = true, servicesEnabled = true))

        AppGroupSessionDataCleaner(directory, dispatchers).clearSessionData()

        assertEquals(listOf(metadata, library).sorted(), FileSystem.SYSTEM.list(directory.root).sorted())
        assertTrue(FileSystem.SYSTEM.exists(library / "Caches"))
        assertTrue(FileSystem.SYSTEM.exists(library / "Preferences"))
    }

    @Test
    fun theAppGroupCleanerAcceptsAMissingContainer() = runTest(dispatcher) {
        AppGroupSessionDataCleaner(directory, dispatchers).clearSessionData()

        assertFalse(FileSystem.SYSTEM.exists(directory.root))
    }

    @Test
    fun theWebsiteDataCleanerReturnsOnlyOnceWebKitHasCleared() = runTest(dispatcher) {
        val clearer = DeferredClearer()

        val cleaning = async { WebsiteDataSessionDataCleaner(clearer, dispatchers).clearSessionData() }
        runCurrent()

        assertEquals(1, clearer.calls)
        assertFalse(cleaning.isCompleted)
        clearer.complete()
        runCurrent()
        assertTrue(cleaning.isCompleted)
    }

    /** WebKit's removal, which answers later on the main queue. */
    private class DeferredClearer : WebsiteDataClearer {
        var calls = 0
        private var completion: (() -> Unit)? = null

        override fun clearWebsiteData(completion: () -> Unit) {
            calls++
            this.completion = completion
        }

        fun complete() = checkNotNull(completion).invoke()
    }

}
