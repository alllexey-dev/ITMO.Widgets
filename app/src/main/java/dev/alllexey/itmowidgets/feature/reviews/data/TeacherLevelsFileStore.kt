package dev.alllexey.itmowidgets.feature.reviews.data

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

/** 1: Backend's answers keyed by teacher ISU. */
private const val FORMAT = 1

/** [level] is null when Backend had no level for the teacher; that answer is remembered too. */
internal data class StoredLevel(val level: String?, val fetchedAt: Long)

internal data class StoredLevels(val format: Int = FORMAT, val entries: Map<String, StoredLevel> = emptyMap())

/** Teacher tones in `filesDir`, cleared with the session. Caller owns IO dispatch and serialization. */
class TeacherLevelsFileStore internal constructor(private val directory: File, private val gson: Gson) {
    @Inject constructor(@ApplicationContext context: Context, gson: Gson) : this(File(context.filesDir, "teacher_levels"), gson)

    private val file get() = File(directory, "levels.json")

    /** Throws on a corrupt file or one of another format. */
    internal fun read(): Map<Int, StoredLevel> {
        if (!file.exists()) return emptyMap()
        val state = checkNotNull(gson.fromJson(file.readText(), StoredLevels::class.java))
        check(state.format == FORMAT) { "Unknown teacher levels format ${state.format}" }
        return checkNotNull(state.entries).entries.associate { (key, entry) ->
            val isu = key.toInt()
            check(isu > 0) { "A stored level has no teacher" }
            checkNotNull(entry)
            entry.level?.let(TeacherLevel::valueOf)
            isu to entry
        }
    }

    internal fun write(levels: Map<Int, StoredLevel>) {
        check(directory.isDirectory || directory.mkdirs())
        val state = StoredLevels(entries = levels.mapKeys { (isu, _) -> isu.toString() })
        val temporary = File(directory, "levels.json.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(gson.toJson(state).toByteArray(Charsets.UTF_8)); stream.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    internal fun clear() { check(!directory.exists() || directory.deleteRecursively()) }
}
