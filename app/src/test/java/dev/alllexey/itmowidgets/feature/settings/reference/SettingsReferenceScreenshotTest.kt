package dev.alllexey.itmowidgets.feature.settings.reference

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.DefaultWidgetPreviewFactory
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.qr.CustomSpoilerManager
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapRenderer
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrColorResolver
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrPreviewBitmapCache
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsRenderer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * XML references of the 11 settings pages and the privacy loading, error and disabled states, under the names of
 * LT-4a's `SettingsScreen` previews. Each page is `fragment_settings` bound the way `SettingsFragment` binds it, with
 * the sections the real page providers build over read-only fakes (custom services on, notifications
 * allowed, no background restriction) and, on the three widget pages, the real widget preview at a fixed clock.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SettingsReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-settings")

    /** The scopes of the view models' widget previews, cancelled after the test. */
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun cancelPreviews() = scopes.forEach { it.cancel() }

    @Test fun root() = page(SettingsPage.ROOT)

    @Test fun services() = page(SettingsPage.SERVICES)

    @Test fun privacy() = page(SettingsPage.PRIVACY, sharing = SharingSettingsState.Content(SharingSettings()))

    @Test fun privacyLoading() = page(SettingsPage.PRIVACY, state = "loading", privacyLoaded = false)

    @Test fun privacyError() = page(SettingsPage.PRIVACY, state = "error", sharing = SharingSettingsState.Error)

    @Test fun privacyDisabled() = page(
        SettingsPage.PRIVACY,
        state = "disabled",
        local = LocalSettings(customServicesEnabled = false)
    )

    @Test fun compactScheduleWidget() = page(SettingsPage.COMPACT_SCHEDULE_WIDGET)

    @Test fun fullScheduleWidget() = page(SettingsPage.FULL_SCHEDULE_WIDGET)

    @Test fun qrWidget() = page(SettingsPage.QR_WIDGET)

    @Test fun home() = page(SettingsPage.HOME)

    @Test fun schedule() = page(SettingsPage.SCHEDULE)

    @Test fun recordbook() = page(SettingsPage.RECORDBOOK)

    @Test fun sport() = page(SettingsPage.SPORT)

    @Test fun maintenance() = page(SettingsPage.MAINTENANCE)

    /** `SettingsScreen_<page>[-<state>]`, the page in kebab case as a preview name allows. */
    private fun page(
        page: SettingsPage,
        state: String? = null,
        local: LocalSettings = LocalSettings(customServicesEnabled = true),
        sharing: SharingSettingsState = SharingSettingsState.Disabled,
        privacyLoaded: Boolean = true
    ) {
        val name = listOfNotNull(page.name.lowercase().replace('_', '-'), state).joinToString("-")
        references.layout("SettingsScreen_$name", R.layout.fragment_settings) { view ->
            val viewModel = referenceSettingsViewModel(local, sharing, page)
            viewModel.onNotificationPermissionChanged(true)
            viewModel.onBackgroundWorkChanged()
            // Privacy masks cached values for its minimum loading time before it shows the fresh ones.
            settle(if (privacyLoaded) PRIVACY_LOADING_MS else 0) { viewModel.uiState.value.loaded }
            val ui = viewModel.uiState.value
            view.findViewById<TextView>(R.id.settings_title).text = page.title.resolve(view.context)
            view.findViewById<View>(R.id.settings_progress).isVisible =
                ui.page == SettingsPage.PRIVACY && ui.loaded && ui.sections.isEmpty()
            view.findViewById<View>(R.id.settings_scroll).isVisible = ui.sections.isNotEmpty()
            SettingsRenderer(view.findViewById(R.id.sections_container), { _, _ -> }, {}, {}, {}).render(ui.sections)
            ui.previewSettings?.let { settings ->
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also(scopes::add)
                val preview = previewFactory(view).create(view.context, scope, settings)
                view.findViewById<FrameLayout>(R.id.widget_preview_container).apply {
                    addView(preview.view)
                    isVisible = true
                }
                var ready = false
                scope.launch {
                    preview.awaitReady()
                    ready = true
                }
                settle { ready }
            }
        }
    }

    /** The factory `SettingsFragment` gets from Hilt, with the QR noise at a fixed hour and no custom image. */
    private fun previewFactory(view: View) = DefaultWidgetPreviewFactory(
        QrPreviewBitmapCache(
            QrCodeGenerator(),
            QrBitmapRenderer(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            CustomSpoilerManager(view.context.applicationContext),
            AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default, main = Dispatchers.Main)
        ),
        QrColorResolver(view.context.applicationContext, PreviewQrPreferences),
        SchedulePreviewScenario(ScheduleWidgetSelector())
    )

    private object PreviewQrPreferences : QrAppearancePreferences {
        override suspend fun useDynamicColors() = true
        override suspend fun isSpoilerEnabled() = true
        override suspend fun spoilerAnimationType() = QrAnimationType.CIRCLE
    }

    private companion object {
        /** Past `SettingsViewModel`'s minimum privacy loading time. */
        const val PRIVACY_LOADING_MS = 1_000L
    }
}
