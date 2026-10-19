package dev.alllexey.itmowidgets.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import dev.alllexey.itmowidgets.shared.feature.map.Res
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.decodeToImageBitmap

/** One of the campus app's map icons (`CampusMapStyle.SPRITES`) at [size], as itmo-map-web shows them in its lists. */
@Composable
internal fun MapSprite(name: String, size: Dp, modifier: Modifier = Modifier) {
    val image = remember(name) { sprite(name) }
    Image(image, contentDescription = null, modifier.size(size))
}

/**
 * Read in place, as Compose resources' own `painterResource` reads a drawable on Android: a 72 px PNG of a few KB
 * from the APK's assets, once per process, so the first frame (and every screenshot) has its icon.
 */
private fun sprite(name: String): ImageBitmap = cache.getOrPut(name) {
    runBlocking { Res.readBytes("files/map_sprites/$name.png") }.decodeToImageBitmap()
}

// 36 icons at most; read and written on the main thread only.
private val cache = HashMap<String, ImageBitmap>()
