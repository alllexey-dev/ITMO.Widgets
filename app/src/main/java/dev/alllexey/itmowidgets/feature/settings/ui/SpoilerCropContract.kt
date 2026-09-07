package dev.alllexey.itmowidgets.feature.settings.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.net.toUri
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.R

sealed interface SpoilerCropResult {
    data class Image(val uri: Uri) : SpoilerCropResult
    data object Cancelled : SpoilerCropResult
    data object Failed : SpoilerCropResult
}

@Suppress("DEPRECATION")
class SpoilerCropContract : ActivityResultContract<Uri, SpoilerCropResult>() {

    companion object {
        const val RESULT_URI = "spoiler_crop_uri"
    }

    private val delegate = CropImageContract()

    override fun createIntent(context: Context, input: Uri): Intent = delegate.createIntent(
        context,
        CropImageContractOptions(
            uri = input,
            cropImageOptions = CropImageOptions(
                guidelines = CropImageView.Guidelines.ON,
                aspectRatioX = 1,
                aspectRatioY = 1,
                fixAspectRatio = true,
                outputCompressFormat = Bitmap.CompressFormat.PNG,
                outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE,
                outputRequestWidth = 420,
                outputRequestHeight = 420,
                imageSourceIncludeCamera = false,
                imageSourceIncludeGallery = false,
                allowFlipping = false,
                activityTitle = context.getString(R.string.spoiler_crop_title),
                cropMenuCropButtonTitle = context.getString(R.string.spoiler_crop_done),
                activityBackgroundColor = MaterialColors.getColor(
                    context, com.google.android.material.R.attr.colorSurface, 0
                )
            )
        )
    ).setClass(context, SpoilerCropActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): SpoilerCropResult {
        if (resultCode == Activity.RESULT_CANCELED) return SpoilerCropResult.Cancelled
        val uri = intent?.getStringExtra(RESULT_URI)?.toUri()
        return if (resultCode == Activity.RESULT_OK && uri != null) SpoilerCropResult.Image(uri) else SpoilerCropResult.Failed
    }
}
