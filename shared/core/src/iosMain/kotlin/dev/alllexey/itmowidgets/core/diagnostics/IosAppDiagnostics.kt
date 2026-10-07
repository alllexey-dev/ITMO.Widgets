package dev.alllexey.itmowidgets.core.diagnostics

import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import kotlin.experimental.ExperimentalNativeApi
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The iOS [AppDiagnostics]: the newest [MAX_ENTRIES] records of this process, newest first, each also written to
 * [log]. Warnings and errors last for the launch; a Kotlin crash ([recordCrash], from [IosCrashHook]) is also written
 * to [crashFile] at once, since the process ends right after, and the next launch shows it first and deletes the file.
 *
 * Android's journal sanitises messages and stack traces with `DiagnosticSanitizer`, which is still in `:app`. Until
 * it is shared, an error contributes only its type here: an exception message can carry a token. A crash keeps its
 * type and its stack frames, which name code, never data.
 */
class IosAppDiagnostics(
    private val clock: Clock,
    private val log: AppLog,
    private val crashFile: AtomicTextFile? = null
) : AppDiagnostics {

    private val entries = MutableStateFlow(listOfNotNull(restoreCrash()))

    override fun warn(tag: String, message: String, error: Throwable?) =
        record(DiagnosticLevel.WARNING, tag, message, error)

    override fun error(tag: String, message: String, error: Throwable?) =
        record(DiagnosticLevel.ERROR, tag, message, error)

    override fun observe(): Flow<List<DiagnosticEntry>> = entries

    override suspend fun clear() {
        entries.value = emptyList()
    }

    /**
     * An exception nothing caught: recorded and saved for the next launch before the runtime ends the process.
     * Never throws, so the hook always reaches the runtime's own termination.
     */
    @OptIn(ExperimentalNativeApi::class)
    fun recordCrash(error: Throwable) {
        val entry = DiagnosticEntry(
            at = clock.now(),
            level = DiagnosticLevel.CRASH,
            tag = CRASH_TAG,
            message = error::class.simpleName ?: UNKNOWN_TYPE,
            stackTrace = error.getStackTrace().joinToString("\n").ifBlank { null }
        )
        log.error(CRASH_TAG, entry.message)
        entries.update { current -> (listOf(entry) + current).take(MAX_ENTRIES) }
        try {
            crashFile?.write(json.encodeToString(StoredCrash.serializer(), StoredCrash.of(entry)))
        } catch (failure: Throwable) {
            log.error(CRASH_TAG, "Could not save the crash: ${failure::class.simpleName}")
        }
    }

    private fun record(level: DiagnosticLevel, tag: String, message: String, error: Throwable?) {
        val text = if (error == null) message else "$message: ${error::class.simpleName}"
        when (level) {
            DiagnosticLevel.WARNING -> log.warn(tag, text)
            else -> log.error(tag, text)
        }
        val entry = DiagnosticEntry(at = clock.now(), level = level, tag = tag, message = text, stackTrace = null)
        entries.update { current -> (listOf(entry) + current).take(MAX_ENTRIES) }
    }

    /** The crash the previous launch saved, once: the file goes as soon as it is read. */
    private fun restoreCrash(): DiagnosticEntry? {
        val file = crashFile ?: return null
        return try {
            val text = file.read() ?: return null
            file.write(null)
            json.decodeFromString(StoredCrash.serializer(), text).toEntry()
        } catch (_: SerializationException) {
            log.warn(CRASH_TAG, "Dropped an unreadable saved crash")
            null
        } catch (_: IllegalArgumentException) {
            log.warn(CRASH_TAG, "Dropped an unreadable saved crash")
            null
        } catch (failure: Exception) {
            log.warn(CRASH_TAG, "Could not read the saved crash: ${failure::class.simpleName}")
            null
        }
    }

    /** The saved crash in [crashFile]; a reader drops a file of a newer [version]. */
    @Serializable
    private data class StoredCrash(
        val version: Int,
        val atEpochMillis: Long,
        val message: String,
        val stackTrace: String?
    ) {
        fun toEntry(): DiagnosticEntry? {
            if (version > VERSION) return null
            return DiagnosticEntry(
                at = Instant.fromEpochMilliseconds(atEpochMillis),
                level = DiagnosticLevel.CRASH,
                tag = CRASH_TAG,
                message = message,
                stackTrace = stackTrace
            )
        }

        companion object {
            const val VERSION = 1

            fun of(entry: DiagnosticEntry) = StoredCrash(
                version = VERSION,
                atEpochMillis = entry.at.toEpochMilliseconds(),
                message = entry.message,
                stackTrace = entry.stackTrace
            )
        }
    }

    companion object {
        /** As Android's journal. */
        const val MAX_ENTRIES = 200

        /** The saved crash in the app's no-backup directory (`iosCoreModule`). */
        const val CRASH_FILE = "diagnostics-crash-v1.json"

        private const val CRASH_TAG = "Crash"
        private const val UNKNOWN_TYPE = "Throwable"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
