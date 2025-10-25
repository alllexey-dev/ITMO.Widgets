package dev.alllexey.itmowidgets.ui.settings

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import dev.alllexey.itmowidgets.AppContainer
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.DYNAMIC_QR_COLORS_KEY
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.QR_SPOILER_ANIMATION_TYPE_KEY
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.QR_SPOILER_KEY
import dev.alllexey.itmowidgets.ui.widgets.WidgetUtils
import kotlin.concurrent.thread

class QrWidgetSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var appContainer: AppContainer

    private val cropImageLauncher = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedImageUri = result.uriContent

            croppedImageUri?.let { uri ->
                if (appContainer.qrToolkit.customSpoilerManager.saveCustomSpoiler(uri)) {
                    Toast.makeText(requireContext(), "Спойлер сохранён", Toast.LENGTH_SHORT).show()
                    updateAllWidgets()
                    updateCustomSpoilerPreferenceState()
                } else {
                    Toast.makeText(requireContext(), "Спойлер сброшен", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
//            val exception = result.error
//            Toast.makeText(requireContext(), "Ошибка: ${exception?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.qr_widget_preferences, rootKey)

        appContainer = (requireContext().applicationContext as ItmoWidgetsApp).appContainer
        val storage = appContainer.storage

        val dynamicQrColors = findPreference<SwitchPreference>(DYNAMIC_QR_COLORS_KEY)
        dynamicQrColors?.setOnPreferenceChangeListener { _, _ ->
            updateAllWidgets()
            true
        }

        val qrSpoiler = findPreference<SwitchPreference>(QR_SPOILER_KEY)
        qrSpoiler?.setOnPreferenceChangeListener { _, newValue ->
            onQrSpoilerStateChanged(newValue as Boolean)
            updateAllWidgets()
            true
        }

        val chooseSpoilerPreference = findPreference<Preference>("choose_custom_spoiler")
        chooseSpoilerPreference?.setOnPreferenceClickListener {
            val cropOptions = CropImageOptions(
                guidelines = CropImageView.Guidelines.ON,
                aspectRatioX = 1,
                aspectRatioY = 1,
                fixAspectRatio = true,
                outputCompressFormat = Bitmap.CompressFormat.PNG,
                outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE,
                outputRequestWidth = 420,
                outputRequestHeight = 420,
                imageSourceIncludeCamera = false
            )

            val contractOptions = CropImageContractOptions(
                uri = null,
                cropImageOptions = cropOptions
            )

            cropImageLauncher.launch(contractOptions)
            true
        }

        val removeSpoilerPreference = findPreference<Preference>("remove_custom_spoiler")
        removeSpoilerPreference?.setOnPreferenceClickListener {
            if (appContainer.qrToolkit.customSpoilerManager.deleteCustomSpoiler()) {
                Toast.makeText(requireContext(), "Спойлер сброшен", Toast.LENGTH_SHORT).show()
                updateAllWidgets()
                updateCustomSpoilerPreferenceState()
            }
            true
        }

        onQrSpoilerStateChanged(storage.settings.getQrSpoilerState())
        updateCustomSpoilerPreferenceState()
    }

    private fun updateCustomSpoilerPreferenceState() {
        val removeSpoilerPreference = findPreference<Preference>("remove_custom_spoiler")
        removeSpoilerPreference?.isEnabled = appContainer.qrToolkit.customSpoilerManager.hasCustomSpoiler()
    }

    fun onQrSpoilerStateChanged(newValue: Boolean) {
        findPreference<ListPreference>(QR_SPOILER_ANIMATION_TYPE_KEY)?.isEnabled = newValue
    }

    private fun updateAllWidgets() {
        thread {
            WidgetUtils.updateAllWidgets(preferenceManager.context)
        }
    }
}