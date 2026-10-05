package dev.alllexey.itmowidgets.core.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * One JSON object per line in `files/diagnostics/log.jsonl`, appended by a single
 * writer. Crashes go to a separate file synchronously, because the writer's
 * coroutine may be dead by then, and are merged on the next start.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FileAppDiagnostics internal constructor(
    private val directory: File,
    private val clock: Clock,
    dispatchers: AppDispatchers,
    private val log: AppLog,
) : AppDiagnostics {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        clock: Clock,
        dispatchers: AppDispatchers,
        log: AppLog,
    ) : this(File(context.filesDir, "diagnostics"), clock, dispatchers, log)

    private val logFile = File(directory, "log.jsonl")
    private val crashFile = File(directory, "pending_crash.jsonl")
    private val writer = dispatchers.io.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + writer)
    private val fileLock = Any()

    /** Null until the file has been read once; readers wait instead of seeing an empty flash. */
    private val entries = MutableStateFlow<List<DiagnosticEntry>?>(null)

    override fun warn(tag: String, message: String, error: Throwable?) = record(DiagnosticLevel.WARNING, tag, message, error)

    override fun error(tag: String, message: String, error: Throwable?) = record(DiagnosticLevel.ERROR, tag, message, error)

    override fun observe(): Flow<List<DiagnosticEntry>> = flow {
        withContext(writer) { ensureLoaded() }
        emitAll(entries.filterNotNull())
    }

    override suspend fun clear() = withContext(writer) {
        synchronized(fileLock) { logFile.delete() }
        entries.value = emptyList()
    }

    /** Called from the uncaught-exception handler; must not touch coroutines. */
    fun recordCrash(threadName: String, error: Throwable) {
        val entry = DiagnosticEntry(
            at = clock.now(),
            level = DiagnosticLevel.CRASH,
            tag = "Crash: $threadName",
            message = DiagnosticSanitizer.sanitize(error.message?.let { "${error.javaClass.simpleName}: $it" } ?: error.javaClass.name),
            stackTrace = DiagnosticSanitizer.stackTrace(error)
        )
        synchronized(fileLock) {
            directory.mkdirs()
            crashFile.appendText(entry.toLine())
        }
    }

    fun importPendingCrashes() {
        scope.launch {
            ensureLoaded()
            val pending = synchronized(fileLock) {
                if (!crashFile.exists()) return@launch
                val lines = crashFile.readLines().mapNotNull(::parse)
                crashFile.delete()
                lines
            }
            pending.forEach { append(it) }
        }
    }

    /** Test hook: completes once every record submitted so far has reached the file. */
    internal suspend fun awaitWrites() = withContext(writer) { }

    private fun record(level: DiagnosticLevel, tag: String, message: String, error: Throwable?) {
        val entry = DiagnosticEntry(
            at = clock.now(),
            level = level,
            tag = tag,
            message = DiagnosticSanitizer.sanitize(message),
            stackTrace = error?.let(DiagnosticSanitizer::stackTrace)
        )
        if (BuildConfig.DEBUG) {
            if (level == DiagnosticLevel.WARNING) log.warn(tag, entry.message) else log.error(tag, entry.message)
        }
        scope.launch {
            ensureLoaded()
            append(entry)
        }
    }

    private fun ensureLoaded() {
        if (entries.value != null) return
        entries.value = synchronized(fileLock) {
            if (logFile.exists()) logFile.readLines().mapNotNull(::parse).asReversed() else emptyList()
        }
    }

    private fun append(entry: DiagnosticEntry) {
        val current = (listOf(entry) + entries.value.orEmpty()).take(MAX_ENTRIES)
        try {
            synchronized(fileLock) {
                directory.mkdirs()
                if (current.size < MAX_ENTRIES) {
                    logFile.appendText(entry.toLine())
                } else {
                    // Oldest first on disk; rewrite keeps the file bounded without a second index.
                    logFile.writeText(current.asReversed().joinToString("") { it.toLine() })
                }
            }
            entries.value = current
        } catch (error: Exception) {
            if (BuildConfig.DEBUG) log.warn(TAG, "Diagnostics write failed: ${error.javaClass.simpleName}")
        }
    }

    private fun parse(line: String): DiagnosticEntry? = try {
        JOURNAL_JSON.decodeFromString(StoredEntry.serializer(), line).toEntry()
    } catch (_: Exception) {
        null
    }

    /** The line 2.2 wrote with Gson: every field optional, a null one left out. */
    @Serializable
    private data class StoredEntry(
        val at: String? = null,
        val level: String? = null,
        val tag: String? = null,
        val message: String? = null,
        val stackTrace: String? = null
    ) {
        fun toEntry(): DiagnosticEntry? {
            val instant = at?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
            val level = DiagnosticLevel.entries.firstOrNull { it.name == level } ?: return null
            return DiagnosticEntry(instant, level, tag.orEmpty(), message.orEmpty(), stackTrace)
        }
    }

    private fun DiagnosticEntry.toLine(): String =
        JOURNAL_JSON.encodeToString(StoredEntry.serializer(), StoredEntry(at.toString(), level.name, tag, message, stackTrace)) + "\n"

    companion object {
        const val MAX_ENTRIES = 200
        private const val TAG = "Diagnostics"

        @OptIn(ExperimentalSerializationApi::class)
        private val JOURNAL_JSON = Json {
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    }
}
