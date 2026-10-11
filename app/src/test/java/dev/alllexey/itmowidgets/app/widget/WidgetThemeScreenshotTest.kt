package dev.alllexey.itmowidgets.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.preview.PreviewAppearance
import dev.alllexey.itmowidgets.designsystem.theme.widgetPalette
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapRenderer
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrColorResolver
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewLabels
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleListRowRenderer
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetRenderer
import dev.alllexey.itmowidgets.testkit.screenshot.CaptureSize
import dev.alllexey.itmowidgets.testkit.screenshot.ShotsCompare
import dev.alllexey.itmowidgets.testkit.screenshot.ShotsRun
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * «Виджеты в цвет темы» in `app/screenshots/WidgetTheme_*`: the compact and the full schedule widget and the QR code
 * through the real renderers with their own colours (`off`) and in the teal preset's palette (`teal`), light and dark.
 * The QR code's `off` is the resolver's fallback, black on white: the test application's theme has no Material
 * attributes. No preview renders these, so `references.txt` lists them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class WidgetThemeScreenshotTest(
    private val case: Case,
    private val theme: ThemeSpec?,
    private val appearance: PreviewAppearance,
) {

    enum class Case(val state: String, val widthDp: Int, val heightDp: Int) {
        Compact("compact", widthDp = 300, heightDp = 84),
        Full("full", widthDp = 300, heightDp = 190),
        Qr("qr", widthDp = 140, heightDp = 140),
    }

    @Test
    fun capture() {
        RuntimeEnvironment.setQualifiers(CaptureSize().qualifiers(appearance))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val palette = theme?.widgetPalette(context)
        val bitmap = when (case) {
            Case.Compact -> drawn(context, ScheduleWidgetRenderer.singleLessonViews(context, snapshot(context), palette)
                .apply(context, FrameLayout(context)))
            Case.Full -> drawn(context, dayList(context, palette))
            Case.Qr -> qrCode(context, palette)
        }
        bitmap.captureRoboImage(AppScreenshotRule.appBaselines.file(base(case, theme), appearance), ShotsCompare.options)
    }

    /** The day list as the settings preview builds it: the widget's shell, its rows through a local adapter. */
    private fun dayList(context: Context, palette: WidgetPalette?): View {
        val snapshot = snapshot(context)
        val rows = ScheduleListRowRenderer(context, palette)
        val root = ScheduleWidgetRenderer.listShellViews(context, palette).apply(context, FrameLayout(context))
        root.findViewById<ListView>(R.id.lesson_list).adapter = object : BaseAdapter() {
            override fun getCount() = snapshot.lessonList.size
            override fun getItem(position: Int) = snapshot.lessonList[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                checkNotNull(rows.render(getItem(position), snapshot.lessonListStyle, snapshot.resolvedFullTextSize))
                    .apply(context, parent)
        }
        return root
    }

    /** The code in the colours the widget takes with `Динамические цвета` on. */
    private fun qrCode(context: Context, palette: WidgetPalette?): Bitmap {
        val resolver = QrColorResolver(context, DynamicColorsOn) { null }
        val (background, foreground) = resolver.getQrColors(context, dynamic = true, palette = palette)
        val generator = QrCodeGenerator()
        return QrBitmapRenderer(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)).render(
            qrCode = generator.toBooleans(generator.generate(QR_SAMPLE)),
            size = px(context, case.widthDp),
            backgroundColor = background,
            foregroundColor = foreground,
            relativePadding = 0.05f,
            relativeRounding = 0.06f
        )
    }

    /** The settings preview's lesson time: a current lesson and the ones after it. */
    private fun snapshot(context: Context): ScheduleWidgetSnapshot {
        val labels = SchedulePreviewLabels(
            context.getString(R.string.widget_preview_subject_history),
            context.getString(R.string.widget_preview_subject_math),
            context.getString(R.string.widget_preview_subject_programming),
            context.getString(R.string.widget_preview_subject_physics),
            context.getString(R.string.widget_preview_teacher)
        )
        val settings = ScheduleWidgetSettings(compact = CompactScheduleWidgetSettings(showNextLessonEarly = false))
        return SchedulePreviewScenario(ScheduleWidgetSelector()).snapshot(settings, evening = false, labels)
    }

    private fun drawn(context: Context, view: View): Bitmap {
        val width = px(context, case.widthDp)
        val height = px(context, case.heightDp)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun px(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    private object DynamicColorsOn : QrAppearancePreferences {
        override suspend fun useDynamicColors() = true
        override suspend fun isSpoilerEnabled() = true
        override suspend fun spoilerAnimationType() = QrAnimationType.CIRCLE
    }

    companion object {
        private const val QR_SAMPLE = "ITMO-TEST"

        private fun base(case: Case, theme: ThemeSpec?) =
            "WidgetTheme_${case.state}-${theme?.accent?.name?.lowercase() ?: "off"}"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}_{2}")
        fun cases(): List<Array<Any?>> = Case.entries.flatMap { case ->
            listOf(null, ThemeSpec(accent = AccentColor.TEAL)).flatMap { theme ->
                AppScreenshotRule.appBaselines.appearances(base(case, theme), ShotsRun.fullMatrix)
                    .map { arrayOf(case, theme, it) }
            }
        }
    }
}
