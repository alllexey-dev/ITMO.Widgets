package dev.alllexey.itmowidgets.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.canhub.cropper.CropImageView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.qr.CustomSpoilerManager
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.databinding.FragmentMeBinding
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState
import dev.alllexey.itmowidgets.feature.me.ui.MeRenderer
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsPreviewActivity
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropActivity
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropResult
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropContract
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toSettingsPreview
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileAndSpoilerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun profileAppearanceAndLongNamesRemainCompactWithoutClipping() {
        for (spec in Appearances.default) {
            SettingsPreviewActivity.appearance = spec.toSettingsPreview()
            val intent = Intent(context, SettingsPreviewActivity::class.java)
                .putExtra(SettingsPreviewActivity.EXTRA_PROFILE, true)
                .putExtra(SettingsPreviewActivity.EXTRA_WIDTH_DP, spec.widthDp)
            spec.colorSeed?.let { intent.putExtra(SettingsPreviewActivity.EXTRA_COLOR_SEED, it) }
            ActivityScenario.launch<SettingsPreviewActivity>(intent).use { scenario ->
                scenario.onActivity {
                    val binding = FragmentMeBinding.bind(it.findViewById(R.id.main))
                    binding.debugToolsRow.visibility = View.VISIBLE
                    binding.debugDivider.visibility = View.VISIBLE
                    MeRenderer.render(binding, MeUiState(CurrentUser(123456, "Александрова-Константинопольская Мария Александровна", null)))
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(300)
                scenario.onActivity {
                    val binding = FragmentMeBinding.bind(it.findViewById(R.id.main))
                    val text = binding.profileName
                    assertEquals(0, text.layout.getEllipsisCount(text.lineCount - 1))
                    assertTrue(text.layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                    for (row in listOf(binding.settingsRow, binding.debugToolsRow, binding.signOutRow)) {
                        assertTrue(row.height >= 48 * it.resources.displayMetrics.density)
                        assertTrue(row.right <= (row.parent as View).width)
                    }
                }
                screenshot("profile-${spec.name}", scenario)
            }
        }
        SettingsPreviewActivity.appearance = SettingsPreviewActivity.Appearance()
    }

    @Test
    fun unavailableProfileHidesMetadataAndDisablesSignOutDuringOperation() {
        val intent = Intent(context, SettingsPreviewActivity::class.java)
            .putExtra(SettingsPreviewActivity.EXTRA_PROFILE, true)
        ActivityScenario.launch<SettingsPreviewActivity>(intent).use { scenario ->
            scenario.onActivity {
                val binding = FragmentMeBinding.bind(it.findViewById(R.id.main))
                MeRenderer.render(binding, MeUiState(signOutInProgress = true))
                assertEquals(it.getString(R.string.me_unknown_user), binding.profileName.text.toString())
                assertEquals(View.GONE, binding.profileMeta.visibility)
                assertFalse(binding.signOutRow.isEnabled)
                MeRenderer.render(binding, MeUiState())
                assertTrue(binding.signOutRow.isEnabled)
            }
            screenshot("profile-unavailable", scenario)
        }
    }

    @Test
    fun croppedImageHasVisibleConfirmationAndSurvivesRecreation() {
        val source = fixture()
        val contract = SpoilerCropContract()
        ActivityScenario.launchActivityForResult<SpoilerCropActivity>(contract.createIntent(context, Uri.fromFile(source))).use { scenario ->
            waitForImage(scenario)
            scenario.recreate()
            waitForImage(scenario)
            SystemClock.sleep(300)
            screenshot("spoiler-crop", scenario)
            scenario.onActivity { activity ->
                val action = activity.findViewById<TextView>(com.canhub.cropper.R.id.crop_image_menu_crop)
                assertNotNull(action)
                assertTrue(action.isShown)
                assertEquals(activity.getString(R.string.spoiler_crop_done), action.text.toString())
                val position = IntArray(2)
                action.getLocationOnScreen(position)
                assertTrue(position[1] > 0)
                assertTrue(action.performClick())
            }
            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            val crop = contract.parseResult(result.resultCode, result.resultData)
            assertTrue(crop is SpoilerCropResult.Image)
            val bitmap = context.contentResolver.openInputStream((crop as SpoilerCropResult.Image).uri).use { BitmapFactory.decodeStream(it) }
            assertNotNull(bitmap)
            assertEquals(bitmap.width, bitmap.height)
            assertTrue(bitmap.width in 1..420)
            bitmap.recycle()
        }
        source.delete()
    }

    @Test
    fun cancellingCropDoesNotReturnAnImageOrError() {
        val source = fixture()
        val contract = SpoilerCropContract()
        ActivityScenario.launchActivityForResult<SpoilerCropActivity>(contract.createIntent(context, Uri.fromFile(source))).use { scenario ->
            waitForImage(scenario)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            val result = scenario.result
            assertEquals(Activity.RESULT_CANCELED, result.resultCode)
            val crop = contract.parseResult(result.resultCode, result.resultData)
            assertEquals(SpoilerCropResult.Cancelled, crop)
        }
        source.delete()
    }

    @Test
    fun failedImageLoadReturnsARecoverableError() {
        val contract = SpoilerCropContract()
        val missing = File(context.cacheDir, "missing-spoiler-fixture.png")
        ActivityScenario.launchActivityForResult<SpoilerCropActivity>(contract.createIntent(context, Uri.fromFile(missing))).use { scenario ->
            val result = scenario.result
            val crop = contract.parseResult(result.resultCode, result.resultData)
            assertEquals(SpoilerCropResult.Failed, crop)
        }
    }

    @Test
    fun storageBoundsImageAndPreservesPreviousImageOnInvalidReplacement() {
        val directory = File(context.cacheDir, "spoiler-store-test").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = directory }
        val manager = CustomSpoilerManager(isolated)
        val source = fixture()
        try {
            assertTrue(manager.saveCustomSpoiler(Uri.fromFile(source)))
            assertTrue(manager.hasCustomSpoiler())
            val before = manager.getCustomSpoilerBitmap()!!
            assertEquals(420, before.width)
            assertEquals(420, before.height)
            assertEquals(0, Color.alpha(before.getPixel(0, 0)))
            assertFalse(manager.saveCustomSpoiler(Uri.fromFile(File(context.cacheDir, "missing-image"))))
            val after = manager.getCustomSpoilerBitmap()!!
            assertTrue(before.sameAs(after))
            before.recycle()
            after.recycle()
            assertTrue(manager.deleteCustomSpoiler())
            assertTrue(manager.deleteCustomSpoiler())
            assertFalse(manager.hasCustomSpoiler())
        } finally {
            source.delete()
            directory.deleteRecursively()
        }
    }

    private fun fixture(): File {
        val image = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.rgb(215, 234, 225))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 115, 90) }
        canvas.drawCircle(800f, 600f, 380f, paint)
        paint.color = Color.rgb(245, 200, 90)
        canvas.drawRect(760f, 200f, 840f, 1000f, paint)
        val file = File(context.cacheDir, "spoiler-test-source.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        return file
    }

    private fun waitForImage(scenario: ActivityScenario<SpoilerCropActivity>) {
        repeat(100) {
            var loaded = false
            scenario.onActivity { activity ->
                loaded = activity.findViewById<CropImageView>(com.canhub.cropper.R.id.cropImageView).wholeImageRect != null
            }
            if (loaded) return
            SystemClock.sleep(100)
        }
        fail("Crop image did not load")
    }

    private fun <A : Activity> screenshot(name: String, scenario: ActivityScenario<A>) =
        Screenshots.capture("profile-verification", name, Screenshots.Location.FILES) {
            lateinit var activity: Activity
            scenario.onActivity { activity = it }
            TestUi.awaitFrameCommit(activity)
            instrumentation.waitForIdleSync()
            SystemClock.sleep(350)
        }
}
