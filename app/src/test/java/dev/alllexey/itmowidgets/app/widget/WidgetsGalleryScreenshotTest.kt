package dev.alllexey.itmowidgets.app.widget

import android.content.Context
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.viewinterop.AndroidView
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFrame
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.LocalPreviewAppearance
import dev.alllexey.itmowidgets.designsystem.preview.PreviewAppearance
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapRenderer
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleSettingsPreview
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsActions
import dev.alllexey.itmowidgets.feature.settings.ui.widgets.WidgetsGalleryActions
import dev.alllexey.itmowidgets.feature.settings.ui.widgets.WidgetsGalleryScreen
import dev.alllexey.itmowidgets.feature.settings.ui.widgets.WidgetsGalleryTags
import dev.alllexey.itmowidgets.feature.settings.ui.widgets.widgetsGallerySample
import dev.alllexey.itmowidgets.feature.sport.ui.widget.SportSettingsPreview
import dev.alllexey.itmowidgets.testkit.screenshot.CaptureSize
import dev.alllexey.itmowidgets.testkit.screenshot.ShotsCompare
import dev.alllexey.itmowidgets.testkit.screenshot.ShotsRun
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `Виджеты` in `app/screenshots/WidgetsGallery*` with the live previews the app draws: each card and the open sheet
 * show the real widget's RemoteViews on the sample day (the QR card its code), unlike `:shared:feature-settings`'s
 * placeholders. No preview renders these, so `references.txt` lists them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class WidgetsGalleryScreenshotTest(private val case: Case, private val appearance: PreviewAppearance) {

    enum class Case(val base: String, val sheet: WidgetFormat?, val scrollTo: WidgetFormat? = null) {
        Gallery("WidgetsGalleryLive", null),
        GalleryEnd("WidgetsGalleryLiveEnd", null, scrollTo = WidgetFormat.PASS),
        NextSheet("WidgetsGalleryLiveNext", WidgetFormat.NEXT),
        DaySheet("WidgetsGalleryLiveDay", WidgetFormat.DAY),
        ListSheet("WidgetsGalleryLiveList", WidgetFormat.LIST),
    }

    private val window = object : ExternalResource() {
        override fun before() {
            RuntimeEnvironment.setQualifiers(CaptureSize().qualifiers(appearance))
            RuntimeEnvironment.setFontScale(appearance.fontScale)
        }
    }

    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(window).around(compose)

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun capture() {
        compose.setContent {
            CompositionLocalProvider(LocalPreviewAppearance provides appearance) {
                ItmoPreview {
                    WidgetsGalleryScreen(
                        state = widgetsGallerySample(),
                        actions = SettingsActions(),
                        galleryActions = WidgetsGalleryActions(),
                        widgetPreview = { settings, modifier ->
                            AndroidView(factory = { context -> widget(context, settings) }, modifier = modifier)
                        },
                        initialSheet = case.sheet,
                    )
                }
            }
        }
        case.scrollTo?.let { compose.onNodeWithTag(WidgetsGalleryTags.card(it)).performScrollTo() }
        compose.waitForIdle()
        captureScreenRoboImage(AppScreenshotRule.appBaselines.file(case.base, appearance), ShotsCompare.options)
    }

    /** The app's own preview views; the pass shows its code in the widget's frame (the app loads it off the main thread). */
    private fun widget(context: Context, settings: WidgetPreviewSettings): View = when (settings) {
        is WidgetPreviewSettings.Schedule -> ScheduleSettingsPreview(context, scenario).apply { bind(settings) }.view
        is WidgetPreviewSettings.Sport -> SportSettingsPreview(context).apply { bind(settings) }.view
        is WidgetPreviewSettings.Qr -> WidgetPreviewFrame(context).apply {
            size = SizeF(SportSettingsPreview.SIZE_DP, SportSettingsPreview.SIZE_DP)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(ImageView(context).apply {
                val generator = QrCodeGenerator()
                setImageBitmap(
                    QrBitmapRenderer(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)).render(
                        generator.toBooleans(generator.generate("ITMO-TEST")), 300, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0.05f, 0.06f
                    )
                )
            })
        }
    }

    companion object {
        private val scenario = SchedulePreviewScenario(ScheduleWidgetSelector())

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> = Case.entries.flatMap { case ->
            AppScreenshotRule.appBaselines.appearances(case.base, ShotsRun.fullMatrix).map { arrayOf(case, it) }
        }
    }
}
