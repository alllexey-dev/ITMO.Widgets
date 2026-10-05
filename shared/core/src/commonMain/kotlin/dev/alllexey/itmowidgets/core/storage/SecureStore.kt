package dev.alllexey.itmowidgets.core.storage

/**
 * Secrets of the app (session tokens, the BARS header) by name, kept where no backup or other app reads them.
 *
 * Android keeps each one as `noBackupFilesDir/<name>`, sealed by the Keystore [TokenCipher] and replaced as a whole,
 * byte-compatible with the files 2.2 wrote (`myitmo_tokens.enc`, `bars_tokens.enc`); iOS keeps a Keychain item of
 * that name. A name is a plain file name: no path separators.
 *
 * A read of a stored value that cannot be decrypted throws, so the caller decides whether to [delete] it; nothing
 * here logs a value.
 */
interface SecureStore {

    /** The value stored under [name], or null when there is none. */
    fun read(name: String): String?

    /** Replaces the value under [name] as a whole; a reader never sees a partial value. */
    fun write(name: String, value: String)

    /** Removes the value under [name]; nothing happens when there is none. */
    fun delete(name: String)
}
