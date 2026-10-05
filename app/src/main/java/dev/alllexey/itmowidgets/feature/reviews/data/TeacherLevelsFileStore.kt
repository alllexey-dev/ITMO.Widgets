package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import javax.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/** 1: Backend's answers keyed by teacher ISU; 2.2 wrote the same with Gson. */
private const val FORMAT = 1

/**
 * The JSON of the reviews' files (recipe `kotlinx-file-store`): absent nullable keys read as null and nulls are not
 * written, as Gson did, and `format` is always written.
 */
@OptIn(ExperimentalSerializationApi::class)
internal val ReviewsStoreJson = Json {
    explicitNulls = false
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** [level] is null when Backend had no level for the teacher; that answer is remembered too. */
@Serializable
internal data class StoredLevel(val level: String? = null, val fetchedAt: Long)

@Serializable
internal data class StoredLevels(val format: Int = FORMAT, val entries: Map<String, StoredLevel> = emptyMap())

/** Teacher tones in `filesDir`, cleared with the session. Caller owns IO dispatch and serialization. */
class TeacherLevelsFileStore internal constructor(private val directory: Path) {
    @Inject constructor(directories: AppDirectories) : this(directories.files / "teacher_levels")

    private val file = AtomicTextFile(directory / "levels.json")

    /** Throws on a corrupt file or one of another format. */
    internal fun read(): Map<Int, StoredLevel> {
        val text = file.read() ?: return emptyMap()
        val state = ReviewsStoreJson.decodeFromString(StoredLevels.serializer(), text)
        check(state.format == FORMAT) { "Unknown teacher levels format ${state.format}" }
        return state.entries.entries.associate { (key, entry) ->
            val isu = key.toInt()
            check(isu > 0) { "A stored level has no teacher" }
            entry.level?.let(TeacherLevel::valueOf)
            isu to entry
        }
    }

    internal fun write(levels: Map<Int, StoredLevel>) {
        val state = StoredLevels(entries = levels.mapKeys { (isu, _) -> isu.toString() })
        file.write(ReviewsStoreJson.encodeToString(StoredLevels.serializer(), state))
    }

    internal fun clear() = FileSystem.SYSTEM.deleteRecursively(directory)
}
