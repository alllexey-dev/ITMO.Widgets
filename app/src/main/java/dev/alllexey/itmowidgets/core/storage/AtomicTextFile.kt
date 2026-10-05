package dev.alllexey.itmowidgets.core.storage

import java.io.File
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toOkioPath

/**
 * A UTF-8 text file replaced as a whole: a write goes to `<name>.new`, is synced and then moved over the file.
 *
 * Up to 2.2 the same files were written by `android.util.AtomicFile`, and an interrupted write left one of two
 * states: below API 30 the valid data sits in `<name>.bak` beside a partial file; from API 30 a partial `<name>.new`
 * sits beside the valid file. A read restores and cleans up both exactly as `AtomicFile.openRead` does, so such a
 * leftover never costs the stored value (`myitmo_tokens.enc` included).
 */
class AtomicTextFile(
    private val path: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM
) {

    constructor(file: File) : this(file.toOkioPath())

    private val newPath = path.withSuffix(NEW_SUFFIX)
    private val legacyBackupPath = path.withSuffix(LEGACY_BACKUP_SUFFIX)
    private val lock = SynchronizedObject()

    @Throws(IOException::class)
    fun read(): String? = synchronized(lock) {
        if (!fileSystem.exists(path)) return null
        restoreLegacyBackup()
        // As in AtomicFile, a `.new` is dropped only beside an existing file.
        fileSystem.delete(newPath)
        fileSystem.read(path) { readUtf8() }
    }

    @Throws(IOException::class)
    fun write(value: String?): Unit = synchronized(lock) {
        if (value == null) {
            fileSystem.delete(path)
            fileSystem.delete(newPath)
            fileSystem.delete(legacyBackupPath)
            return
        }

        path.parent?.let(fileSystem::createDirectories)
        restoreLegacyBackup()
        try {
            val bytes = value.encodeToByteArray()
            fileSystem.openReadWrite(newPath).use { handle ->
                handle.resize(0)
                handle.write(0, bytes, 0, bytes.size)
                handle.flush()
            }
            fileSystem.atomicMove(newPath, path)
        } catch (error: Exception) {
            fileSystem.delete(newPath)
            throw error
        }
    }

    private fun restoreLegacyBackup() {
        if (fileSystem.exists(legacyBackupPath)) fileSystem.atomicMove(legacyBackupPath, path)
    }

    private fun Path.withSuffix(suffix: String): Path = checkNotNull(parent) { "$this has no parent" } / (name + suffix)

    private companion object {
        const val NEW_SUFFIX = ".new"
        const val LEGACY_BACKUP_SUFFIX = ".bak"
    }
}
