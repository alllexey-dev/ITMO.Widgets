package dev.alllexey.itmowidgets.app

import android.os.Bundle
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
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.databinding.ActivityMainBinding
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import dev.alllexey.itmowidgets.feature.me.ui.MeFragment
import dev.alllexey.itmowidgets.feature.settings.domain.*
import dev.alllexey.itmowidgets.feature.settings.presentation.*
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart

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

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, f: Fragment, state: Bundle?) {
                if (f is MeFragment) {
                    ViewModelProvider(f, object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            MeViewModel(ProfileSession) as T
                    })[MeViewModel::class.java]
                    return
                }
                if (f !is SettingsFragment) return
                val page = SettingsPage.fromArgument(f.arguments?.getString(SettingsPage.ARGUMENT))
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                        SettingsViewModel::class.java -> SettingsViewModel(repository, Services, refresh, AppVersion("test"), SavedStateHandle(mapOf(SettingsPage.ARGUMENT to page.name)))
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
                setStartDestination(R.id.navigation_home)
                // Root routing is real; unrelated roots use the static Home view to avoid API calls.
                listOf(R.id.navigation_schedule, R.id.navigation_recordbook, R.id.navigation_sport).forEach {
                    (findNode(it) as FragmentNavigator.Destination).setClassName(
                        "dev.alllexey.itmowidgets.feature.home.ui.HomeFragment"
                    )
                }
            }
        }
        binding.sessionProgress.isVisible = false
        binding.navHostFragment.isVisible = true
        binding.bottomNavView.isVisible = true
        navigation = MainNavigationCoordinator(binding, supportFragmentManager, host)
    }

    override fun openScreen(screen: AppScreen, arguments: Bundle?) = navigation.openScreen(screen, arguments)

    val host: NavHostFragment
        get() = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment

    private class FixtureRepository : SettingsRepository {
        private val local = MutableStateFlow(LocalSettings(customServicesEnabled = true))
        private val sharing = MutableStateFlow<SharingSettingsState>(SharingSettingsState.Loading)
        override fun observeLocalSettings() = local.onStart { delay(80) }
        override fun observeSharingSettings() = sharing
        override suspend fun refreshSharingSettings() { sharing.value = SharingSettingsState.Content(SharingSettings(true, true)) }
        override fun disableSharingSettings() = Unit
        override suspend fun setScheduleSharing(enabled: Boolean) = AppResult.Success(Unit)
        override suspend fun setSportSharing(enabled: Boolean) = AppResult.Success(Unit)
        override suspend fun setNextLessonEarlyEnabled(enabled: Boolean) = Unit
        override suspend fun setWidgetTeacherHidden(hidden: Boolean) = Unit
        override suspend fun setPastLessonsHidden(hidden: Boolean) = Unit
        override suspend fun setTomorrowScheduleEnabled(enabled: Boolean) = Unit
        override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) = Unit
        override suspend fun setQrSpoilerEnabled(enabled: Boolean) = Unit
        override suspend fun setQrAnimationType(type: QrAnimationType) = Unit
        override suspend fun setTeacherSelectorHidden(hidden: Boolean) = Unit
        override suspend fun setTimeSelectorHidden(hidden: Boolean) = Unit
        override suspend fun setScheduleSportAutoSignEnabled(enabled: Boolean) {
            local.value = local.value.copy(showSportAutoSign = enabled)
        }
    }

    private object Services : CustomServicesRepository {
        override fun observeEnabled() = MutableStateFlow(true)
        override suspend fun isEnabled() = true
        override suspend fun setEnabled(enabled: Boolean) = Unit
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
