package dev.alllexey.itmowidgets.feature.onboarding.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.WidgetProviders
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.permission.RequestNotificationPermission
import dev.alllexey.itmowidgets.core.ui.permission.notificationPermissionIsRuntime
import dev.alllexey.itmowidgets.core.ui.permission.openAppNotificationSettings
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropResult
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerImagePicker
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPinRequester
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingEvent
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetKind
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The first-run flow (`onboarding`): [OnboardingContent] in a `ComposeView`, with the real widget previews of
 * [OnboardingWidgetPreviews] in its preview slot. The ViewModel is Koin's, in this Fragment's store.
 *
 * Every event of the shared ViewModel is handled here, through [handleOnboardingEvent]. Each event is delivered once,
 * to one collector; a second collector would take events away from this one. Only Android work stays here: the
 * launcher pin, the notification permission and settings page, the spoiler photo picker and crop screen, and opening
 * a link. The Compose shell's onboarding entry hosts the same pieces.
 */
@AndroidEntryPoint
class OnboardingFragment : Fragment() {

    @Inject lateinit var previewFactory: WidgetPreviewFactory

    private val viewModel: OnboardingViewModel by viewModel()
    private lateinit var pinRequester: WidgetPinRequester
    private var backCallback: OnBackPressedCallback? = null
    private val snackbars = SnackbarHostState()
    private val previews by lazy { OnboardingWidgetPreviews(previewFactory) }

    /** The preview view of each composed widget page, for instrumented tests. */
    @get:VisibleForTesting
    val previewViews: Map<WidgetKind, View> get() = previews.views

    private val notificationPermissionLauncher =
        registerForActivityResult(RequestNotificationPermission()) { granted ->
            viewModel.onNotificationPermission(granted)
        }

    private val spoilerImagePicker = SpoilerImagePicker(this) { result ->
        viewModel.onSpoilerCropped(result) { showSnackbar(R.string.settings_qr_custom_image_failed) }
    }

    // Lazy: the ViewModel exists only once the Fragment is attached.
    private val actions by lazy {
        onboardingActions(
            viewModel,
            onPickSpoilerImage = { spoilerImagePicker.launch() },
            onOpenLink = { url ->
                requireContext().openOnboardingLink(url) { showSnackbar(R.string.link_open_failed) }
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pinRequester = WidgetPinRequester(requireContext())
        // The launcher confirms the pin while this screen is stopped, so the receiver
        // lives as long as the Fragment, not as long as its view.
        pinRequester.start(viewModel::onWidgetPinnedProvider)
        viewModel.onPinSupportChanged(pinRequester.isSupported)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView { OnboardingRoute() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewFactory.preload(requireContext(), viewLifecycleOwner.lifecycleScope)

        // Back leaves the flow only from its first step; further in it is a step back.
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = viewModel.back()
        }.also {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
        }

        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            // The activity leaves the flow on its own once it is finished; until then back stays a step back.
            .onEach { state -> backCallback?.isEnabled = state.canStepBack }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.events
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { event ->
                requireContext().handleOnboardingEvent(
                    event,
                    pinRequester,
                    notificationPermissionLauncher,
                    ::showSnackbar,
                )
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onNotificationsEnabled(requireContext())
    }

    override fun onStop() {
        previews.stop()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        backCallback = null
        // The composition releases its previews first; this covers a view destroyed without that.
        previews.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        pinRequester.stop()
    }

    /** The screen over this Fragment's ViewModel. */
    @Composable
    private fun OnboardingRoute() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        OnboardingContent(
            state = state,
            actions = actions,
            widgetPreview = { kind, modifier ->
                previews.Slot(kind, state, viewLifecycleOwner.lifecycleScope, modifier)
            },
            snackbars = snackbars,
        )
    }

    private fun showSnackbar(@StringRes message: Int) {
        if (view == null) return
        val text = getString(message)
        viewLifecycleOwner.lifecycleScope.launch {
            snackbars.showSnackbar(text, duration = SnackbarDuration.Long)
        }
    }

    companion object {
        /** The flow's root, tagged like the root id of the View flow, so routing tests keep their lookup. */
        @VisibleForTesting
        const val ROOT_TEST_TAG = "onboarding_root"
    }
}

/**
 * `OnboardingScreen` on the surface, the footer clearing the navigation bar and the surface running behind it, tagged
 * [OnboardingFragment.ROOT_TEST_TAG]. Both hosts call it (this Fragment and the Compose shell's onboarding entry).
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
                .testTag(OnboardingFragment.ROOT_TEST_TAG),
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
 * snackbars. Both hosts call it.
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
 * The real widget previews of one host (recipe platform-view-slot): one preview per composed page, built in the
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
