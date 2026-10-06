package dev.alllexey.itmowidgets.feature.recordbook.data

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals

/** A file 2.2 wrote, from `src/androidHostTest/resources/stores/` (see its README). */
internal fun stored22(path: String): ByteArray =
    checkNotNull(object {}.javaClass.getResourceAsStream("/stores/$path")) { "No golden $path" }.use { it.readBytes() }

/** Copies the 2.2 file at [path] into [directory] under [name]. */
internal fun copyStored22(path: String, directory: File, name: String = File(path).name): File =
    File(directory.apply { mkdirs() }, name).apply { writeBytes(stored22(path)) }

/**
 * Gson ordered keys by the runtime's field order and escaped `=` and `&`; kotlinx orders them as declared. The trees
 * must still match: every key, string and number.
 */
internal fun assertSameJson(expected: String, actual: String) =
    assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual))
