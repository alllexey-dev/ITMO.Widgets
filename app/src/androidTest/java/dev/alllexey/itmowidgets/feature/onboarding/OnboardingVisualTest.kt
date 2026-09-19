package dev.alllexey.itmowidgets.feature.onboarding

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.materialswitch.MaterialSwitch
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The real first-run flow on fixture repositories: no stored preferences, no backend. */
@RunWith(AndroidJUnit4::class)
class OnboardingVisualTest {

    @After
    fun reset() {
        SettingsNavigationTestActivity.appearance = SettingsNavigationTestActivity.Appearance()
        SettingsNavigationTestActivity.onboardingFixture =
            SettingsNavigationTestActivity.OnboardingFixture()
        SettingsNavigationTestActivity.startDestination = R.id.navigation_home
    }

    @Test
    fun everyStepReadsAndFitsAcrossAppearances() {
        appearances.forEachIndexed { index, appearance ->
            launch(appearance) { scenario ->
                // Three widget steps: preview, the widget's own rows, one pin button.
                listOf(
                    R.string.onboarding_compact_widget_title to 2,
                    R.string.onboarding_full_widget_title to 3,
                    R.string.onboarding_qr_widget_title to 2
                ).forEachIndexed { step, (titleRes, rows) ->
                    if (step > 0) next(scenario)
                    scenario.onActivity { activity ->
                        val root = activity.onboardingRoot()
                        val page = activity.currentPage()
                        assertEquals(activity.getString(titleRes), page.text(R.id.step_title))
                        assertEquals(rows, page.findViewById<ViewGroup>(R.id.setting_rows).switches().size)
                        assertPreviewDrawn(page, compact = step == 0)
                        assertEquals(View.VISIBLE, page.findViewById<View>(R.id.pin_button).visibility)
                        assertEquals(View.GONE, page.findViewById<View>(R.id.pin_hint).visibility)
                        assertEquals(4, root.findViewById<ViewGroup>(R.id.onboarding_steps).childCount)
                        assertEquals(activity.getString(R.string.onboarding_next), root.text(R.id.next_button))
                        assertTextFits(root)
                        assertTouchTargets(root)
                    }
                    capture("widget-$step-$index")
                }

                next(scenario)
                scenario.onActivity { activity ->
                    val root = activity.onboardingRoot()
                    val page = activity.currentPage()
                    assertEquals(false, page.findViewById<MaterialSwitch>(R.id.services_switch).isChecked)
                    // Without the opt-in this is the last step: nothing to skip, "Готово" instead of "Далее".
                    assertEquals(View.GONE, root.findViewById<View>(R.id.skip_button).visibility)
                    assertEquals(activity.getString(R.string.onboarding_done), root.text(R.id.next_button))
                    assertTextFits(root)
                    assertTouchTargets(root)
                }
                capture("services-$index")

                scenario.onActivity { it.currentPage().findViewById<View>(R.id.services_row).performClick() }
                settle()
                scenario.onActivity { activity ->
                    val root = activity.onboardingRoot()
                    val page = activity.currentPage()
                    // The switch flips in place; the flow grows by the notifications step.
                    assertEquals(true, page.findViewById<MaterialSwitch>(R.id.services_switch).isChecked)
                    assertEquals(View.VISIBLE, page.findViewById<View>(R.id.services_switch).visibility)
                    assertEquals(5, root.findViewById<ViewGroup>(R.id.onboarding_steps).childCount)
                    assertEquals(View.VISIBLE, root.findViewById<View>(R.id.skip_button).visibility)
                    assertEquals(activity.getString(R.string.onboarding_next), root.text(R.id.next_button))
                    assertTextFits(root)
                }
                capture("services-on-$index")

                next(scenario)
                scenario.onActivity { activity ->
                    val root = activity.onboardingRoot()
                    val page = activity.currentPage()
                    assertEquals(View.VISIBLE, page.findViewById<View>(R.id.notifications_status).visibility)
                    assertEquals(View.VISIBLE, page.findViewById<View>(R.id.notifications_button).visibility)
                    assertEquals(View.GONE, root.findViewById<View>(R.id.skip_button).visibility)
                    assertEquals(activity.getString(R.string.onboarding_done), root.text(R.id.next_button))
                    assertTextFits(root)
                    assertTouchTargets(root)
                }
                capture("notifications-$index")
            }
        }
    }

    @Test
    fun aWidgetRowWritesThroughAndTheSwitchFollows() {
        launch(appearances.first()) { scenario ->
            scenario.onActivity { activity ->
                val rows = activity.currentPage().findViewById<ViewGroup>(R.id.setting_rows)
                assertEquals(true, rows.switches().first().isChecked)
                rows.rows().first().performClick()
            }
            settle()
            scenario.onActivity { activity ->
                val rows = activity.currentPage().findViewById<ViewGroup>(R.id.setting_rows)
                assertEquals(false, rows.switches().first().isChecked)
                assertPreviewDrawn(activity.currentPage(), compact = true)
            }
            capture("widget-0-toggled")
        }
    }

    @Test
    fun aLauncherWithoutPinningExplainsItselfInsteadOfOfferingButtons() {
        SettingsNavigationTestActivity.onboardingFixture =
            SettingsNavigationTestActivity.OnboardingFixture(pinSupported = false)

        launch(appearances.first()) { scenario ->
            scenario.onActivity { activity ->
                val page = activity.currentPage()
                assertEquals(View.VISIBLE, page.findViewById<View>(R.id.pin_hint).visibility)
                assertEquals(View.GONE, page.findViewById<View>(R.id.pin_button).visibility)
                assertPreviewDrawn(page, compact = true)
                assertTextFits(activity.onboardingRoot())
            }
            capture("widget-no-pinning")
        }
    }

    private fun launch(
        appearance: SettingsNavigationTestActivity.Appearance,
        block: (ActivityScenario<SettingsNavigationTestActivity>) -> Unit
    ) {
        SettingsNavigationTestActivity.appearance = appearance
        SettingsNavigationTestActivity.startDestination = R.id.onboarding
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            settle()
            block(scenario)
        }
    }

    private fun next(scenario: ActivityScenario<SettingsNavigationTestActivity>) {
        scenario.onActivity { it.onboardingRoot().findViewById<View>(R.id.next_button).performClick() }
        settle()
    }

    private fun SettingsNavigationTestActivity.onboardingRoot(): View =
        host.childFragmentManager.fragments.single().requireView()

    /** The resumed page of the pager; other pages may exist off screen. */
    private fun SettingsNavigationTestActivity.currentPage(): View =
        host.childFragmentManager.fragments.single().childFragmentManager.fragments
            .single { it.isResumed }.requireView()

    private fun assertPreviewDrawn(page: View, compact: Boolean = false) {
        val container = page.findViewById<ViewGroup>(R.id.widget_preview_container)
        assertEquals("Preview count", 1, container.childCount)
        val preview = container.getChildAt(0)
        assertTrue("Preview size", preview.width > 0 && preview.height > 0)
        // A single-lesson preview is as tall as the widget, never the day list's bounded band.
        if (compact) assertTrue("Preview height", preview.height < 200 * page.resources.displayMetrics.density)
    }

    private fun ViewGroup.switches(): List<MaterialSwitch> =
        descendants().filterIsInstance<MaterialSwitch>().toList()

    private fun ViewGroup.rows(): List<View> = (0 until childCount).map(::getChildAt).filter { it.isClickable }

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(650)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun capture(name: String) {
        settle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.externalCacheDir, "onboarding-screenshots")
            .apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun assertTextFits(root: View) {
        root.descendants().filterIsInstance<TextView>().filter { it.isShown && it.text.isNotEmpty() }
            .forEach { view ->
                val layout = view.layout ?: return@forEach
                assertTrue(
                    "Height: ${view.text}",
                    layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom
                )
                for (line in 0 until layout.lineCount) {
                    assertEquals("Ellipsis: ${view.text}", 0, layout.getEllipsisCount(line))
                    assertTrue(
                        "Width: ${view.text}",
                        layout.getLineMax(line) <=
                            view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1
                    )
                }
            }
    }

    private fun assertTouchTargets(root: View) {
        val min = 48 * root.resources.displayMetrics.density - 1
        root.descendants().filter { it.isShown && it.isClickable }.forEach {
            assertTrue("Touch target: ${it.javaClass.simpleName}", it.height >= min)
        }
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) (0 until childCount).forEach { yieldAll(getChildAt(it).descendants()) }
    }

    private val appearances = listOf(
        SettingsNavigationTestActivity.Appearance(),
        SettingsNavigationTestActivity.Appearance(dark = true),
        SettingsNavigationTestActivity.Appearance(fontScale = 1.3f, colorSeed = 0xff087f5b.toInt()),
        SettingsNavigationTestActivity.Appearance(
            dark = true,
            fontScale = 1.3f,
            colorSeed = 0xff826c24.toInt()
        )
    )
}
