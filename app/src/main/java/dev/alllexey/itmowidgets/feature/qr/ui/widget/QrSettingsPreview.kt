package dev.alllexey.itmowidgets.feature.qr.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.databinding.ViewWidgetPreviewQrBinding
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrColorResolver
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrPreviewBitmapCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Uses the widget's bitmap/transition renderers, but never loads a real access pass. */
class QrSettingsPreview(
    private val context: Context,
    private val scope: CoroutineScope,
    private val images: QrPreviewBitmapCache,
    private val colors: QrColorResolver
) : WidgetPreview {

    private val binding = ViewWidgetPreviewQrBinding.inflate(LayoutInflater.from(context))
    override val view: View = binding.root
    private val image = binding.qrPreviewWidget.qrCodeImage
    private var appearance: QrWidgetSettings? = null
    private var bitmaps: Pair<Bitmap, Bitmap>? = null
    private var imageJob: Job? = null
    private var animator: ValueAnimator? = null
    private var revealed = false
    private val ready = MutableStateFlow(false)

    init {
        image.setOnClickListener { toggleSpoiler() }
    }

    override fun bind(settings: WidgetPreviewSettings) {
        val next = (settings as WidgetPreviewSettings.Qr).appearance
        if (appearance == next) return
        val previous = appearance
        appearance = next
        stop()
        revealed = !next.spoilerEnabled
        if (bitmaps == null || previous?.dynamicColors != next.dynamicColors) {
            loadImages()
        } else {
            showRestingImage()
            if (previous?.animationType != next.animationType && next.spoilerEnabled) toggleSpoiler()
        }
    }

    override fun refresh() {
        if (appearance == null) return
        stop()
        revealed = appearance?.spoilerEnabled == false
        images.invalidate()
        loadImages()
    }

    override suspend fun awaitReady() {
        ready.first { it }
    }

    private fun loadImages() {
        val options = appearance ?: return
        imageJob?.cancel()
        val palette = colors.getQrColors(context, options.dynamicColors)
        images.cached(palette)?.let { cached ->
            bitmaps = cached
            ready.value = true
            showRestingImage()
            return
        }
        imageJob = scope.launch {
            try {
                bitmaps = images.load(palette)
                showRestingImage()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                binding.qrPreviewHint.setText(R.string.widget_preview_failed)
            }
            ready.value = true
        }
    }

    private fun toggleSpoiler() {
        val options = appearance ?: return
        val (qr, cover) = bitmaps ?: return
        if (!options.spoilerEnabled || animator?.isRunning == true) return
        val from = if (revealed) qr else cover
        revealed = !revealed
        val to = if (revealed) qr else cover
        updateHint()
        val animation = QrWidgetAnimation.of(options.animationType, from, to)
        if (animation == null || !ValueAnimator.areAnimatorsEnabled()) {
            image.setImageBitmap(to)
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { value ->
                image.setImageBitmap(animation.frame(QrWidgetAnimation.ease(value.animatedValue as Float)))
            }
            start()
        }
    }

    private fun showRestingImage() {
        bitmaps?.let { (qr, cover) -> image.setImageBitmap(if (revealed) qr else cover) }
        image.isClickable = appearance?.spoilerEnabled == true
        image.isFocusable = image.isClickable
        updateHint()
    }

    private fun updateHint() {
        val text = when {
            appearance?.spoilerEnabled == false -> R.string.widget_preview_qr_plain_hint
            revealed -> R.string.widget_preview_qr_open_hint
            else -> R.string.widget_preview_qr_hint
        }
        binding.qrPreviewHint.setText(R.string.widget_preview_qr_plain_hint)
        image.contentDescription = context.getString(text)
    }

    override fun stop() {
        animator?.cancel()
        animator?.removeAllUpdateListeners()
        animator = null
        showRestingImage()
    }

    override fun close() {
        stop()
        imageJob?.cancel()
        image.setImageDrawable(null)
        bitmaps = null
    }
}
