package dev.alllexey.itmowidgets.feature.map.data.mirror

import dev.alllexey.itmowidgets.feature.map.domain.catalog.MapBuilding
import dev.alllexey.itmowidgets.feature.map.domain.catalog.MapPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `manifest.json` of the mirror (srvscripts `stacks/itmomap/mirror/sync.py`): schema 1, content-addressed files
 * relative to the manifest, the glyphs template relative to it too.
 */
@Serializable
internal class MirrorManifest(
    val schema: Int,
    val attribution: String = "",
    val glyphs: String,
    val buildings: List<Building>,
) {
    @Serializable
    class Building(
        val id: Int,
        val name: String,
        val bounds: List<Double>,
        val center: List<Double>,
        val bearing: Double = 0.0,
        val levels: List<Int>,
        val tiles: MirrorFile,
        val graph: MirrorFile,
        val rooms: MirrorFile,
        val removed: Boolean = false,
    ) {
        fun toModel() = MapBuilding(id, name, bounds, MapPoint(center[0], center[1]), bearing, levels.sorted())
    }

    companion object {
        const val SCHEMA = 1
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String): MirrorManifest = json.decodeFromString(text)
    }
}

/** A file of the mirror: its [file] path relative to the manifest and the [sha256] of its bytes. */
@Serializable
internal class MirrorFile(val file: String, val sha256: String, val bytes: Long = 0)
