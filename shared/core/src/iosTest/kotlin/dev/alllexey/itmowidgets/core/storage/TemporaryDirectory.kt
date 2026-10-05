package dev.alllexey.itmowidgets.core.storage

import kotlin.random.Random
import okio.FileSystem
import okio.Path

/** A fresh directory under the simulator's temporary directory for one test; [delete] removes it. */
class TemporaryDirectory {
    val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-core-test-${Random.nextLong().toULong()}"

    init {
        FileSystem.SYSTEM.createDirectories(root)
    }

    fun delete() = FileSystem.SYSTEM.deleteRecursively(root)
}
