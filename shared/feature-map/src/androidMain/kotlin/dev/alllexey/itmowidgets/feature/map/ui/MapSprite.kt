package dev.alllexey.itmowidgets.feature.map.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp

/** One of the campus app's map icons (`CampusMapStyle.SPRITES`) at [size], as itmo-map-web shows them in its lists. */
@Composable
internal fun MapSprite(name: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image = remember(name) { sprite(context, name) }
    Image(image, contentDescription = null, modifier.size(size))
}

/**
 * Read in place, as `painterResource` reads a drawable: a 72 px PNG of a few KB from the APK's assets, once per
 * process, so the first frame (and every screenshot) has its icon.
 */
private fun sprite(context: Context, name: String): ImageBitmap = cache.getOrPut(name) {
    context.assets.open(mapSpritePath(name)).use(BitmapFactory::decodeStream).asImageBitmap()
}

/** The asset of one of `CampusMapStyle.SPRITES`; the iOS bundle has the same `map_sprites` folder. */
internal fun mapSpritePath(name: String): String = "map_sprites/$name.png"

// 36 icons at most; read and written on the main thread only.
private val cache = HashMap<String, ImageBitmap>()
