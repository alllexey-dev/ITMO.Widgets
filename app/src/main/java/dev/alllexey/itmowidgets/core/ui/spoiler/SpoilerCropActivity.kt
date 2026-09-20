package dev.alllexey.itmowidgets.core.ui.spoiler

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.canhub.cropper.CropImageActivity
import com.canhub.cropper.CropImageView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.ActivitySpoilerCropBinding

/** The library's activity assumes an ActionBar; the application has a no-action-bar theme. */
@Suppress("DEPRECATION")
class SpoilerCropActivity : CropImageActivity() {

    private var cropping = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = findViewById<ViewGroup>(android.R.id.content)
        val cropView = content.getChildAt(0)
        content.removeView(cropView)
        val binding = ActivitySpoilerCropBinding.inflate(layoutInflater)
        binding.cropContent.addView(cropView)
        setContentView(binding.root)
        setSupportActionBar(binding.cropToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.spoiler_crop_title)
        binding.cropToolbar.setNavigationIcon(R.drawable.ic_close)
        binding.cropToolbar.setNavigationContentDescription(R.string.common_cancel)
        binding.cropToolbar.setNavigationOnClickListener { setResultCancel() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    // Return only the URI string, not the library's Parcelable containing an exception.
    override fun getResultIntent(uri: Uri?, error: Exception?, sampleSize: Int): Intent =
        Intent().putExtra(SpoilerCropContract.RESULT_URI, uri?.toString())

    override fun cropImage() {
        if (cropping) return
        val view = findViewById<CropImageView>(com.canhub.cropper.R.id.cropImageView)
        if (view.wholeImageRect == null) return
        cropping = true
        invalidateOptionsMenu()
        super.cropImage()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(com.canhub.cropper.R.id.crop_image_menu_crop)?.isEnabled = !cropping
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val created = super.onCreateOptionsMenu(menu)
        menu.findItem(com.canhub.cropper.R.id.ic_rotate_right_24)
            ?.setTitle(R.string.spoiler_crop_rotate)
        return created
    }
}
