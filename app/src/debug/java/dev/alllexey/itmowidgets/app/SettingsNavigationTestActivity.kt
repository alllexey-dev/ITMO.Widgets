package dev.alllexey.itmowidgets.app

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.descendants
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
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
class SettingsNavigationTestActivity : AppCompatActivity() {
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
                (f.enterTransition as Transition).addListener(object : TransitionListenerAdapter() {
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
        setContentView(FrameLayout(this).apply {
            id = R.id.settings_test_container
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                view.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
                insets
            }
        })
        if (savedInstanceState == null) {
            val navHost = NavHostFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_test_container, navHost, "settings-nav")
                .setPrimaryNavigationFragment(navHost)
                .commitNow()
            navHost.navController.graph = navHost.navController.navInflater.inflate(R.navigation.main_nav_graph).apply {
                setStartDestination(R.id.navigation_home)
            }
        }
    }

    val host: NavHostFragment get() = supportFragmentManager.findFragmentByTag("settings-nav") as NavHostFragment

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
