package dev.alllexey.itmowidgets.feature.onboarding.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.WidgetProviders
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.permission.notificationPermissionIsRuntime
import dev.alllexey.itmowidgets.core.ui.permission.openAppNotificationSettings
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropResult
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPinRequester
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingEvent
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetKind
import kotlinx.coroutines.CoroutineScope

/** The flow's root tag, which the routing tests look up. */
@VisibleForTesting
const val ONBOARDING_ROOT_TEST_TAG = "onboarding_root"

/**
 * `OnboardingScreen` on the surface, the footer clearing the navigation bar and the surface running behind it, tagged
 * [ONBOARDING_ROOT_TEST_TAG]. The Compose shell's onboarding entry calls it.
 */
@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    actions: OnboardingActions,
    widgetPreview: @Composable (WidgetKind, Modifier) -> Unit,
    snackbars: SnackbarHostState,
) {
    Box(Modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        OnboardingScreen(
            state = state,
            actions = actions,
            widgetPreview = widgetPreview,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .testTag(ONBOARDING_ROOT_TEST_TAG),
            notificationPermissionIsRuntime = notificationPermissionIsRuntime(Build.VERSION.SDK_INT),
            snackbarHostState = snackbars,
        )
    }
}

/** The flow's actions over [viewModel]; picking the spoiler image and opening a link are the host's. */
fun onboardingActions(
    viewModel: OnboardingViewModel,
    onPickSpoilerImage: () -> Unit,
    onOpenLink: (url: String) -> Unit,
) = OnboardingActions(
    onNext = { viewModel.next() },
    onSkip = { viewModel.skip() },
    onOption = { option, enabled -> viewModel.setOption(option, enabled) },
    onTextSize = { kind, size -> viewModel.setTextSize(kind, size) },
    onPickSpoilerImage = onPickSpoilerImage,
    onResetSpoilerImage = { viewModel.resetSpoilerImage() },
    onPinWidget = { kind -> viewModel.pinWidget(kind) },
    onServicesEnabled = { enabled -> viewModel.setServicesEnabled(enabled) },
    onRequestNotifications = { viewModel.requestNotifications() },
    onOpenLink = onOpenLink,
)

/** Back is a step back from the second step until the flow is finished; the host leaves it on its own then. */
val OnboardingUiState.canStepBack: Boolean get() = stepIndex > 0 && !finished

/**
 * What only Android does for one event: the launcher pin, the notification permission or the settings page, and the
 * snackbars. The Compose shell's onboarding entry calls it.
 */
fun Context.handleOnboardingEvent(
    event: OnboardingEvent,
    pinRequester: WidgetPinRequester,
    notificationPermission: ActivityResultLauncher<Unit>,
    showSnackbar: (message: Int) -> Unit,
) {
    when (event) {
        is OnboardingEvent.RequestPinWidget -> pinRequester.request(event.kind.providerClassName)
        OnboardingEvent.RequestNotificationPermission ->
            if (notificationPermissionIsRuntime(Build.VERSION.SDK_INT)) notificationPermission.launch(Unit)
            else openAppNotificationSettings()
        OnboardingEvent.OpenNotificationSettings -> openAppNotificationSettings()
        OnboardingEvent.SpoilerImageFailed -> showSnackbar(R.string.settings_qr_custom_image_failed)
        is OnboardingEvent.ShowError -> showSnackbar(event.error.messageRes())
    }
}

/** The pin the launcher confirmed, by its provider class name; other providers are not the flow's. */
fun OnboardingViewModel.onWidgetPinnedProvider(provider: String) {
    WidgetKind.entries.firstOrNull { it.providerClassName == provider }?.let(::onWidgetPinned)
}

/** The crop screen's answer: a stored image, a failure for [onFailed], or nothing when cancelled. */
fun OnboardingViewModel.onSpoilerCropped(result: SpoilerCropResult, onFailed: () -> Unit) {
    when (result) {
        is SpoilerCropResult.Image -> saveSpoilerImage(result.uri.toString())
        SpoilerCropResult.Failed -> onFailed()
        SpoilerCropResult.Cancelled -> Unit
    }
}

/** Covers both the runtime permission and the system switch on older Android; hosts call it on every resume. */
fun OnboardingViewModel.onNotificationsEnabled(context: Context) {
    onNotificationPermission(NotificationManagerCompat.from(context).areNotificationsEnabled())
}

/** Opens a link of the flow in another app; [onFailed] when none can. */
fun Context.openOnboardingLink(url: String, onFailed: () -> Unit) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        onFailed()
    }
}

/**
 * The real widget previews of one host: one preview per composed page, built in the
 * factory, bound to every appearance change, re-read when a new spoiler image is stored, closed on release.
 */
class OnboardingWidgetPreviews(private val factory: WidgetPreviewFactory) {
    private val previews = linkedMapOf<WidgetKind, WidgetPreview>()

    /** The preview view of each composed page. */
    val views: Map<WidgetKind, View> get() = previews.mapValues { (_, preview) -> preview.view }

    /**
     * The real widget of [kind] once the stored appearance answers, its work in [scope]. Rows wait for the appearance
     * too, so the card stays empty until then.
     */
    @Composable
    fun Slot(kind: WidgetKind, state: OnboardingUiState, scope: CoroutineScope, modifier: Modifier) {
        val appearance = state.appearance
        if (appearance == null) {
            Box(modifier)
            return
        }
        val settings = kind.previewSettings(appearance)
        val revision = state.spoilerRevision
        val drawn = remember(kind) { DrawnRevision() }
        AndroidView(
            factory = { context ->
                factory.create(context, scope, settings).also { preview ->
                    previews.put(kind, preview)?.close()
                    drawn.value = revision
                }.view
            },
            modifier = modifier,
            update = {
                val preview = previews[kind] ?: return@AndroidView
                preview.bind(settings)
                if (drawn.value != revision) preview.refresh()
                drawn.value = revision
            },
            onRelease = { view ->
                val preview = previews[kind]?.takeIf { it.view === view } ?: return@AndroidView
                previews.remove(kind)
                preview.close()
            },
        )
    }

    /** The host stopped: the previews stop their work. */
    fun stop() = previews.values.forEach(WidgetPreview::stop)

    /** Closes every preview a composition has not released. */
    fun close() {
        previews.values.forEach(WidgetPreview::close)
        previews.clear()
    }

    /** The spoiler revision a preview last drew; a newer one re-reads the stored image. */
    private class DrawnRevision {
        var value: Int? = null
    }
}

private fun WidgetKind.previewSettings(appearance: WidgetAppearance): WidgetPreviewSettings =
    when (this) {
        WidgetKind.SINGLE_LESSON -> WidgetPreviewSettings.Schedule(appearance.schedule, ScheduleWidgetFormat.COMPACT)
        WidgetKind.DAY_SCHEDULE -> WidgetPreviewSettings.Schedule(appearance.schedule, ScheduleWidgetFormat.FULL)
        WidgetKind.QR -> WidgetPreviewSettings.Qr(appearance.qr)
    }

private val WidgetKind.providerClassName: String
    get() = when (this) {
        WidgetKind.SINGLE_LESSON -> WidgetProviders.SINGLE_LESSON
        WidgetKind.DAY_SCHEDULE -> WidgetProviders.DAY_SCHEDULE
        WidgetKind.QR -> WidgetProviders.QR_CODE
    }
