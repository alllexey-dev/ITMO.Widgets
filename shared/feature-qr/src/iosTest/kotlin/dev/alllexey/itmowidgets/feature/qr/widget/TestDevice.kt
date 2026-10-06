package dev.alllexey.itmowidgets.feature.qr.widget

import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import kotlin.random.Random
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSProcessInfo

/**
 * The simulator test binary's stand-ins for one test: the app's directories and the App Group container under a
 * fresh temporary directory (the binary has no App Group), removed by [delete].
 */
class TestDevice {
    val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-qr-test-${Random.nextLong().toULong()}"

    val directories: AppDirectories = object : AppDirectories {
        override val files = root / "files"
        override val cache = root / "caches"
        override val noBackup = root / "no-backup"
    }

    init {
        FileSystem.SYSTEM.createDirectories(root)
    }

    fun appGroup(log: AppLog): AppGroupDirectory = AppGroupDirectory.resolve(
        identifiers = BundleIdentifiers(appGroupId = "group.test.itmo", keychainGroup = null),
        appDirectories = directories,
        log = log,
        containerOf = { root / "group" },
    )

    fun delete() = FileSystem.SYSTEM.deleteRecursively(root)
}

/**
 * The repository checkout this test binary was built in: the simulator runs it from
 * `shared/<module>/build/bin/...` on the Mac's own file system, so the checkout is the nearest parent with
 * `iosApp/project.yml`.
 */
fun repositoryRoot(): Path {
    var directory: Path? = NSProcessInfo.processInfo.arguments.first().toString().toPath().parent
    while (directory != null) {
        if (FileSystem.SYSTEM.exists(directory / "iosApp" / "project.yml")) return directory
        directory = directory.parent
    }
    error("No repository above the test binary ${NSProcessInfo.processInfo.arguments.first()}")
}
