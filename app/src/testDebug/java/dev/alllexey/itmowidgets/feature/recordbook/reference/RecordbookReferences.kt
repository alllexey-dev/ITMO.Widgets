package dev.alllexey.itmowidgets.feature.recordbook.reference

import android.app.Activity
import android.os.Looper
import android.view.View
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewFixtures
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewFixtures.Scene
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowViewRootImpl

/**
 * The XML references of the recordbook screens (LR-1c): today's Views in the debug host, captured under the name of
 * the preview that replaces them into `shared/feature-recordbook/screenshots/`. Each port deletes its screen's test.
 */
internal class RecordbookReferences(shots: AppScreenshotRule) {

    private val references = XmlReferenceCapture(shots, module = "feature-recordbook")

    /** Captures [scene] as `<preview>_<appearance>`; the host is filled before every launch and emptied after. */
    fun capture(preview: String, scene: Scene) = try {
        references.host(
            preview,
            RecordbookPreviewActivity::class.java,
            appearance = {
                RecordbookPreviewActivity.appearance = it
                scene.install()
            },
            ready = scene::show,
            view = { activity -> scene.view(activity).also { focusWindowOf(it, activity) } },
        )
    } finally {
        RecordbookPreviewFixtures.reset()
    }

    /**
     * Roborazzi finds a view through Espresso, which looks in the focused window only. A sheet lives in its dialog's
     * window, which Robolectric never focuses, so the focus moves there from the activity.
     */
    private fun focusWindowOf(view: View, activity: Activity) {
        val window = view.rootView
        val content = activity.window.decorView
        if (window === content) return
        Shadow.extract<ShadowViewRootImpl>(content.parent).callWindowFocusChanged(false)
        Shadow.extract<ShadowViewRootImpl>(window.parent).callWindowFocusChanged(true)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
