package dev.alllexey.itmowidgets.feature.qr.ui.rendering

import android.graphics.Bitmap
import androidx.core.graphics.scale
import dev.alllexey.itmowidgets.core.qr.CustomSpoilerManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Bounded, process-local sample images. Never retains an Activity or a real access pass. */
@Singleton
class QrPreviewBitmapCache @Inject constructor(
    private val generator: QrCodeGenerator,
    private val renderer: QrBitmapRenderer,
    private val spoilers: CustomSpoilerManager
) {
    private data class Key(val palette: Pair<Int, Int>, val revision: Long, val generation: Long)
    private val images = LinkedHashMap<Key, Pair<Bitmap, Bitmap>>(4, 0.75f, true)
    private val renderMutex = Mutex()
    private var generation = 0L

    @Synchronized
    fun cached(palette: Pair<Int, Int>): Pair<Bitmap, Bitmap>? = images[key(palette)]

    @Synchronized
    fun invalidate() {
        generation++
        images.clear()
    }

    suspend fun load(palette: Pair<Int, Int>): Pair<Bitmap, Bitmap> = renderMutex.withLock {
        cached(palette)?.let { return@withLock it }
        while (true) {
            val key = key(palette)
            val custom = withContext(Dispatchers.IO) { spoilers.getCustomSpoilerBitmap() }
            val rendered = withContext(Dispatchers.Default) {
                val qr = renderer.render(
                    generator.toBooleans(generator.generate("WIDGET PREVIEW")),
                    420, palette.first, palette.second, 0.05f, 0.06f
                )
                val cover = custom?.scale(420, 420) ?: renderer.renderNoise(
                    420, palette.first, palette.second, 0.05f, 0.06f
                )
                qr to cover
            }
            val accepted = synchronized(this) {
                if (key != key(palette)) false else {
                    images[key] = rendered
                    while (images.size > 4) images.remove(images.keys.first())
                    true
                }
            }
            if (accepted) return@withLock rendered
        }
        @Suppress("UNREACHABLE_CODE")
        error("Unreachable")
    }

    @Synchronized
    private fun key(palette: Pair<Int, Int>) = Key(palette, spoilers.revision, generation)
}
