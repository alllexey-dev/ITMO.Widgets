package dev.alllexey.itmowidgets.feature.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapRenderer
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewLabels
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleListRowRenderer
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetRenderer
import dev.alllexey.itmowidgets.testing.Screenshots
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a test: with `captureScreenshots=true` it renders the launcher preview images
 * (the `widget_..._preview.png` drawables) from the real widget renderers and the
 * settings preview scenario, so the picker shows exactly what a widget looks like.
 * Run it on a device of the density the images are stored for.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPreviewImageCapture {
    private val application = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var context: Context

    @Test
    fun renderLauncherPreviews() {
        if (!Screenshots.enabled) return
        for (night in listOf(false, true)) {
            context = application.createConfigurationContext(Configuration(application.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            })
            render(if (night) "night" else "light")
        }
    }

    private fun render(variant: String) {
        val labels = SchedulePreviewLabels(
            context.getString(R.string.widget_preview_subject_history),
            context.getString(R.string.widget_preview_subject_math),
            context.getString(R.string.widget_preview_subject_programming),
            context.getString(R.string.widget_preview_subject_physics),
            context.getString(R.string.widget_preview_teacher)
        )
        // At the scenario's 12:50 the second lesson is still on: the preview shows a current lesson, not the next one.
        val settings = ScheduleWidgetSettings(compact = CompactScheduleWidgetSettings(showNextLessonEarly = false))
        val snapshot = SchedulePreviewScenario(ScheduleWidgetSelector()).snapshot(settings, evening = false, labels)
        val rows = ScheduleListRowRenderer(context)

        Screenshots.save("$DIRECTORY-$variant", "widget_single_lesson_preview") {
            onMain {
                val parent = FrameLayout(context)
                val view = ScheduleWidgetRenderer.singleLessonViews(context, snapshot).apply(context, parent)
                drawn(view, SINGLE_WIDTH_DP, SINGLE_HEIGHT_DP)
            }
        }
        Screenshots.save("$DIRECTORY-$variant", "widget_lesson_list_preview") {
            onMain {
                val root = FrameLayout(context)
                LayoutInflater.from(context).inflate(R.layout.widget_lesson_list, root, true)
                root.findViewById<ListView>(R.id.lesson_list).adapter = object : BaseAdapter() {
                    override fun getCount() = snapshot.lessonList.size
                    override fun getItem(position: Int) = snapshot.lessonList[position]
                    override fun getItemId(position: Int) = position.toLong()
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                        checkNotNull(rows.render(getItem(position), snapshot.lessonListStyle, snapshot.resolvedFullTextSize))
                            .apply(context, parent)
                }
                drawn(root, LIST_WIDTH_DP, LIST_HEIGHT_DP)
            }
        }
        Screenshots.save("$DIRECTORY-$variant", "widget_qr_code_preview") {
            val generator = QrCodeGenerator()
            val renderer = QrBitmapRenderer(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
            val size = (QR_SIZE_DP * context.resources.displayMetrics.density).toInt()
            renderer.render(
                qrCode = generator.toBooleans(generator.generate(QR_SAMPLE)),
                size = size,
                backgroundColor = android.graphics.Color.WHITE,
                foregroundColor = android.graphics.Color.BLACK,
                relativePadding = 0.05f,
                relativeRounding = 0.06f
            )
        }

        // The static Android 12+ preview layouts, drawn the same way, to compare against the real ones.
        for ((name, layout, width, height) in listOf(
            Layout("layout_single_lesson", R.layout.widget_single_lesson_preview, SINGLE_WIDTH_DP, SINGLE_HEIGHT_DP),
            Layout("layout_lesson_list", R.layout.widget_lesson_list_preview, LIST_WIDTH_DP, LIST_HEIGHT_DP),
            Layout("layout_qr_code", R.layout.widget_qr_code_preview, QR_SIZE_DP, QR_SIZE_DP)
        )) {
            Screenshots.save("$DIRECTORY-$variant", name) {
                onMain { drawn(LayoutInflater.from(context).inflate(layout, FrameLayout(context), false), width, height) }
            }
        }
    }

    private data class Layout(val name: String, val layout: Int, val width: Int, val height: Int)

    private fun drawn(view: View, widthDp: Int, heightDp: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        return checkNotNull(result)
    }

    private companion object {
        const val DIRECTORY = "widget-previews"
        // 4 x 1 and 4 x 2 launcher cells on a typical phone; the picker scales the image anyway.
        const val SINGLE_WIDTH_DP = 300
        const val SINGLE_HEIGHT_DP = 84
        const val LIST_WIDTH_DP = 300
        const val LIST_HEIGHT_DP = 190
        const val QR_SIZE_DP = 140
        const val QR_SAMPLE = "ITMO-TEST"
    }
}
