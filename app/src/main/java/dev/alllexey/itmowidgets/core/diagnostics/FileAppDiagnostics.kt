package dev.alllexey.itmowidgets.core.diagnostics

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.time.WallClock
import java.io.File
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
) : AppDiagnostics {

    @Inject
    constructor(@ApplicationContext context: Context, @WallClock clock: Clock) :
        this(File(context.filesDir, "diagnostics"), clock)

    private val logFile = File(directory, "log.jsonl")
    private val crashFile = File(directory, "pending_crash.jsonl")
    private val gson = Gson()
    private val writer = Dispatchers.IO.limitedParallelism(1)
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
            at = Instant.now(clock),
            level = DiagnosticLevel.CRASH,
            tag = "Crash: $threadName",
            message = DiagnosticSanitizer.sanitize(error.message?.let { "${error.javaClass.simpleName}: $it" } ?: error.javaClass.name),
            stackTrace = DiagnosticSanitizer.stackTrace(error)
        )
        synchronized(fileLock) {
            directory.mkdirs()
            crashFile.appendText(gson.toJson(entry.toStored()) + "\n")
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
            at = Instant.now(clock),
            level = level,
            tag = tag,
            message = DiagnosticSanitizer.sanitize(message),
            stackTrace = error?.let(DiagnosticSanitizer::stackTrace)
        )
        if (BuildConfig.DEBUG) Log.println(if (level == DiagnosticLevel.WARNING) Log.WARN else Log.ERROR, tag, entry.message)
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
                    logFile.appendText(gson.toJson(entry.toStored()) + "\n")
                } else {
                    // Oldest first on disk; rewrite keeps the file bounded without a second index.
                    logFile.writeText(current.asReversed().joinToString("") { gson.toJson(it.toStored()) + "\n" })
                }
            }
            entries.value = current
        } catch (error: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Diagnostics write failed: ${error.javaClass.simpleName}")
        }
    }

    private fun parse(line: String): DiagnosticEntry? = try {
        gson.fromJson(line, StoredEntry::class.java)?.toEntry()
    } catch (_: Exception) {
        null
    }

    private data class StoredEntry(
        val at: String?,
        val level: String?,
        val tag: String?,
        val message: String?,
        val stackTrace: String?
    ) {
        fun toEntry(): DiagnosticEntry? {
            val instant = at?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
            val level = DiagnosticLevel.entries.firstOrNull { it.name == level } ?: return null
            return DiagnosticEntry(instant, level, tag.orEmpty(), message.orEmpty(), stackTrace)
        }
    }

    private fun DiagnosticEntry.toStored() = StoredEntry(at.toString(), level.name, tag, message, stackTrace)

    companion object {
        const val MAX_ENTRIES = 200
        private const val TAG = "Diagnostics"
    }
}
