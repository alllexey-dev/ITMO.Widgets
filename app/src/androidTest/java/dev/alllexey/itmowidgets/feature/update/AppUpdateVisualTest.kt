package dev.alllexey.itmowidgets.feature.update

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.ui.AppUpdatePreviewActivity
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toAppUpdate
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTextFits
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTouchTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The real screen with a synthetic offer: no version check and no backend call. */
@RunWith(AndroidJUnit4::class)
class AppUpdateVisualTest {

    @Test
    fun offerReadsAndFitsAcrossAppearances() {
        Appearances.default.forEachIndexed { index, spec ->
            preview(spec.toAppUpdate(), offer(note = "Виджет расписания обновляется быстрее, зачётка помнит выбранный семестр.")) { scenario ->
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
        // The two extremes of the matrix: plain light and dark, narrow, large font.
        Appearances.default.let { listOf(it.first(), it.last()) }.distinct().forEachIndexed { index, spec ->
            preview(spec.toAppUpdate(), offer(unsupported = true)) { scenario ->
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

    private fun settle() = TestUi.settle(400)

    /** Dynamic colours and night mode recreate the host; capture only after it has drawn again. */
    private fun screenshot(name: String) = Screenshots.capture("app-update-screenshots", name) { settle() }
}
