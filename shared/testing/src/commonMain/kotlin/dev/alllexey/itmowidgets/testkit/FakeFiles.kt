package dev.alllexey.itmowidgets.testkit

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.time.Clock

/**
 * A [FakeFileSystem] on [clock] that already holds [files] (path to UTF-8 text), parent directories included.
 * Call `checkNoOpenFiles()` on it in `@AfterTest`.
 */
fun fakeFileSystemOf(vararg files: Pair<String, String>, clock: Clock = Clock.System): FakeFileSystem =
    FakeFileSystem(clock).apply {
        files.forEach { (name, text) ->
            val path = name.toPath()
            path.parent?.let { createDirectories(it) }
            write(path) { writeUtf8(text) }
        }
    }
