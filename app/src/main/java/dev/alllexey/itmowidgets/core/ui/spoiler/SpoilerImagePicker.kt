package dev.alllexey.itmowidgets.core.ui.spoiler

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

/**
 * The spoiler image flow: the system photo picker, then the square crop screen.
 *
 * Create it while the caller is being constructed, like any `registerForActivityResult`.
 * A device without a picker answers [SpoilerCropResult.Failed].
 */
class SpoilerImagePicker(caller: ActivityResultCaller, private val onResult: (SpoilerCropResult) -> Unit) {

    private val cropLauncher = caller.registerForActivityResult(SpoilerCropContract(), onResult)

    private val pickLauncher = caller.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropLauncher.launch(uri)
    }

    fun launch() {
        try {
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: ActivityNotFoundException) {
            onResult(SpoilerCropResult.Failed)
        }
    }
}
