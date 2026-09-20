package dev.alllexey.itmowidgets.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorViewModel
import dev.alllexey.itmowidgets.feature.friendselector.ui.FriendSelectorDialogFragment
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeViewModel
import dev.alllexey.itmowidgets.feature.qr.ui.QrCodeFragment
import kotlinx.coroutines.flow.flowOf
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileViewModel
import dev.alllexey.itmowidgets.feature.social.ui.UserFriendsFragment
import dev.alllexey.itmowidgets.feature.social.ui.UserProfileFragment
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.descendants
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentFactory
import dev.alllexey.itmowidgets.feature.web.ui.MyItmoWebFragment
import dev.alllexey.itmowidgets.feature.web.ui.MyItmoWebPreviewFragment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.FragmentNavigator
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.databinding.ActivityMainBinding
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.social.SocialState
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import dev.alllexey.itmowidgets.feature.onboarding.ui.OnboardingFragment
import dev.alllexey.itmowidgets.feature.me.ui.MeFragment
import dev.alllexey.itmowidgets.feature.home.presentation.HomeViewModel
import dev.alllexey.itmowidgets.feature.home.ui.HomeFragment
import dev.alllexey.itmowidgets.feature.settings.domain.*
import dev.alllexey.itmowidgets.feature.settings.presentation.*
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import dev.alllexey.itmowidgets.core.diagnostics.NoDiagnostics

/** Actual settings/NavHost lifecycle, backed only by in-memory settings and no credentials. */
@AndroidEntryPoint
class SettingsNavigationTestActivity : AppCompatActivity(), AppNavigator {
    lateinit var binding: ActivityMainBinding
        private set
    lateinit var navigation: MainNavigationCoordinator
        private set
    val overlayEnteredReady = mutableListOf<Boolean>()
    val entered = mutableListOf<Pair<SettingsPage, Boolean>>()
    val offlineLoadingFrames = mutableListOf<SettingsPage>()
    val groupedProfileFrames = mutableListOf<Boolean>()
    private val repository = FixtureRepository()
    private val refresh = object : WidgetRefreshRequester { override fun refreshAll() = Unit }
    private val onboardingServices = FixtureOnboardingServices()
    private val onboardingAppearance = FixtureWidgetAppearance()

    data class Appearance(val dark: Boolean = false, val fontScale: Float = 1f, val colorSeed: Int? = null)

    /** The first-run flow with no stored preferences and no backend behind the opt-in. */
    data class OnboardingFixture(
        val servicesEnabled: Boolean = false,
        val pinSupported: Boolean = true
    )

    companion object {
        @Volatile var appearance = Appearance()
        @Volatile var startDestination = R.id.navigation_home
        @Volatile var onboardingFixture = OnboardingFixture()
        @Volatile var friendSelectorFixture = FriendSelectorFixture()
        @Volatile var friendsResult: AppResult<List<UserProfile>> = AppResult.Success(emptyList())
        @Volatile var friendsDelayMs = 0L
        @Volatile var friendsOpen = true
        @Volatile var qrCode: QrCodeSnapshot? = QrCodeSnapshot("ITMO-TEST", 3_600_000)
        @Volatile var qrRefreshResult: AppResult<Unit> = AppResult.Success(Unit)
        @Volatile var qrDelayMs = 0L
        @Volatile var homeFixture = HomeFixture()
        /** The last feed source the host built, for tests that swap cards while the screen is up. */
        @Volatile var homeSource: FixtureHomeCardSource? = null
        @Volatile var homePreferences: FixtureHomeCardPreferences? = null
        @Volatile var homeHintStore: FixtureHomeHintStore? = null
        const val LONG_NAME = "Александра Константиновна Константинопольская"
    }

    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply {
            fontScale = appearance.fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (appearance.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = object : FragmentFactory() {
            override fun instantiate(classLoader: ClassLoader, className: String): Fragment =
                if (className == MyItmoWebFragment::class.java.name) MyItmoWebPreviewFragment()
                else super.instantiate(classLoader, className)
        }
        delegate.localNightMode = if (appearance.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, f: Fragment, state: Bundle?) {
                if (f is FriendSelectorDialogFragment) {
                    ViewModelProvider(f, object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = friendSelectorFixture.let {
                            FriendSelectorViewModel(it.repository, it.history, it.search) as T
                        }
                    })[FriendSelectorViewModel::class.java]
                    return
                }
                if (f is QrCodeFragment) {
                    ViewModelProvider(f, object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = QrCodeViewModel(
                            object : QrCodeRepository {
                                override suspend fun currentQr() = qrCode
                                override suspend fun currentQrHex(allowExpired: Boolean) = qrCode?.hex
                                override fun observeQrHex() = flowOf(qrCode?.hex.orEmpty())
                                override suspend fun refreshQrHex(force: Boolean): AppResult<Unit> {
                                    delay(qrDelayMs)
                                    return qrRefreshResult
                                }
                                override fun clearCache() = Unit
                            }, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
                        ) as T
                    })[QrCodeViewModel::class.java]
                    return
                }
                if (f is UserProfileFragment || f is UserFriendsFragment) {
                    val arguments = SavedStateHandle(mapOf(
                        UserScreenArgs.ISU to f.requireArguments().getInt(UserScreenArgs.ISU),
                        UserScreenArgs.NAME to f.requireArguments().getString(UserScreenArgs.NAME)
                    ))
                    val factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                            UserFriendsViewModel::class.java -> UserFriendsViewModel(arguments, ProfileSocial)
                            UserProfileViewModel::class.java -> UserProfileViewModel(arguments, ProfileSocial, object : CurrentUserProvider {
                                override suspend fun getCurrentUser() = CurrentUser(100001, "Тестовый пользователь", null)
                            })
                            else -> error("Unexpected social ViewModel")
                        } as T
                    }
                    if (f is UserFriendsFragment) ViewModelProvider(f, factory)[UserFriendsViewModel::class.java]
                    else ViewModelProvider(f, factory)[UserProfileViewModel::class.java]
                    return
                }
                if (f is HomeFragment) {
                    ViewModelProvider(f, object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            val source = FixtureHomeCardSource(homeFixture).also { homeSource = it }
                            val preferences = FixtureHomeCardPreferences(homeFixture.hidden).also { homePreferences = it }
                            val hints = FixtureHomeHintStore().also { homeHintStore = it }
                            return HomeViewModel(setOf(source), preferences, hints, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)) as T
                        }
                    })[HomeViewModel::class.java]
                    return
                }
                if (f is MeFragment) {
                    ViewModelProvider(f, object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            MeViewModel(ProfileSession, ProfileSocial, Services) as T
                    })[MeViewModel::class.java]
                    return
                }
                if (f is OnboardingFragment) {
                    ViewModelProvider(f, object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = OnboardingViewModel(
                            onboardingRepository = Onboarding,
                            customServicesRepository = onboardingServices,
                            widgetAppearanceRepository = onboardingAppearance,
                            savedStateHandle = SavedStateHandle()
                        ) as T
                    })[OnboardingViewModel::class.java]
                    return
                }
                if (f !is SettingsFragment) return
                val page = SettingsPage.fromArgument(f.arguments?.getString(SettingsPage.ARGUMENT))
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                        SettingsViewModel::class.java -> SettingsViewModel(repository, Services, Onboarding, refresh, AppVersion("test"), NoDiagnostics, SavedStateHandle(mapOf(SettingsPage.ARGUMENT to page.name)))
                        CustomSpoilerViewModel::class.java -> CustomSpoilerViewModel(object : CustomSpoilerRepository {
                            override suspend fun hasImage() = false
                            override suspend fun saveImage(sourceUri: String) = false
                            override suspend fun resetImage() = false
                        }, refresh)
                        else -> error("Unexpected ViewModel")
                    } as T
                }
                ViewModelProvider(f, factory)[SettingsViewModel::class.java]
                ViewModelProvider(f, factory)[CustomSpoilerViewModel::class.java]
            }

            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, state: Bundle?) {
                if (f is OnboardingFragment) {
                    // The Fragment reports the real launcher in onCreate; the fixture decides here.
                    ViewModelProvider(f)[OnboardingViewModel::class.java]
                        .onPinSupportChanged(onboardingFixture.pinSupported)
                    return
                }
                if (f is AppOverlayHostFragment) {
                    (f.enterTransition as? Transition)?.addListener(object : TransitionListenerAdapter() {
                        override fun onTransitionStart(transition: Transition) {
                            val content = f.childFragmentManager.primaryNavigationFragment?.view
                            overlayEnteredReady += content?.findViewById<View>(R.id.settings_scroll)?.visibility == View.VISIBLE
                        }
                    })
                }
                if (f is MeFragment && v is ViewGroup) {
                    val initialAlphas = v.descendants.associateWith { it.alpha }
                    v.viewTreeObserver.addOnPreDrawListener {
                        groupedProfileFrames += v.isTransitionGroup && v.descendants.all {
                            it.translationX == 0f && it.translationY == 0f && it.alpha == initialAlphas[it]
                        }
                        true
                    }
                    return
                }
                if (f !is SettingsFragment) return
                val page = SettingsPage.fromArgument(f.arguments?.getString(SettingsPage.ARGUMENT))
                v.viewTreeObserver.addOnPreDrawListener {
                    if (page != SettingsPage.PRIVACY && v.findViewById<View>(R.id.settings_progress).visibility == View.VISIBLE) {
                        offlineLoadingFrames += page
                    }
                    true
                }
                (f.enterTransition as? Transition)?.addListener(object : TransitionListenerAdapter() {
                    override fun onTransitionStart(transition: Transition) {
                        val contentReady = if (page == SettingsPage.PRIVACY) true else {
                            v.findViewById<View>(R.id.settings_scroll).visibility == View.VISIBLE &&
                                (page != SettingsPage.QR_WIDGET || v.findViewById<ImageView>(R.id.qr_code_image)?.drawable != null)
                        }
                        entered += page to contentReady
                    }
                })
            }
        }, true)
        super.onCreate(savedInstanceState)
        supportFragmentManager.setFragmentResultListener(FriendSelectionContract.RESULT_KEY, this) { _, result ->
            friendSelectorFixture.results += result.getInt(FriendSelectionContract.RESULT_USER_ISU)
        }
        appearance.colorSeed?.let {
            DynamicColors.applyToActivityIfAvailable(this, DynamicColorsOptions.Builder().setContentBasedSource(it).build())
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        val bottomPadding = binding.bottomNavView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.bottomNavView.updatePadding(bottom = bottomPadding + bars.bottom)
            insets
        }
        if (host.navController.currentDestination == null) {
            host.navController.graph = host.navController.navInflater.inflate(R.navigation.main_nav_graph).apply {
                setStartDestination(startDestination)
                // Root routing is real; unrelated roots get a blank view to avoid API calls.
                listOf(R.id.navigation_schedule, R.id.navigation_recordbook, R.id.navigation_sport).forEach {
                    (findNode(it) as FragmentNavigator.Destination).setClassName(BlankTabFragment::class.java.name)
                }
            }
        }
        binding.sessionProgress.isVisible = false
        binding.navHostFragment.isVisible = true
        // The first-run flow owns the whole window, exactly as it does in MainActivity.
        binding.bottomNavView.isVisible = startDestination != R.id.onboarding
        navigation = MainNavigationCoordinator(binding, supportFragmentManager, host)
    }

    override fun openScreen(screen: AppScreen, arguments: Bundle?) = navigation.openScreen(screen, arguments)

    override fun dismissOverlays() = navigation.dismissOverlays()

    override fun openRoot(root: AppRoot) = Unit

    override fun openLessonDetails(args: LessonDetailsArgs) = navigation.openLessonDetails(args)

    override fun openPendingSportDetails(args: PendingSportDetailsArgs) = navigation.openPendingSportDetails(args)

    val host: NavHostFragment
        get() = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment

    private class FixtureRepository : SettingsRepository {
        private val local = MutableStateFlow(LocalSettings(customServicesEnabled = true))
        private val sharing = MutableStateFlow<SharingSettingsState>(SharingSettingsState.Loading)
        override fun observeLocalSettings() = local.onStart { delay(80) }
        override fun observeSharingSettings() = sharing
        override suspend fun refreshSharingSettings() { sharing.value = SharingSettingsState.Content(SharingSettings()) }
        override fun disableSharingSettings() = Unit
        override suspend fun setScheduleVisibility(visibility: SharingVisibility): AppResult<Unit> {
            val content = sharing.value as SharingSettingsState.Content
            sharing.value = content.copy(settings = content.settings.copy(scheduleVisibility = visibility))
            return AppResult.Success(Unit)
        }
        override suspend fun setFriendsVisibility(visibility: SharingVisibility): AppResult<Unit> {
            val content = sharing.value as SharingSettingsState.Content
            sharing.value = content.copy(settings = content.settings.copy(friendsVisibility = visibility))
            return AppResult.Success(Unit)
        }

        override suspend fun setSportVisibility(visibility: SharingVisibility): AppResult<Unit> {
            val content = sharing.value as SharingSettingsState.Content
            sharing.value = content.copy(settings = content.settings.copy(sportVisibility = visibility))
            return AppResult.Success(Unit)
        }
        override suspend fun setCompactWidgetNextLessonEarlyEnabled(enabled: Boolean) = Unit
        override suspend fun setCompactWidgetTeacherHidden(hidden: Boolean) = Unit
        override suspend fun setFullWidgetTeacherHidden(hidden: Boolean) = Unit
        override suspend fun setFullWidgetPastLessonsHidden(hidden: Boolean) = Unit
        override suspend fun setFullWidgetTomorrowEnabled(enabled: Boolean) = Unit
        override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) = Unit
        override suspend fun setQrSpoilerEnabled(enabled: Boolean) = Unit
        override suspend fun setQrAnimationType(type: QrAnimationType) = Unit
        override suspend fun setTeacherSelectorHidden(hidden: Boolean) = Unit
        override suspend fun setTimeSelectorHidden(hidden: Boolean) = Unit
        override suspend fun setScheduleSportAutoSignEnabled(enabled: Boolean) {
            local.value = local.value.copy(showSportAutoSign = enabled)
        }

        override suspend fun setHomeCardVisible(kind: HomeCardKind, visible: Boolean) {
            local.value = local.value.copy(hiddenHomeCards = if (visible) local.value.hiddenHomeCards - kind else local.value.hiddenHomeCards + kind)
        }

        override suspend fun setCompactWidgetTextSize(size: WidgetTextSize) {
            val widget = local.value.scheduleWidget
            local.value = local.value.copy(scheduleWidget = widget.copy(compact = widget.compact.copy(textSize = size)))
        }

        override suspend fun setFullWidgetTextSize(size: WidgetTextSize) {
            val widget = local.value.scheduleWidget
            local.value = local.value.copy(scheduleWidget = widget.copy(full = widget.full.copy(textSize = size)))
        }
    }

    private object Services : CustomServicesRepository {
        override fun observeEnabled() = MutableStateFlow(true)
        override suspend fun isEnabled() = true
        override suspend fun setEnabled(enabled: Boolean) = Unit
    }

    private object Onboarding : OnboardingRepository {
        val resets = mutableListOf<Unit>()
        override fun observeCompleted() = MutableStateFlow(true)
        override suspend fun complete() = Unit
        override suspend fun reset() { resets += Unit }
    }

    /** The opt-in really flips here, but nothing behind it reaches Backend. */
    private class FixtureOnboardingServices : CustomServicesRepository {
        private val enabled = MutableStateFlow(onboardingFixture.servicesEnabled)
        override fun observeEnabled() = enabled
        override suspend fun isEnabled() = enabled.value
        override suspend fun setEnabled(enabled: Boolean) { this.enabled.value = enabled }
    }

    private class FixtureWidgetAppearance : WidgetAppearanceRepository {
        private val appearance = MutableStateFlow(WidgetAppearance())
        override fun observeAppearance() = appearance
        override suspend fun setCompactNextLessonEarly(enabled: Boolean) =
            schedule { copy(compact = compact.copy(showNextLessonEarly = enabled)) }
        override suspend fun setCompactTeacherHidden(hidden: Boolean) =
            schedule { copy(compact = compact.copy(hideTeacher = hidden)) }
        override suspend fun setFullTeacherHidden(hidden: Boolean) =
            schedule { copy(full = full.copy(hideTeacher = hidden)) }
        override suspend fun setFullPastLessonsHidden(hidden: Boolean) =
            schedule { copy(full = full.copy(hidePastLessons = hidden)) }
        override suspend fun setFullTomorrowEnabled(enabled: Boolean) =
            schedule { copy(full = full.copy(showTomorrowWhenTodayIsOver = enabled)) }
        override suspend fun setCompactTextSize(size: WidgetTextSize) =
            schedule { copy(compact = compact.copy(textSize = size)) }
        override suspend fun setFullTextSize(size: WidgetTextSize) =
            schedule { copy(full = full.copy(textSize = size)) }
        override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) =
            qr { copy(dynamicColors = enabled) }
        override suspend fun setQrSpoilerEnabled(enabled: Boolean) =
            qr { copy(spoilerEnabled = enabled) }

        private fun schedule(block: ScheduleWidgetSettings.() -> ScheduleWidgetSettings) {
            appearance.value = appearance.value.copy(schedule = appearance.value.schedule.block())
        }

        private fun qr(block: QrWidgetSettings.() -> QrWidgetSettings) {
            appearance.value = appearance.value.copy(qr = appearance.value.qr.block())
        }
    }

    private object ProfileSocial : SocialRepository {
        override fun observeFriends() = MutableStateFlow<SocialState<List<UserProfile>>>(SocialState.Content(emptyList()))
        override fun observeRequests() = MutableStateFlow<SocialState<FriendRequests>>(SocialState.Content(FriendRequests.EMPTY))
        override fun observeCurrentUser() = MutableStateFlow<UserSummary?>(null)
        override val currentFriends: List<UserProfile> = emptyList()
        override suspend fun refresh() = Unit
        override suspend fun userFriends(isu: Int): AppResult<List<UserProfile>> {
            delay(friendsDelayMs)
            return friendsResult
        }
        override suspend fun profile(isu: Int): AppResult<UserProfile> = AppResult.Success(UserProfile(
            UserSummary(isu, LONG_NAME, null, emptyList(), UserSharing(true, true, friendsOpen)), RelationshipState.NONE
        ))
        override suspend fun lookup(isus: List<Int>): AppResult<List<UserProfile>> = AppResult.Success(emptyList())
        override suspend fun sendRequest(isu: Int): AppResult<UserProfile> = error("Social data is unavailable in this fixture")
        override suspend fun acceptRequest(isu: Int): AppResult<UserProfile> = error("Social data is unavailable in this fixture")
        override suspend fun rejectRequest(isu: Int): AppResult<UserProfile> = error("Social data is unavailable in this fixture")
        override suspend fun cancelRequest(isu: Int): AppResult<UserProfile> = error("Social data is unavailable in this fixture")
        override suspend fun removeFriend(isu: Int): AppResult<UserProfile> = error("Social data is unavailable in this fixture")
    }

    private object ProfileSession : SessionRepository {
        override val state = MutableStateFlow<SessionState>(
            SessionState.SignedIn(CurrentUser(123456, "Александрова Мария Александровна", null))
        )
        override suspend fun initialize() = Unit
        override suspend fun completeItmoIdLogin(tokenResponseJson: String): AppResult<Unit> =
            error("Authentication is unavailable in this fixture")
        override suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit> =
            error("Authentication is unavailable in this fixture")
        override suspend fun signOut() = error("Sign-out is unavailable in this fixture")
    }
}
