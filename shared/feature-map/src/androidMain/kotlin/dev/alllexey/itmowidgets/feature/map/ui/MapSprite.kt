package dev.alllexey.itmowidgets.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import dev.alllexey.itmowidgets.shared.feature.map.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** One of the campus app's map icons (`CampusMapStyle.SPRITES`) at [size], as itmo-map-web shows them in its lists. */
@Composable
internal fun MapSprite(name: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap by produceState(cache[name], name) {
        value = cache[name] ?: withContext(Dispatchers.Default) {
            Res.readBytes("files/map_sprites/$name.png").decodeToImageBitmap()
        }.also { cache[name] = it }
    }
    val image = bitmap
    if (image == null) Spacer(modifier.size(size)) else Image(image, contentDescription = null, modifier.size(size))
}

// 36 icons of 72 px at most: decoded once per process, read and written on the main thread only.
private val cache = HashMap<String, ImageBitmap>()
