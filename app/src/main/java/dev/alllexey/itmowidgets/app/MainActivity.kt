package dev.alllexey.itmowidgets.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.databinding.ActivityMainBinding
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingGate
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingGateViewModel
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateGateViewModel
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyViewModel
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCommonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.sport.ui.common.bookingAction
import dev.alllexey.itmowidgets.feature.sport.ui.common.toDetailsArgs
import dev.alllexey.itmowidgets.feature.update.ui.toScreenArguments
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.dataOrNull
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), AppNavigator {

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var sportBookings: SportBookingRepository

    @Inject
    lateinit var timeProvider: AcademicTimeProvider

    /** Shared with the sport tab, so a cancellation from the feed or the schedule goes the same way. */
    private val sportMy: SportMyViewModel by viewModels()

    private val updateGate: AppUpdateGateViewModel by viewModels()
    private val onboardingGate: OnboardingGateViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var navigation: MainNavigationCoordinator
    private var pendingRootDestination: Int? = null
    private var pendingUserIsu: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            pendingRootDestination = savedInstanceState.getInt(PENDING_ROOT).takeIf { it != 0 }
            pendingUserIsu = savedInstanceState.getInt(PENDING_USER).takeIf { it > 0 }
        } else {
            acceptIntent(intent)
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        val navView = binding.bottomNavView
        val initialBottomNavPadding = navView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = systemBars.left, top = systemBars.top, right = systemBars.right)
            navView.updatePadding(bottom = initialBottomNavPadding + systemBars.bottom)
            insets
        }

        val rootHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navigation = MainNavigationCoordinator(binding, supportFragmentManager, rootHost)
        // Sheets opened from the feed or the schedule report their action here, not to a sport Fragment.
        supportFragmentManager.setFragmentResultListener(SportCommonDetailsBottomSheet.ACTION_REQUEST, this) { _, result ->
            onSportSheetAction(
                result.getLong(SportCommonDetailsBottomSheet.RESULT_LESSON_ID),
                result.getString(SportCommonDetailsBottomSheet.RESULT_ACTION)
            )
        }

        lifecycleScope.launch { sessionRepository.initialize() }
        sessionRepository.state
            .flowWithLifecycle(lifecycle)
            .onEach(::renderSession)
            .launchIn(lifecycleScope)
        // The gate resolves and later flips on its own; the session state alone never reports it.
        onboardingGate.state
            .flowWithLifecycle(lifecycle)
            .onEach { renderSession(sessionRepository.state.value) }
            .launchIn(lifecycleScope)
        // Only while resumed: opening the offer runs a Fragment transaction.
        updateGate.offers
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { update -> openScreen(AppScreen.APP_UPDATE, update.toScreenArguments()) }
            .launchIn(lifecycleScope)
    }

    override fun openScreen(screen: AppScreen, arguments: Bundle?) {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        // The first-run flow owns the whole window; overlays would appear over a hidden container.
        if (onboardingGate.state.value != OnboardingGate.Passed) return
        navigation.openScreen(screen, arguments)
    }

    override fun dismissOverlays() {
        navigation.dismissOverlays()
    }

    override fun openRoot(root: AppRoot) {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        if (onboardingGate.state.value != OnboardingGate.Passed) return
        navigation.openRoot(root)
    }

    /** A sport lesson in the schedule is a booking: it gets the sport sheet with `Отменить`. */
    override fun openSubjectLinks(args: dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs) {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        if (onboardingGate.state.value != OnboardingGate.Passed) return
        navigation.openSubjectLinks(args)
    }

    override fun openLessonDetails(args: LessonDetailsArgs) {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        if (onboardingGate.state.value != OnboardingGate.Passed) return
        if (args.typeId != SPORT_TYPE_ID) {
            navigation.openLessonDetails(args)
            return
        }
        lifecycleScope.launch {
            val booking = findSportBookingAt(LocalDate.parse(args.date), LocalTime.parse(args.start), args.subjectName)
            if (booking != null) navigation.openSportDetails(booking) else navigation.openLessonDetails(args)
        }
    }

    /** The sport tab's sheet when the sport data knows the queue; the schedule's own sheet otherwise. */
    override fun openPendingSportDetails(args: PendingSportDetailsArgs) {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        if (onboardingGate.state.value != OnboardingGate.Passed) return
        lifecycleScope.launch {
            val item = findSportBooking(args.lessonId)
            if (item != null) navigation.openSportDetails(item) else navigation.openPendingSportDetails(args)
        }
    }

    /**
     * The sport tab's merged bookings. Every source behind them is a replay flow
     * that only emits after its own refresh, so the first caller loads the tab's
     * data exactly as opening the tab would; afterwards the answer is immediate.
     */
    private suspend fun sportBookingsSnapshot(): List<SportBooking>? {
        sportMy.ensureDataLoaded()
        val state = withTimeoutOrNull(BOOKINGS_WAIT_MILLIS) { sportBookings.observeSportBookings().first() }
        return state?.dataOrNull()
    }

    private suspend fun findSportBooking(lessonId: Long): SportBooking? =
        sportBookingsSnapshot()?.firstOrNull { it.lessonId == lessonId }

    /**
     * Several items can share a slot: a confirmed booking and a queue for another
     * section. The confirmed one wins, and the section name breaks the remaining ties.
     */
    private suspend fun findSportBookingAt(date: LocalDate, start: LocalTime, subject: String): SportBooking? {
        val candidates = sportBookingsSnapshot()?.filter { booking ->
            val local = booking.start.atZoneSameInstant(timeProvider.zoneId)
            local.toLocalDate() == date && local.toLocalTime() == start
        }.orEmpty()
        val wanted = subject.trim().lowercase()
        fun SportBooking.named() = wanted.isNotEmpty() && sectionName.raw.trim().lowercase().let { it in wanted || wanted in it }
        return candidates.firstOrNull { it.signed && it.named() }
            ?: candidates.firstOrNull { it.signed }
            ?: candidates.firstOrNull { it.named() }
            ?: candidates.firstOrNull()
    }

    private fun onSportSheetAction(lessonId: Long, action: String?) {
        lifecycleScope.launch {
            val booking = findSportBooking(lessonId) ?: return@launch
            val expected = booking.toDetailsArgs().bookingAction(timeProvider.now())
            if (expected == SportBookingAction.NONE || expected.name != action) return@launch
            MaterialAlertDialogBuilder(this@MainActivity)
                .setMessage(R.string.sport_cancel_booking_question)
                .setNegativeButton(R.string.common_back, null)
                .setPositiveButton(R.string.sport_cancel_booking_action) { _, _ -> sportMy.cancelBooking(booking) }
                .show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
        renderSession(sessionRepository.state.value)
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        // An intent can arrive after onSaveInstanceState. Apply it only once transactions are safe.
        renderSession(sessionRepository.state.value)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(PENDING_ROOT, pendingRootDestination ?: 0)
        outState.putInt(PENDING_USER, pendingUserIsu ?: 0)
        super.onSaveInstanceState(outState)
    }

    private fun renderSession(state: SessionState) {
        if (supportFragmentManager.isStateSaved) return
        val controller = navigation.rootHost.navController
        when (state) {
            SessionState.Initializing -> {
                binding.bottomNavView.isVisible = false
                binding.navHostFragment.isVisible = false
                binding.overlayContainer.isVisible = false
                binding.sessionProgress.isVisible = true
            }

            SessionState.SigningOut,
            SessionState.SignedOut,
            SessionState.ReauthenticationRequired -> {
                navigation.dismissOverlays()
                binding.overlayContainer.isVisible = false
                binding.bottomNavView.isVisible = false
                if (controller.currentDestination?.id != R.id.auth) {
                    controller.setGraph(R.navigation.main_nav_graph)
                }
                revealResolvedGraph()
            }

            is SessionState.SignedIn -> {
                val gate = onboardingGate.state.value
                if (gate == OnboardingGate.Unknown) {
                    // The stored flag decides the start destination; do not guess it for one frame.
                    binding.bottomNavView.isVisible = false
                    binding.navHostFragment.isVisible = false
                    binding.overlayContainer.isVisible = false
                    binding.sessionProgress.isVisible = true
                    return
                }
                val onboarding = gate == OnboardingGate.Required
                val current = controller.currentDestination?.id
                if (current == null || current == R.id.auth) {
                    controller.graph = controller.navInflater.inflate(R.navigation.main_nav_graph).apply {
                        setStartDestination(if (onboarding) R.id.onboarding else R.id.navigation_home)
                    }
                } else if (!onboarding && current == R.id.onboarding) {
                    controller.navigate(
                        R.id.navigation_home,
                        null,
                        navOptions { popUpTo(R.id.onboarding) { inclusive = true } }
                    )
                } else if (onboarding && current != R.id.onboarding) {
                    // A replay from maintenance: the flow takes the window back, without history.
                    navigation.dismissOverlays()
                    controller.navigate(
                        R.id.onboarding,
                        null,
                        navOptions { popUpTo(controller.graph.id) { inclusive = true } }
                    )
                }
                if (!onboarding) {
                    pendingRootDestination?.let { destination ->
                        if (navigation.selectRoot(destination)) {
                            pendingRootDestination = null
                            val userIsu = pendingUserIsu
                            pendingUserIsu = null
                            if (userIsu != null) {
                                navigation.openScreen(AppScreen.USER_PROFILE, Bundle().apply {
                                    putInt(UserScreenArgs.ISU, userIsu)
                                })
                            }
                        }
                    }
                    // A signed-in session is what the update check needs; it runs once per process.
                    updateGate.checkForUpdate()
                }
                // Contextual navigation covers this surface instead of resizing it.
                binding.bottomNavView.isVisible = !onboarding
                binding.overlayContainer.isVisible = !onboarding
                revealResolvedGraph()
            }
        }
    }

    private fun revealResolvedGraph() {
        navigation.rootHost.childFragmentManager.executePendingTransactions()
        binding.sessionProgress.isVisible = false
        binding.navHostFragment.isVisible = true
    }

    private fun acceptIntent(intent: Intent) {
        val route = MainActivityIntentRouting.parse(intent.action, intent.getIntExtra(UserScreenArgs.ISU, 0)) ?: return
        pendingRootDestination = route.rootDestination
        pendingUserIsu = route.userIsu
    }

    companion object {
        private const val BOOKINGS_WAIT_MILLIS = 8_000L
        private const val SPORT_TYPE_ID = 11
        const val ACTION_OPEN_SPORT = "dev.alllexey.itmowidgets.action.OPEN_SPORT"
        const val ACTION_OPEN_USER_PROFILE = "dev.alllexey.itmowidgets.action.OPEN_USER_PROFILE"
        const val ACTION_OPEN_SCHEDULE = "dev.alllexey.itmowidgets.action.OPEN_SCHEDULE"
        private const val PENDING_USER = "pending_user_isu"
        private const val PENDING_ROOT = "pending_root_destination"
    }
}
