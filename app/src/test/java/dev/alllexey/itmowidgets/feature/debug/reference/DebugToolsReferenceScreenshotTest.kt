package dev.alllexey.itmowidgets.feature.debug.reference

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.view.View
import androidx.annotation.IdRes
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.ReferenceHostActivity
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.debug.ui.DebugToolsFragment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Today's debug tools under the names LA-9's `DebugToolsScreenshotTest` records, in `app/screenshots/` (debug tools
 * stay in `:app`): the screen and its two dialogs, on a fresh install's stores (no token, no overrides).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class DebugToolsReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots)

    @Test
    fun content() = references.fragment("DebugToolsScreen_content") { DebugToolsFragment() }

    @Test
    fun refreshTokenDialog() =
        dialog("DebugToolsScreen_refresh-token-dialog", R.id.debug_refresh_token_configure_button)

    @Test
    fun sportScoreDialog() = dialog("DebugToolsScreen_sport-score-dialog", R.id.debug_sport_score_configure_button)

    /** Opens the screen once per launch, taps [button] and captures the dialog it shows. */
    private fun dialog(preview: String, @IdRes button: Int) {
        var dialog: Dialog? = null
        references.host(
            preview,
            ReferenceHostActivity::class.java,
            appearance = {},
            ready = { host ->
                if (host.supportFragmentManager.fragments.none { it is DebugToolsFragment }) {
                    val fragment = DebugToolsFragment()
                    host.show(fragment)
                    fragment.requireView().findViewById<View>(button).performClick()
                    dialog = ShadowDialog.getLatestDialog()
                }
                dialog?.isShowing == true
            },
            view = { it.mirror(checkNotNull(dialog?.window).decorView) },
        )
    }
}

/**
 * Shows [source], a view of a dialog window, in place of the host's content: a capture finds only views of the
 * activity's window and includes what lies under a transparent view, so the screen behind the dialog is hidden.
 */
private fun ReferenceHostActivity.mirror(source: View): View {
    for (index in 0 until container.childCount) container.getChildAt(index).visibility = View.INVISIBLE
    return WindowMirror(this, source).also(::show)
}

/** Draws [source] at its own size. */
private class WindowMirror(context: Context, private val source: View) : View(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(source.width, source.height)

    override fun onDraw(canvas: Canvas) = source.draw(canvas)
}
