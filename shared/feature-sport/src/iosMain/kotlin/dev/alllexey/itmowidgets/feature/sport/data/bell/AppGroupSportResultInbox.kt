package dev.alllexey.itmowidgets.feature.sport.data.bell

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * The notification service's queue results in the App Group: one `sport-results/<lesson>-<millis>.json` per result,
 * a [SportResult] written to a temporary name and moved in place, so a file the app lists is always whole. [take]
 * reads and deletes every one; a file that does not decode is deleted too. Sign-out drops what is left.
 */
class AppGroupSportResultInbox internal constructor(
    private val directory: Path,
    private val dispatchers: AppDispatchers,
    private val fileSystem: FileSystem,
) : SportResultInbox, SessionDataCleaner {

    constructor(group: AppGroupDirectory, dispatchers: AppDispatchers) :
        this(group.root / DIRECTORY, dispatchers, FileSystem.SYSTEM)

    override suspend fun take(): List<SportResult> = withContext(dispatchers.io) {
        files().mapNotNull { file ->
            val result = try {
                JSON.decodeFromString<SportResult>(fileSystem.read(file) { readUtf8() })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            fileSystem.delete(file)
            result
        }
    }

    override suspend fun clearSessionData() = withContext(dispatchers.io) {
        files().forEach(fileSystem::delete)
    }

    private fun files(): List<Path> =
        fileSystem.listOrNull(directory).orEmpty().filter { it.name.endsWith(SUFFIX) }

    internal companion object {
        /** The directory and the suffix the notification service writes (`SportResultInbox.swift`). */
        const val DIRECTORY = "sport-results"
        const val SUFFIX = ".json"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
