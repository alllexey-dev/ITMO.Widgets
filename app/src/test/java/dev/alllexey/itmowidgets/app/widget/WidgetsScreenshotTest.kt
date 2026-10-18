package dev.alllexey.itmowidgets.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.preview.PreviewAppearance
import dev.alllexey.itmowidgets.designsystem.theme.widgetPalette
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapRenderer
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrColorResolver
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewMoment
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.NextLessonWidgetRenderer
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleSettingsPreview
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSampleState
import dev.alllexey.itmowidgets.feature.sport.ui.widget.SportSettingsPreview
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
 * Every widget in `app/screenshots/Widget_*` through its real RemoteViews renderer on the sample day
 * (`SchedulePreviewScenario`, `SportWidgetSamples`), with the widgets' own colours (`off`) and in the teal preset's
 * palette (`teal`, «Виджеты в цвет темы»), light and dark. The launcher previews (`WidgetPreviewImages`) are drawn the
 * same way. No preview renders these, so `references.txt` lists them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class WidgetsScreenshotTest(
    private val case: Case,
    private val theme: ThemeSpec?,
    private val appearance: PreviewAppearance,
) {

    enum class Case(val state: String, val widthDp: Int, val heightDp: Int) {
        NextSmall("next-small", 170, 170),
        NextMedium("next-medium", 340, 180),
        NextMorning("next-morning", 340, 180),
        NextGap("next-gap", 340, 180),
        NextEvening("next-evening", 340, 180),
        NextSignedOut("next-signed-out", 170, 170),
        Day("day", 320, 330),
        DayMorning("day-morning", 320, 330),
        DayEvening("day-evening", 320, 330),
        List("list", 320, 250),
        ListEvening("list-evening", 320, 250),
        SportSmall("sport-small", 170, 170),
        SportQueue("sport-queue", 340, 170),
        SportBooked("sport-booked", 340, 170),
        SportDone("sport-done", 340, 170),
        Qr("qr", 140, 140),
    }

    @Test
    fun capture() {
        RuntimeEnvironment.setQualifiers(CaptureSize().qualifiers(appearance))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = if (case == Case.Qr) {
            qrCode(context, theme?.widgetPalette(context))
        } else {
            render(context, view(context, case, theme), case.widthDp, case.heightDp)
        }
        bitmap.captureRoboImage(AppScreenshotRule.appBaselines.file(base(case, theme), appearance), ShotsCompare.options)
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

    private object DynamicColorsOn : QrAppearancePreferences {
        override suspend fun useDynamicColors() = true
        override suspend fun isSpoilerEnabled() = true
        override suspend fun spoilerAnimationType() = QrAnimationType.CIRCLE
    }

    companion object {
        private const val QR_SAMPLE = "ITMO-TEST"
        private val scenario = SchedulePreviewScenario(ScheduleWidgetSelector())

        /** [case] as the launcher would draw it; the launcher previews use it too. */
        fun view(context: Context, case: Case, theme: ThemeSpec?): View {
            val list = { moment: SchedulePreviewMoment ->
                ScheduleSettingsPreview.view(context, scenario, WidgetPreviewSettings.Schedule(ScheduleWidgetSettings(), ScheduleWidgetFormat.LIST, theme), moment)
            }
            return when (case) {
                Case.List -> list(SchedulePreviewMoment.LESSON)
                Case.ListEvening -> list(SchedulePreviewMoment.EVENING)
                else -> views(context, case, theme).apply(context, FrameLayout(context))
            }
        }

        /** The RemoteViews of [case]; the classic list is a collection, drawn by [view]. */
        private fun views(context: Context, case: Case, theme: ThemeSpec?): RemoteViews {
            val schedule = { format: ScheduleWidgetFormat, moment: SchedulePreviewMoment ->
                ScheduleSettingsPreview.views(context, scenario, WidgetPreviewSettings.Schedule(ScheduleWidgetSettings(), format, theme), moment)
            }
            val sport = { medium: Boolean, state: SportWidgetSampleState ->
                SportSettingsPreview.views(context, WidgetPreviewSettings.Sport(theme), medium, state)
            }
            return when (case) {
                Case.NextSmall -> NextLessonWidgetRenderer.layout(
                    context, scenario.snapshot(ScheduleWidgetSettings(), SchedulePreviewMoment.LESSON, ScheduleSettingsPreview.labels(context)),
                    theme?.widgetPalette(context),
                    scenario.instant(SchedulePreviewMoment.LESSON),
                    widgetId = null, medium = false,
                )
                Case.NextMedium -> schedule(ScheduleWidgetFormat.COMPACT, SchedulePreviewMoment.LESSON)
                Case.NextMorning -> schedule(ScheduleWidgetFormat.COMPACT, SchedulePreviewMoment.MORNING)
                Case.NextGap -> schedule(ScheduleWidgetFormat.COMPACT, SchedulePreviewMoment.GAP)
                Case.NextEvening -> schedule(ScheduleWidgetFormat.COMPACT, SchedulePreviewMoment.EVENING)
                Case.NextSignedOut -> NextLessonWidgetRenderer.layout(
                    context, ScheduleWidgetSnapshot.signedOut(), theme?.widgetPalette(context),
                    scenario.instant(SchedulePreviewMoment.LESSON), widgetId = null, medium = false,
                )
                Case.Day -> schedule(ScheduleWidgetFormat.FULL, SchedulePreviewMoment.LESSON)
                Case.DayMorning -> schedule(ScheduleWidgetFormat.FULL, SchedulePreviewMoment.MORNING)
                Case.DayEvening -> schedule(ScheduleWidgetFormat.FULL, SchedulePreviewMoment.EVENING)
                Case.SportSmall -> sport(false, SportWidgetSampleState.QUEUE)
                Case.SportQueue -> sport(true, SportWidgetSampleState.QUEUE)
                Case.SportBooked -> sport(true, SportWidgetSampleState.BOOKED)
                Case.SportDone -> sport(true, SportWidgetSampleState.DONE)
                Case.List, Case.ListEvening -> error("The classic list is a collection")
                Case.Qr -> error("The QR widget is a bitmap")
            }
        }

        /** [view] laid out at [widthDp] x [heightDp] and drawn. */
        fun render(context: Context, view: View, widthDp: Int, heightDp: Int): Bitmap {
            val width = px(context, widthDp)
            val height = px(context, heightDp)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }

        private fun px(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

        private fun base(case: Case, theme: ThemeSpec?) =
            "Widget_${case.state}-${theme?.accent?.name?.lowercase() ?: "off"}"

        /** The teal palette is checked on the widgets that draw the most roles. */
        private val THEMED = setOf(Case.NextMedium, Case.Day, Case.List, Case.SportQueue, Case.Qr)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}_{2}")
        fun cases(): List<Array<Any?>> = Case.entries.flatMap { case ->
            (listOf<ThemeSpec?>(null) + listOfNotNull(ThemeSpec(accent = AccentColor.TEAL).takeIf { case in THEMED })).flatMap { theme ->
                AppScreenshotRule.appBaselines.appearances(base(case, theme), ShotsRun.fullMatrix)
                    .map { arrayOf(case, theme, it) }
            }
        }
    }
}
