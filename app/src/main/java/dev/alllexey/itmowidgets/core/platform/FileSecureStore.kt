package dev.alllexey.itmowidgets.core.platform

import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import java.util.concurrent.ConcurrentHashMap
import okio.FileSystem
import okio.Path

/**
 * Android's [SecureStore], the token files of 2.2: `<directory>/<name>` holds the [cipher]'s sealed text, replaced as
 * a whole through [AtomicTextFile]. The app passes `noBackupFilesDir` and the Keystore cipher, so the files 2.2 left
 * read as they are and nothing migrates.
 *
 * `MyItmoStorage` stays the only writer of `myitmo_tokens.enc`; a second writer of one file would race its cache.
 */
class FileSecureStore(
    private val directory: Path,
    private val cipher: TokenCipher,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : SecureStore {

    /** One [AtomicTextFile] per name, so its lock serialises every access to that file in the process. */
    private val files = ConcurrentHashMap<String, AtomicTextFile>()

    override fun read(name: String): String? = file(name).read()?.let(cipher::decrypt)

    override fun write(name: String, value: String) = file(name).write(cipher.encrypt(value))

    override fun delete(name: String) = file(name).write(null)

    private fun file(name: String): AtomicTextFile {
        require(name.isNotEmpty() && '/' !in name && name != "." && name != "..") { "Not a plain file name: '$name'" }
        return files.computeIfAbsent(name) { AtomicTextFile(directory / it, fileSystem) }
    }
}
