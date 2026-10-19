package dev.alllexey.itmowidgets.feature.map.data

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.feature.map.data.graph.TransitionJson
import dev.alllexey.itmowidgets.feature.map.data.mirror.CampusMapApi
import dev.alllexey.itmowidgets.feature.map.data.mirror.MirrorException
import dev.alllexey.itmowidgets.feature.map.data.mirror.MirrorFile
import dev.alllexey.itmowidgets.feature.map.data.mirror.MirrorManifest
import dev.alllexey.itmowidgets.feature.map.data.rooms.RoomsJson
import dev.alllexey.itmowidgets.feature.map.domain.CampusMapRepository
import dev.alllexey.itmowidgets.feature.map.domain.catalog.LoadedPlan
import dev.alllexey.itmowidgets.feature.map.domain.catalog.MapCatalog
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import dev.alllexey.itmowidgets.feature.map.domain.trip.BuildingPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * The mirror's plans on the device, under `cache/campus_map`: the last manifest and the files it names, each checked
 * against its sha256. The manifest is fetched once per process and replaced only by a valid one; files are
 * immutable, so a stored file is never fetched again, and the files of older manifests are deleted. Map data is the
 * same for everyone: nothing here belongs to the session.
 */
class CampusMapRepositoryImpl internal constructor(
    private val api: CampusMapApi,
    private val root: Path,
    private val fileSystem: FileSystem,
    private val dispatchers: AppDispatchers,
) : CampusMapRepository {

    constructor(api: CampusMapApi, directories: AppDirectories, dispatchers: AppDispatchers) :
        this(api, directories.cache / DIRECTORY, MapFileSystem, dispatchers)

    private val lock = Mutex()
    private var manifest: MirrorManifest? = null
    private var fresh = false
    private val plans = HashMap<String, LoadedPlan>()

    override suspend fun catalog(): AppResult<MapCatalog> = load { catalogOf(manifest()) }

    override suspend fun plan(buildingId: Int): AppResult<LoadedPlan> = load {
        val manifest = manifest()
        val entry = manifest.buildings.firstOrNull { it.id == buildingId && !it.removed } ?: throw MirrorException(404)
        val key = "${entry.tiles.sha256}:${entry.graph.sha256}:${entry.rooms.sha256}"
        lock.withLock { plans[key] }?.let { return@load it }
        val tiles = stored(entry.tiles)
        val graph = stored(entry.graph)
        val rooms = stored(entry.rooms)
        val plan = withContext(dispatchers.default) {
            BuildingPlan(
                buildingId,
                RoomsJson.decode(buildingId, read(rooms)),
                TransitionJson.decode(read(graph)),
            )
        }
        LoadedPlan(entry.toModel(), tiles.toString(), plan).also { lock.withLock { plans[key] = it } }
    }

    override suspend fun allRooms(): AppResult<Map<Int, List<MapRoom>>> = load {
        manifest().buildings.filterNot { it.removed }.associate { entry ->
            val path = stored(entry.rooms)
            entry.id to withContext(dispatchers.default) { RoomsJson.decode(entry.id, read(path)) }
        }
    }

    private suspend fun <T> load(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: MirrorException) {
        AppResult.Failure(if (error.status == 404) AppError.NotFound else AppError.Network)
    } catch (error: IOException) {
        AppResult.Failure(AppError.Unknown(error))
    } catch (error: SerializationException) {
        AppResult.Failure(AppError.Unknown(error))
    } catch (error: IllegalArgumentException) {
        AppResult.Failure(AppError.Unknown(error))
    }

    /** The manifest of this process: fetched once, else the stored one; a failed fetch is retried on the next call. */
    private suspend fun manifest(): MirrorManifest {
        lock.withLock { if (fresh) return manifest!! }
        val fetched = try {
            api.manifest().let { text -> MirrorManifest.decode(text).takeIf { it.schema == MirrorManifest.SCHEMA }?.let { text to it } }
        } catch (error: MirrorException) {
            null
        } catch (error: SerializationException) {
            null
        }
        return lock.withLock {
            if (fetched != null) {
                val (text, parsed) = fetched
                withContext(dispatchers.io) {
                    writeAtomically(root / MANIFEST, text.encodeToByteArray())
                    prune(parsed)
                }
                manifest = parsed
                fresh = true
                parsed
            } else {
                manifest ?: withContext(dispatchers.io) { storedManifest() }?.also { manifest = it }
                    ?: throw MirrorException(null)
            }
        }
    }

    private fun storedManifest(): MirrorManifest? {
        val path = root / MANIFEST
        if (!fileSystem.exists(path)) return null
        return try {
            MirrorManifest.decode(fileSystem.read(path) { readUtf8() })
        } catch (error: SerializationException) {
            null
        }
    }

    /** The local copy of [file], downloaded and checked first when missing. */
    private suspend fun stored(file: MirrorFile): Path {
        val path = root / FILES / file.file.substringAfterLast('/')
        if (withContext(dispatchers.io) { fileSystem.exists(path) }) return path
        val bytes = api.file(file.file)
        // A building's tiles are megabytes: hashing them on the main thread froze the screen.
        val hash = withContext(dispatchers.default) { bytes.toByteString().sha256().hex() }
        if (hash != file.sha256) throw MirrorException(null)
        withContext(dispatchers.io) { writeAtomically(path, bytes) }
        return path
    }

    private suspend fun read(path: Path): String = withContext(dispatchers.io) { fileSystem.read(path) { readUtf8() } }

    private fun writeAtomically(path: Path, bytes: ByteArray) {
        path.parent?.let(fileSystem::createDirectories)
        val temporary = path.parent!! / "${path.name}.tmp"
        fileSystem.write(temporary) { write(bytes) }
        fileSystem.atomicMove(temporary, path)
    }

    private fun prune(current: MirrorManifest) {
        val directory = root / FILES
        if (!fileSystem.exists(directory)) return
        val keep = current.buildings.flatMap { listOf(it.tiles, it.graph, it.rooms) }.mapTo(HashSet()) { it.file.substringAfterLast('/') }
        fileSystem.list(directory).filterNot { it.name in keep }.forEach(fileSystem::delete)
    }

    private fun catalogOf(manifest: MirrorManifest) = MapCatalog(
        buildings = manifest.buildings.filterNot { it.removed }.map { it.toModel() },
        attribution = manifest.attribution,
        glyphsUrl = api.resolve(manifest.glyphs),
    )

    private companion object {
        const val DIRECTORY = "campus_map"
        const val FILES = "files"
        const val MANIFEST = "manifest.json"
    }
}
