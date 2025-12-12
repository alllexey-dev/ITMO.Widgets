package dev.alllexey.itmowidgets.ui.onboarding

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.ui.widgets.WidgetUtils
import dev.alllexey.itmowidgets.ui.widgets.data.QrAnimationType
import dev.alllexey.itmowidgets.workers.QrAnimationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QrSetupFragment : Fragment(R.layout.fragment_onboarding_qr) {

    private lateinit var qrImage: ImageView
    private val appContainer by lazy { (requireContext().applicationContext as ItmoWidgetsApp).appContainer }

    private val previewQrContent = "ABCD1234"

    private var animationJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        qrImage = view.findViewById(R.id.qr_preview_image)
        val animGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.anim_toggle_group)
        val spoilerSwitch = view.findViewById<MaterialSwitch>(R.id.switch_spoiler)
        val dynamicColorsSwitch = view.findViewById<MaterialSwitch>(R.id.switch_dynamic_colors)

        val settings = appContainer.storage.settings

        spoilerSwitch.isChecked = settings.getQrSpoilerState()
        dynamicColorsSwitch.isChecked = settings.getDynamicQrColorsState()

        if (settings.getQrSpoilerAnimationType() == QrAnimationType.FADE) {
            animGroup.check(R.id.toggle_anim_fade)
        } else {
            animGroup.check(R.id.toggle_anim_circle)
        }

        updatePreviewImage()

        animGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val type = if (checkedId == R.id.toggle_anim_fade) QrAnimationType.FADE else QrAnimationType.CIRCLE
                settings.setQrAnimationType(type)

                if (spoilerSwitch.isChecked) {
                    runAnimationPreview()
                }
                WidgetUtils.updateAllWidgets(requireContext())
            }
        }

        spoilerSwitch.setOnCheckedChangeListener { _, isChecked ->
            animationJob?.cancel()

            settings.setQrSpoilerState(isChecked)

            val animCircleBtn = view.findViewById<View>(R.id.toggle_anim_circle)
            val animFadeBtn = view.findViewById<View>(R.id.toggle_anim_fade)

            animCircleBtn.isEnabled = isChecked
            animCircleBtn.alpha = if (isChecked) 1f else 0.5f
            animFadeBtn.isEnabled = isChecked
            animFadeBtn.alpha = if (isChecked) 1f else 0.5f

            updatePreviewImage()
            WidgetUtils.updateAllWidgets(requireContext())
        }

        spoilerSwitch.jumpDrawablesToCurrentState()
        val initialAnimState = spoilerSwitch.isChecked
        view.findViewById<View>(R.id.toggle_anim_circle).apply { isEnabled = initialAnimState; alpha = if(initialAnimState) 1f else 0.5f }
        view.findViewById<View>(R.id.toggle_anim_fade).apply { isEnabled = initialAnimState; alpha = if(initialAnimState) 1f else 0.5f }

        dynamicColorsSwitch.setOnCheckedChangeListener { _, isChecked ->
            animationJob?.cancel()

            settings.setDynamicQrColorsState(isChecked)

            appContainer.qrToolkit.bitmapCache.clearCache()

            updatePreviewImage()
            WidgetUtils.updateAllWidgets(requireContext())
        }

        qrImage.setOnClickListener {
            if (spoilerSwitch.isChecked) {
                runAnimationPreview()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        animationJob?.cancel()
    }

    private fun updatePreviewImage() {
        val toolkit = appContainer.qrToolkit
        val useSpoiler = appContainer.storage.settings.getQrSpoilerState()

        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = if (useSpoiler) {
                toolkit.generateSpoilerBitmap(noCache = true)
            } else {
                toolkit.generateQrBitmap(previewQrContent)
            }

            withContext(Dispatchers.Main) {
                qrImage.setImageBitmap(bitmap)
            }
        }
    }

    private fun runAnimationPreview() {
        animationJob?.cancel()

        animationJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val toolkit = appContainer.qrToolkit

            val qr = toolkit.generateQrBitmap(previewQrContent)
            val spoiler = toolkit.generateSpoilerBitmap(noCache = true)

            val type = appContainer.storage.settings.getQrSpoilerAnimationType()

            val animation = if (type == QrAnimationType.CIRCLE) {
                QrAnimationWorker.CircleAnimation(qr, spoiler)
            } else {
                QrAnimationWorker.FadeAnimation(qr, spoiler)
            }

            val durationMs = 800L
            val frameRate = 60
            val frameDelay = 1000L / frameRate
            val totalFrames = (durationMs / frameDelay).toInt()

            for (i in 0..totalFrames) {
                if (!isActive) return@launch
                val startTime = System.currentTimeMillis()

                val progress = i.toFloat() / totalFrames
                val easedProgress = QrAnimationWorker.easeInOut(progress)
                val frameBitmap = animation.getBitmap(easedProgress)

                withContext(Dispatchers.Main) {
                    qrImage.setImageBitmap(frameBitmap)
                }

                val workTime = System.currentTimeMillis() - startTime
                val delayTime = (frameDelay - workTime).coerceAtLeast(0)
                delay(delayTime)
            }

            if (!isActive) return@launch
            delay(1000)

            if (!isActive) return@launch
            withContext(Dispatchers.Main) {
                updatePreviewImage()
            }
        }
    }
}