package dev.alllexey.itmowidgets.feature.onboarding.reference

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.testing.FakeCustomSpoilerRepository
import dev.alllexey.itmowidgets.core.testing.FakeOnboardingRepository
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.ReferenceHostActivity
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingStep
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import dev.alllexey.itmowidgets.feature.onboarding.ui.OnboardingFragment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Today's first-run flow under the names of LA-4a's `OnboardingScreen` previews, in
 * `shared/feature-account/screenshots/`: the steps of `OnboardingVisualTest` on in-memory preferences, the opt-in
 * and the spoiler image, as `SettingsNavigationTestActivity` hosts them; no backend, no stored preferences.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class OnboardingReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-account")

    @Test
    fun singleLessonWidget() = onboarding("OnboardingScreen_widget-single-lesson", OnboardingStep.COMPACT_WIDGET)

    @Test
    fun dayScheduleWidget() = onboarding("OnboardingScreen_widget-day-schedule", OnboardingStep.FULL_WIDGET)

    @Test
    fun qrWidget() = onboarding("OnboardingScreen_widget-qr", OnboardingStep.QR_WIDGET)

    @Test
    fun widgetWithoutPinning() =
        onboarding("OnboardingScreen_widget-no-pinning", OnboardingStep.COMPACT_WIDGET, pinSupported = false)

    @Test
    fun servicesOff() = onboarding("OnboardingScreen_services-off", OnboardingStep.SERVICES)

    @Test
    fun servicesOn() = onboarding("OnboardingScreen_services-on", OnboardingStep.SERVICES, servicesEnabled = true)

    @Test
    fun servicesBusy() = onboarding("OnboardingScreen_services-busy", OnboardingStep.SERVICES) {
        setServicesEnabled(true)
    }

    @Test
    fun notificationsAsk() = notifications("OnboardingScreen_notifications-ask") {
        onNotificationPermission(false)
    }

    @Test
    fun notificationsDenied() = notifications("OnboardingScreen_notifications-denied") {
        // Asked once and refused: Android will not show the dialog again.
        onNotificationPermission(false)
        requestNotifications()
        onNotificationPermission(false)
    }

    @Test
    fun notificationsGranted() = notifications("OnboardingScreen_notifications-granted") {
        onNotificationPermission(true)
    }

    private fun notifications(preview: String, act: OnboardingViewModel.() -> Unit) =
        onboarding(preview, OnboardingStep.NOTIFICATIONS, servicesEnabled = true, act = act)

    /**
     * Opens the flow on its first step once per launch and walks it with `Далее` to [step], as a user does (a pager
     * that has not been laid out yet cannot scroll to a restored step). [act] runs there, after the Fragment has
     * reported the real launcher and the notification permission, as the fixture host does.
     */
    private fun onboarding(
        preview: String,
        step: OnboardingStep,
        servicesEnabled: Boolean = false,
        pinSupported: Boolean = true,
        act: OnboardingViewModel.() -> Unit = {},
    ) {
        var model: OnboardingViewModel? = null
        var acted = false
        references.host(
            preview,
            ReferenceHostActivity::class.java,
            appearance = {},
            ready = ready@{ host ->
                val shown = host.supportFragmentManager.fragments.filterIsInstance<OnboardingFragment>().firstOrNull()
                    ?: OnboardingFragment().also { fragment ->
                        acted = false
                        host.supportFragmentManager.preset(fragment) {
                            OnboardingViewModel(
                                onboardingRepository = FakeOnboardingRepository(),
                                customServicesRepository = Services(servicesEnabled),
                                widgetAppearanceRepository = Appearance,
                                customSpoilerRepository = FakeCustomSpoilerRepository(),
                                savedStateHandle = SavedStateHandle(),
                            ).also { model = it }
                        }
                        host.show(fragment)
                        checkNotNull(model).onPinSupportChanged(pinSupported)
                    }
                val flow = checkNotNull(model)
                val pager = shown.requireView().findViewById<ViewPager2>(R.id.onboarding_pager)
                if (pager.width == 0 || pager.scrollState != ViewPager2.SCROLL_STATE_IDLE) return@ready false
                when {
                    flow.uiState.value.step != step -> flow.next()
                    !acted -> {
                        flow.act()
                        acted = true
                    }
                    else -> return@ready shown.childFragmentManager.fragments.any { it.isResumed && it.view != null }
                }
                false
            },
            view = { it.content },
        )
    }

    /** The opt-in; switching it waits forever, so a switch in flight stays busy. */
    private class Services(enabled: Boolean) : CustomServicesRepository {
        private val enabled = MutableStateFlow(enabled)
        override fun observeEnabled() = enabled
        override suspend fun isEnabled() = enabled.value
        override suspend fun setEnabled(enabled: Boolean) = CompletableDeferred<Unit>().await()
    }

    /** The default widget appearance of a fresh install; the flow only reads it here. */
    private object Appearance : WidgetAppearanceRepository {
        override fun observeAppearance() = flowOf(WidgetAppearance())
        override suspend fun setCompactNextLessonEarly(enabled: Boolean) = Unit
        override suspend fun setCompactTeacherHidden(hidden: Boolean) = Unit
        override suspend fun setFullTeacherHidden(hidden: Boolean) = Unit
        override suspend fun setFullPastLessonsHidden(hidden: Boolean) = Unit
        override suspend fun setFullTomorrowEnabled(enabled: Boolean) = Unit
        override suspend fun setCompactTextSize(size: WidgetTextSize) = Unit
        override suspend fun setFullTextSize(size: WidgetTextSize) = Unit
        override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) = Unit
        override suspend fun setQrSpoilerEnabled(enabled: Boolean) = Unit
    }

}

/** Hands [fragment] the view model [create] makes before Hilt could create one. */
private fun FragmentManager.preset(fragment: Fragment, create: () -> ViewModel) =
    registerFragmentLifecycleCallbacks(
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {
                if (f !== fragment) return
                val model = create()
                ViewModelProvider(
                    f,
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = model as T
                    },
                )[model.javaClass]
            }
        },
        false,
    )
