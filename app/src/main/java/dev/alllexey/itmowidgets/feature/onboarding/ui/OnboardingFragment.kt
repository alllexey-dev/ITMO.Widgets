package dev.alllexey.itmowidgets.feature.onboarding.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The first-run flow (`onboarding`): `OnboardingScreen` from `:shared:feature-account` in a `ComposeView`, with the
 * real widget previews in its preview slot. The ViewModel is Koin's, in this Fragment's store.
 *
 * Every event of the shared ViewModel is handled here. Each event is delivered once, to one collector; a second
 * collector would take events away from this one. Only Android work stays here: the launcher pin, the notification
 * permission and settings page, the spoiler photo picker and crop screen, and opening a link.
 */
@AndroidEntryPoint
class OnboardingFragment : Fragment() {

    @Inject lateinit var previewFactory: WidgetPreviewFactory

    private val viewModel: OnboardingViewModel by viewModel()
    private lateinit var pinRequester: WidgetPinRequester
    private var backCallback: OnBackPressedCallback? = null
    private val snackbars = SnackbarHostState()
    private val previews = linkedMapOf<WidgetKind, WidgetPreview>()

    /** The preview view of each composed widget page, for instrumented tests. */
    @get:VisibleForTesting
    val previewViews: Map<WidgetKind, View> get() = previews.mapValues { (_, preview) -> preview.view }

    private val notificationPermissionLauncher =
        registerForActivityResult(RequestNotificationPermission()) { granted ->
            viewModel.onNotificationPermission(granted)
        }

    private val spoilerImagePicker = SpoilerImagePicker(this) { result ->
        when (result) {
            is SpoilerCropResult.Image -> viewModel.saveSpoilerImage(result.uri.toString())
            SpoilerCropResult.Failed -> showSnackbar(R.string.settings_qr_custom_image_failed)
            SpoilerCropResult.Cancelled -> Unit
        }
    }

    private val actions = OnboardingActions(
        onNext = { viewModel.next() },
        onSkip = { viewModel.skip() },
        onOption = { option, enabled -> viewModel.setOption(option, enabled) },
        onTextSize = { kind, size -> viewModel.setTextSize(kind, size) },
        onPickSpoilerImage = { spoilerImagePicker.launch() },
        onResetSpoilerImage = { viewModel.resetSpoilerImage() },
        onPinWidget = { kind -> viewModel.pinWidget(kind) },
        onServicesEnabled = { enabled -> viewModel.setServicesEnabled(enabled) },
        onRequestNotifications = { viewModel.requestNotifications() },
        onOpenLink = { url -> openLink(url) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pinRequester = WidgetPinRequester(requireContext())
        // The launcher confirms the pin while this screen is stopped, so the receiver
        // lives as long as the Fragment, not as long as its view.
        pinRequester.start { provider ->
            WidgetKind.entries.firstOrNull { it.providerClassName == provider }?.let(viewModel::onWidgetPinned)
        }
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
            .onEach { state -> backCallback?.isEnabled = state.stepIndex > 0 && !state.finished }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.events
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::handle)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onResume() {
        super.onResume()
        // Covers both the runtime permission and the system switch on older Android.
        viewModel.onNotificationPermission(
            NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        )
    }

    override fun onStop() {
        previews.values.forEach(WidgetPreview::stop)
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        backCallback = null
        // The composition releases its previews first; this covers a view destroyed without that.
        previews.values.forEach(WidgetPreview::close)
        previews.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        pinRequester.stop()
    }

    /** The screen over this Fragment's ViewModel; the footer clears the navigation bar, the surface runs behind it. */
    @Composable
    private fun OnboardingRoute() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        Box(Modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
            OnboardingScreen(
                state = state,
                actions = actions,
                widgetPreview = { kind, modifier -> WidgetPreviewSlot(kind, state, modifier) },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .testTag(ROOT_TEST_TAG),
                notificationPermissionIsRuntime = notificationPermissionIsRuntime(Build.VERSION.SDK_INT),
                snackbarHostState = snackbars,
            )
        }
    }

    /**
     * The real widget of [kind] once the stored appearance answers (recipe platform-view-slot): one preview per
     * composed page, built in the factory, bound to every appearance change, re-read when a new spoiler image is
     * stored, closed on release. Rows wait for the appearance too, so the card stays empty until then.
     */
    @Composable
    private fun WidgetPreviewSlot(kind: WidgetKind, state: OnboardingUiState, modifier: Modifier) {
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
                previewFactory.create(context, viewLifecycleOwner.lifecycleScope, settings).also { preview ->
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

    private fun handle(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.RequestPinWidget -> pinRequester.request(event.kind.providerClassName)
            OnboardingEvent.RequestNotificationPermission -> requestNotificationPermission()
            OnboardingEvent.OpenNotificationSettings -> requireContext().openAppNotificationSettings()
            OnboardingEvent.SpoilerImageFailed -> showSnackbar(R.string.settings_qr_custom_image_failed)
            is OnboardingEvent.ShowError -> showSnackbar(event.error.messageRes())
        }
    }

    private fun requestNotificationPermission() {
        if (!notificationPermissionIsRuntime(Build.VERSION.SDK_INT)) {
            requireContext().openAppNotificationSettings()
            return
        }
        notificationPermissionLauncher.launch(Unit)
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            showSnackbar(R.string.link_open_failed)
        }
    }

    private fun showSnackbar(@StringRes message: Int) {
        if (view == null) return
        val text = getString(message)
        viewLifecycleOwner.lifecycleScope.launch {
            snackbars.showSnackbar(text, duration = SnackbarDuration.Long)
        }
    }

    /** The spoiler revision a preview last drew; a newer one re-reads the stored image. */
    private class DrawnRevision {
        var value: Int? = null
    }

    companion object {
        /** The flow's root, tagged like the root id of the View flow, so routing tests keep their lookup. */
        @VisibleForTesting
        const val ROOT_TEST_TAG = "onboarding_root"
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
