package dev.alllexey.itmowidgets.core.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsPreviewActivity
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignComponentsVisualTest {
    @Test
    fun optInRefreshUsesAppForegroundAndBackgroundAcrossAppearances() {
        appearances.forEach { appearance -> preview(appearance) { scenario ->
            scenario.onActivity { activity ->
                for (layout in listOf(R.layout.fragment_schedule, R.layout.fragment_recordbook,
                    R.layout.fragment_recordbook_subject, R.layout.fragment_sport_my, R.layout.fragment_sport_sign)) {
                    val root = activity.layoutInflater.inflate(layout, FrameLayout(activity), false)
                    val refresh = root.descendants().filterIsInstance<SwipeRefreshLayout>().single()
                    refresh.applyAppRefreshColors()
                    val indicator = refresh.indicator()
                    assertArrayEquals(intArrayOf(activity.color.primary), (indicator.drawable as CircularProgressDrawable).colorSchemeColors)
                    assertEquals(activity.color.background, centerPixel(indicator.background))
                    // Applying the shared configuration again must not accumulate variants.
                    refresh.applyAppRefreshColors()
                    assertArrayEquals(intArrayOf(activity.color.primary), (indicator.drawable as CircularProgressDrawable).colorSchemeColors)
                    assertEquals(activity.color.background, centerPixel(indicator.background))
                }
            }
        } }
    }

    @Test
    fun inflatedLightItmoIdRefreshKeepsLibraryDefaults() = preview(Appearance()) { scenario ->
        scenario.onActivity { activity ->
            val root = activity.layoutInflater.inflate(R.layout.activity_login, FrameLayout(activity), false)
            val webView = root.findViewById<WebView>(R.id.login_web_view)
            webView.settings.blockNetworkLoads = true
            try {
                val login = root.findViewById<SwipeRefreshLayout>(R.id.login_swipe_refresh).indicator()
                val defaults = SwipeRefreshLayout(activity).indicator()
                val unrelatedRefresh = SwipeRefreshLayout(activity)
                unrelatedRefresh.applyAppRefreshColors()
                assertArrayEquals((defaults.drawable as CircularProgressDrawable).colorSchemeColors,
                    (login.drawable as CircularProgressDrawable).colorSchemeColors)
                assertEquals(centerPixel(defaults.background), centerPixel(login.background))
                assertTrue(webView.url == null || webView.url == "about:blank")
            } finally {
                webView.destroy()
            }
        }
    }

    @Test
    fun errorEmptyAndHomeStatesFitFullScreenAndInlineLayouts() {
        appearances.forEachIndexed { index, appearance -> preview(appearance) { scenario ->
            val cases = listOf(
                StateLayout("recordbook", R.layout.fragment_recordbook),
                StateLayout("subject", R.layout.fragment_recordbook_subject),
                StateLayout("schedule", R.layout.fragment_schedule, R.id.schedule_state_container,
                    R.id.schedule_state_title, R.id.schedule_state_description, R.id.schedule_state_action),
                StateLayout("sport-my", R.layout.fragment_sport_my, R.id.empty_state_layout,
                    action = R.id.sport_state_retry),
                StateLayout("sport-inline", R.layout.item_content_state, inline = true)
            )
            for (case in cases) for (error in listOf(false, true)) {
                lateinit var state: View
                scenario.onActivity { activity ->
                    val root = activity.layoutInflater.inflate(case.layout, FrameLayout(activity), false)
                    state = if (case.inline) root else root.findViewById(case.container)
                    state.visibility = View.VISIBLE
                    state.findViewById<TextView>(case.title).setText(
                        if (error) R.string.common_load_error_title else when (case.name) {
                            "schedule" -> R.string.schedule_empty_title
                            "sport-my" -> R.string.sport_bookings_empty_title
                            "sport-inline" -> R.string.sport_lessons_empty_title
                            else -> R.string.recordbook_empty_title
                        }
                    )
                    state.findViewById<TextView>(case.description).setText(
                        if (error) R.string.common_error_network else when (case.name) {
                            "schedule" -> R.string.schedule_empty_description
                            "sport-my" -> R.string.sport_bookings_empty_description
                            "sport-inline" -> R.string.sport_lessons_empty_description
                            else -> R.string.recordbook_empty_description
                        }
                    )
                    state.findViewById<MaterialButton>(case.action).apply {
                        setText(R.string.common_retry)
                        visibility = if (error) View.VISIBLE else View.GONE
                    }
                    if (case.name == "sport-my") {
                        root.findViewById<View>(R.id.points_card).visibility = if (error) View.GONE else View.VISIBLE
                        root.findViewById<View>(R.id.button_go_to_schedule).visibility = if (error) View.GONE else View.VISIBLE
                    }
                    if (error) state.descendants().filterIsInstance<ImageView>().first().setImageResource(R.drawable.ic_error)
                    show(activity, root, appearance.widthDp, case.inline)
                }
                settle()
                screenshot("${case.name}-${if (error) "error" else "empty"}-$index")
                scenario.onActivity {
                    assertContentBounds(state)
                    assertTrue(state.height > 0)
                    assertEquals(state.context.color.onSurface, state.findViewById<TextView>(case.title).currentTextColor)
                    assertEquals(state.context.color.onSurfaceVariant, state.findViewById<TextView>(case.description).currentTextColor)
                    state.descendants().filterIsInstance<ImageView>().forEach { assertNull(it.contentDescription) }
                    if (case.inline) assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, state.layoutParams.height)
                    state.descendants().filterIsInstance<MaterialButton>().filter { it.isShown }.forEach { button ->
                        val minimum = 48 * button.resources.displayMetrics.density - 1
                        assertTrue("Action width", button.width >= minimum)
                        assertTrue("Action height", button.height >= minimum)
                    }
                }
            }
            lateinit var home: View
            scenario.onActivity { activity ->
                home = activity.layoutInflater.inflate(R.layout.fragment_home, FrameLayout(activity), false)
                show(activity, home, appearance.widthDp)
            }
            settle()
            screenshot("home-$index")
            scenario.onActivity {
                assertContentBounds(home)
                assertFalse(home.descendants().any { it is SwipeRefreshLayout })
                assertFalse(home.descendants().any { it.isClickable })
            }
        } }
    }

    private fun assertContentBounds(root: View) {
        root.descendants().filter { it.isShown && (it is TextView || it is ImageView) }.forEach { view ->
            val bounds = Rect().also(view::getDrawingRect)
            (root as ViewGroup).offsetDescendantRectToMyCoords(view, bounds)
            assertTrue("Child bounds: ${view.javaClass.simpleName} $bounds in ${root.width}x${root.height}",
                bounds.left >= 0 && bounds.top >= 0 && bounds.right <= root.width && bounds.bottom <= root.height)
            if (view is TextView && view.text.isNotEmpty()) {
                val layout = checkNotNull(view.layout)
                assertTrue("Text height: ${view.text}", layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
                for (line in 0 until layout.lineCount) {
                    assertEquals("Ellipsis: ${view.text}", 0, layout.getEllipsisCount(line))
                    assertTrue("Text width: ${view.text}", layout.getLineMax(line) <= view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1)
                }
            }
        }
    }

    private fun show(activity: SettingsPreviewActivity, root: View, widthDp: Int, inline: Boolean = false) {
        val frame = FrameLayout(activity).apply { setBackgroundColor(activity.color.surface) }
        frame.addView(root, FrameLayout.LayoutParams(
            if (widthDp > 0) (widthDp * activity.resources.displayMetrics.density).toInt() else -1,
            if (inline) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ))
        activity.setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(frame)
    }

    private fun SwipeRefreshLayout.indicator() = descendants().filterIsInstance<ImageView>()
        .single { it.drawable is CircularProgressDrawable }

    private fun centerPixel(drawable: Drawable): Int {
        val previousBounds = Rect(drawable.bounds)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        return try {
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        } finally {
            drawable.bounds = previousBounds
            bitmap.recycle()
        }
    }

    private fun preview(appearance: Appearance, block: (ActivityScenario<SettingsPreviewActivity>) -> Unit) {
        SettingsPreviewActivity.appearance = SettingsPreviewActivity.Appearance(appearance.fontScale, appearance.dark)
        try {
            val intent = Intent(ApplicationProvider.getApplicationContext(), SettingsPreviewActivity::class.java)
            appearance.seed?.let { intent.putExtra(SettingsPreviewActivity.EXTRA_COLOR_SEED, it) }
            ActivityScenario.launch<SettingsPreviewActivity>(intent).use(block)
        } finally {
            SettingsPreviewActivity.appearance = SettingsPreviewActivity.Appearance()
        }
    }

    private fun settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(250)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(instrumentation.targetContext.externalCacheDir, "design-components-screenshots").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) (0 until childCount).forEach { yieldAll(getChildAt(it).descendants()) }
    }

    private data class StateLayout(val name: String, val layout: Int, val container: Int = R.id.state_container,
        val title: Int = R.id.state_title, val description: Int = R.id.state_description,
        val action: Int = R.id.state_action, val inline: Boolean = false)

    private data class Appearance(val widthDp: Int = 0, val fontScale: Float = 1f, val dark: Boolean = false, val seed: Int? = null)

    private val appearances = listOf(
        Appearance(), Appearance(dark = true),
        Appearance(widthDp = 320, fontScale = 1.3f, seed = 0xff826c24.toInt()),
        Appearance(widthDp = 320, fontScale = 1.3f, dark = true, seed = 0xff386a20.toInt())
    )
}
