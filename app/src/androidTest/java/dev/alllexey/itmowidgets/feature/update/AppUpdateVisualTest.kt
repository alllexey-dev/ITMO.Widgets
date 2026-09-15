package dev.alllexey.itmowidgets.feature.update

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.ui.AppUpdatePreviewActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The real screen with a synthetic offer: no version check and no backend call. */
@RunWith(AndroidJUnit4::class)
class AppUpdateVisualTest {

    @Test
    fun offerReadsAndFitsAcrossAppearances() {
        appearances.forEachIndexed { index, appearance ->
            preview(appearance, offer(note = "Виджет расписания обновляется быстрее, зачётка помнит выбранный семестр.")) { scenario ->
                scenario.onActivity { activity ->
                    val root = activity.fragment.requireView()
                    assertEquals("2.1 → 2.2", root.text(R.id.update_versions))
                    assertEquals(activity.getString(R.string.app_update_title), root.text(R.id.update_title))
                    assertTrue(root.text(R.id.update_description).endsWith("выбранный семестр."))
                    // A release the user can postpone keeps every way out visible.
                    assertEquals(View.VISIBLE, root.findViewById<View>(R.id.skip_button).visibility)
                    assertTextFits(root)
                    assertTouchTargets(root)
                }
                screenshot("offer-$index")
            }
        }
    }

    @Test
    fun unsupportedBuildExplainsItselfAndDropsTheSkip() {
        listOf(appearances.first(), appearances.last()).forEachIndexed { index, appearance ->
            preview(appearance, offer(unsupported = true)) { scenario ->
                scenario.onActivity { activity ->
                    val root = activity.fragment.requireView()
                    assertEquals(activity.getString(R.string.app_update_unsupported_title), root.text(R.id.update_title))
                    assertEquals(
                        activity.getString(R.string.app_update_unsupported_description),
                        root.text(R.id.update_description)
                    )
                    // Skipping an unsupported build would leave nothing that works.
                    assertEquals(View.GONE, root.findViewById<View>(R.id.skip_button).visibility)
                    assertTextFits(root)
                    assertTouchTargets(root)
                }
                screenshot("unsupported-$index")
            }
        }
    }

    private fun offer(note: String = "", unsupported: Boolean = false) = AppUpdate(
        installed = AppVersionName("2.1"),
        latest = AppVersionName("2.2"),
        note = note,
        unsupported = unsupported
    )

    private fun preview(
        appearance: AppUpdatePreviewActivity.Appearance,
        offer: AppUpdate,
        block: (ActivityScenario<AppUpdatePreviewActivity>) -> Unit
    ) {
        AppUpdatePreviewActivity.appearance = appearance
        AppUpdatePreviewActivity.offer = offer
        try {
            val intent = Intent(ApplicationProvider.getApplicationContext(), AppUpdatePreviewActivity::class.java)
            ActivityScenario.launch<AppUpdatePreviewActivity>(intent).use { scenario ->
                settle()
                block(scenario)
            }
        } finally {
            AppUpdatePreviewActivity.appearance = AppUpdatePreviewActivity.Appearance()
        }
    }

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(400)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun screenshot(name: String) {
        // Dynamic colours and night mode recreate the host; capture only after it has drawn again.
        settle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.externalCacheDir, "app-update-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun assertTextFits(root: View) {
        root.descendants().filterIsInstance<TextView>().filter { it.isShown && it.text.isNotEmpty() }.forEach { view ->
            val layout = view.layout ?: return@forEach
            assertTrue("Height: ${view.text}", layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
            for (line in 0 until layout.lineCount) {
                assertEquals("Ellipsis: ${view.text}", 0, layout.getEllipsisCount(line))
                assertTrue("Width: ${view.text}", layout.getLineMax(line) <= view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1)
            }
        }
    }

    private fun assertTouchTargets(root: View) {
        val min = 48 * root.resources.displayMetrics.density - 1
        root.descendants().filter { it.isShown && it.isClickable }.forEach {
            assertTrue("Touch target: ${it.javaClass.simpleName}", it.width >= min && it.height >= min)
        }
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) (0 until childCount).forEach { yieldAll(getChildAt(it).descendants()) }
    }

    private val appearances = listOf(
        AppUpdatePreviewActivity.Appearance(),
        AppUpdatePreviewActivity.Appearance(dark = true),
        AppUpdatePreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f, colorSeed = 0xff826c24.toInt()),
        AppUpdatePreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f, dark = true, colorSeed = 0xff386a20.toInt())
    )
}
