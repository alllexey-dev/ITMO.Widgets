package dev.alllexey.itmowidgets.core.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsPreviewActivity
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.toSettingsPreview
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignComponentsVisualTest {
    @Test
    fun inflatedLightItmoIdRefreshKeepsLibraryDefaults() = preview(Appearances.light) { scenario ->
        scenario.onActivity { activity ->
            val root = activity.layoutInflater.inflate(R.layout.activity_login, FrameLayout(activity), false)
            val webView = root.findViewById<WebView>(R.id.login_web_view)
            webView.settings.blockNetworkLoads = true
            try {
                val login = root.findViewById<SwipeRefreshLayout>(R.id.login_swipe_refresh).indicator()
                val defaults = SwipeRefreshLayout(activity).indicator()
                assertArrayEquals((defaults.drawable as CircularProgressDrawable).colorSchemeColors,
                    (login.drawable as CircularProgressDrawable).colorSchemeColors)
                assertEquals(centerPixel(defaults.background), centerPixel(login.background))
                assertTrue(webView.url == null || webView.url == "about:blank")
            } finally {
                webView.destroy()
            }
        }
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

    private fun preview(spec: Appearances.Spec, block: (ActivityScenario<SettingsPreviewActivity>) -> Unit) {
        SettingsPreviewActivity.appearance = spec.toSettingsPreview()
        try {
            val intent = Intent(ApplicationProvider.getApplicationContext(), SettingsPreviewActivity::class.java)
            spec.colorSeed?.let { intent.putExtra(SettingsPreviewActivity.EXTRA_COLOR_SEED, it) }
            ActivityScenario.launch<SettingsPreviewActivity>(intent).use(block)
        } finally {
            SettingsPreviewActivity.appearance = PreviewAppearance()
        }
    }
}
