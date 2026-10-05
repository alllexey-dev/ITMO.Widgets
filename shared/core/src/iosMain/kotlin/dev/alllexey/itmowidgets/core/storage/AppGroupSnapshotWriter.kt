package dev.alllexey.itmowidgets.core.storage

import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Asks WidgetKit to reload the timelines of one widget kind. Swift implements it (IO-05's `IosPlatform`):
 * WidgetKit has no Objective-C API that Kotlin could call.
 */
fun interface WidgetReloader {
    fun reload(kind: String)
}

/**
 * One versioned JSON file in the App Group container, `<name>-v<version>.json`, holding
 * `{"version": <version>, "value": <value>}`. A reader rejects a file whose `version` is higher than its own, so an
 * older extension never misreads what a newer app wrote.
 */
class SnapshotFile<T>(
    val name: String,
    val version: Int,
    val serializer: KSerializer<T>
) {
    init {
        require(NAME.matches(name)) { "'$name' is not a snapshot name" }
        require(version >= 1) { "A snapshot version starts at 1" }
    }

    val fileName: String
        get() = "$name-v$version.json"

    private companion object {
        val NAME = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    }
}

/**
 * Writes what the Swift-only widget extension and the notification service read: the file is replaced as a whole
 * (written beside it, then renamed over it), so a reader in another process sees the old or the new snapshot,
 * never a partial one; then each given widget kind is reloaded. Holds no lock: a write never suspends.
 */
class AppGroupSnapshotWriter(
    private val directory: AppGroupDirectory,
    private val reloader: WidgetReloader,
    private val json: Json = Json,
    private val fileSystem: FileSystem = SystemFileSystem
) {

    @Throws(IOException::class)
    fun <T> write(file: SnapshotFile<T>, value: T, reloadKinds: Collection<String> = emptyList()) {
        val envelope = buildJsonObject {
            put(VERSION, file.version)
            put(VALUE, json.encodeToJsonElement(file.serializer, value))
        }
        replace(directory.file(file.fileName), json.encodeToString(JsonObject.serializer(), envelope))
        reloadKinds.forEach(reloader::reload)
    }

    /** The snapshot as this build reads it: null when missing, unreadable or written by a newer version. */
    fun <T> read(file: SnapshotFile<T>): T? = readSnapshot(directory, file, json, fileSystem)

    /**
     * A temporary file of its own per write, so writers in two processes never share one; unlike [AtomicTextFile]
     * a reader never deletes another process's temporary file.
     */
    private fun replace(target: Path, text: String) {
        fileSystem.createDirectories(directory.root)
        val temporary = directory.file(".${target.name}.${Random.nextLong().toULong().toString(RADIX)}.tmp")
        try {
            fileSystem.write(temporary, mustCreate = true) { writeUtf8(text) }
            fileSystem.atomicMove(temporary, target)
        } catch (failure: Throwable) {
            fileSystem.delete(temporary)
            throw failure
        }
    }

    internal companion object {
        const val VERSION = "version"
        const val VALUE = "value"
        const val RADIX = 36
    }
}

internal fun <T> readSnapshot(
    directory: AppGroupDirectory,
    file: SnapshotFile<T>,
    json: Json = Json,
    fileSystem: FileSystem = SystemFileSystem
): T? {
    val path = directory.file(file.fileName)
    val text = try {
        fileSystem.read(path) { readUtf8() }
    } catch (_: IOException) {
        return null
    }
    return try {
        val envelope = json.parseToJsonElement(text).jsonObject
        val version = envelope.getValue(AppGroupSnapshotWriter.VERSION).jsonPrimitive.int
        if (version > file.version) return null
        json.decodeFromJsonElement(file.serializer, envelope.getValue(AppGroupSnapshotWriter.VALUE))
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: NoSuchElementException) {
        null
    }
}
