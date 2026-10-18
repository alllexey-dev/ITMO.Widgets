package dev.alllexey.itmowidgets.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The launcher picker's `previewImage` drawables (`res/drawable{,-night}-420dpi/widget_*_preview.png`, shown by the
 * `previewLayout`s too): the real widgets on the sample day, drawn as [WidgetsScreenshotTest] draws them. With
 * `ITMO_WRITE_WIDGET_PREVIEWS=1` it writes them; otherwise it checks that every one is there. Regenerate them whenever
 * a widget layout changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class WidgetPreviewImagesTest {

    @Test
    fun theLauncherPreviewsAreTheRealWidgets() {
        val write = System.getenv("ITMO_WRITE_WIDGET_PREVIEWS") == "1"
        for (night in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers("w411dp-h891dp-${if (night) "night" else "notnight"}-420dpi")
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = File(RES, if (night) "drawable-night-420dpi" else "drawable-420dpi")
            PREVIEWS.forEach { (name, case) ->
                val file = File(directory, "$name.png")
                if (write) {
                    val view = WidgetsScreenshotTest.view(context, case, theme = null)
                    directory.mkdirs()
                    file.outputStream().use {
                        WidgetsScreenshotTest.render(context, view, case.widthDp, case.heightDp).compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                assertTrue("$file is missing: run with ITMO_WRITE_WIDGET_PREVIEWS=1", file.isFile)
            }
        }
    }

    private companion object {
        // Unit tests run in the module directory.
        val RES = File("src/main/res")
        val PREVIEWS = listOf(
            "widget_single_lesson_preview" to WidgetsScreenshotTest.Case.NextMedium,
            "widget_day_preview" to WidgetsScreenshotTest.Case.Day,
            "widget_lesson_list_preview" to WidgetsScreenshotTest.Case.List,
            "widget_sport_preview" to WidgetsScreenshotTest.Case.SportQueue,
        )
    }
}
