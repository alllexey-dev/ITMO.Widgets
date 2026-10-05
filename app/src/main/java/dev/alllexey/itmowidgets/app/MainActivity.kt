package dev.alllexey.itmowidgets.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.os.bundleOf
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
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.ScheduleTodayRequest
import dev.alllexey.itmowidgets.core.navigation.SportLessonRequest
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.navigation.from
import dev.alllexey.itmowidgets.core.navigation.toBundle
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.time.javaNow
import dev.alllexey.itmowidgets.core.time.javaZone
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.databinding.ActivityMainBinding
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingGate
import dev.alllexey.itmowidgets.feature.recordbook.ui.BarsLoginActivity
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingGateViewModel
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateGateViewModel
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyViewModel
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCommonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.sport.ui.common.bookingAction
import dev.alllexey.itmowidgets.feature.sport.ui.common.toDetailsArgs
import dev.alllexey.itmowidgets.feature.update.ui.InstallStateWatcher
import dev.alllexey.itmowidgets.feature.update.ui.toScreenArguments
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import java.time.LocalDate
import java.time.LocalTime
import kotlin.time.toJavaInstant
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

    /** Google Play's flexible update; the GitHub build never reports. */
    @Inject
    lateinit var installState: InstallStateWatcher
    private var updateDownloaded: Snackbar? = null

    /** Shared with the sport tab, so a cancellation from the feed or the schedule goes the same way. */
    private val sportMy: SportMyViewModel by viewModels()

    private val updateGate: AppUpdateGateViewModel by viewModels()
    private val onboardingGate: OnboardingGateViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var navigation: MainNavigationCoordinator
    private val routes = MainRouteQueue()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            restoreRoute(savedInstanceState)
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

        binding.demoBannerSignIn.setOnClickListener { lifecycleScope.launch { sessionRepository.signOut() } }

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
        if (!onboardingPassed()) return
        if (screen == AppScreen.MY_ITMO_WEB && refusedInDemo()) return
        navigation.openScreen(screen, arguments)
    }

    private val demoSession: Boolean get() = (sessionRepository.state.value as? SessionState.SignedIn)?.demo == true

    /** The demo session skips the first-run flow without marking it passed. */
    private fun onboardingPassed(): Boolean = demoSession || onboardingGate.state.value == OnboardingGate.Passed

    /** My ITMO in the browser and the web sign-in need a real account. */
    private fun refusedInDemo(): Boolean {
        if (!demoSession) return false
        Toast.makeText(this, R.string.error_demo_unavailable, Toast.LENGTH_SHORT).show()
        return true
    }

    override fun dismissOverlays() {
        navigation.dismissOverlays()
    }

    override fun openRoot(root: AppRoot) {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        if (!onboardingPassed()) return
        navigation.openRoot(root)
    }

    override fun openSubjectLinks(args: SubjectLinksArgs) {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        if (!onboardingPassed()) return
        navigation.openSubjectLinks(args)
    }

    override fun openLinkEditor(args: SubjectLinksArgs, linkId: String?) {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        if (!onboardingPassed()) return
        navigation.openLinkEditor(args, linkId)
    }

    override fun openLinkActions(args: SubjectLinksArgs, linkId: String) {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        if (!onboardingPassed()) return
        navigation.openLinkActions(args, linkId)
    }

    override fun openSheetScores(args: SheetScoresArgs) {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        if (!onboardingPassed()) return
        navigation.openSheetScores(args)
    }

    override fun openReviewEditor(args: TeacherReviewArgs) {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        if (!onboardingPassed()) return
        navigation.openReviewEditor(args)
    }

    override fun openReviewReport(args: TeacherReviewArgs, reviewId: String) {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        if (!onboardingPassed()) return
        navigation.openReviewReport(args, reviewId)
    }

    override fun openWebLogin() {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        if (!onboardingPassed()) return
        if (refusedInDemo()) return
        navigation.openWebLogin()
    }

    /** A sport lesson in the schedule is a booking: it gets the sport sheet with `Отменить`. */
    override fun openLessonDetails(args: LessonDetailsArgs) {
        if (sessionRepository.state.value !is SessionState.SignedIn) return
        if (!onboardingPassed()) return
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
        if (!onboardingPassed()) return
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
        return state?.valueOrNull()
    }

    private suspend fun findSportBooking(lessonId: Long): SportBooking? =
        sportBookingsSnapshot()?.firstOrNull { it.lessonId == lessonId }

    /**
     * Several items can share a slot: a confirmed booking and a queue for another
     * section. The confirmed one wins, and the section name breaks the remaining ties.
     */
    private suspend fun findSportBookingAt(date: LocalDate, start: LocalTime, subject: String): SportBooking? {
        val candidates = sportBookingsSnapshot()?.filter { booking ->
            val local = booking.start.toJavaInstant().atZone(timeProvider.javaZone())
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
            val expected = booking.toDetailsArgs().bookingAction(timeProvider.javaNow())
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

    override fun onResume() {
        super.onResume()
        installState.start(::offerRestart)
    }

    override fun onPause() {
        installState.stop()
        super.onPause()
    }

    private fun offerRestart() {
        if (updateDownloaded?.isShownOrQueued == true) return
        val snackbar = Snackbar.make(binding.root, R.string.update_downloaded, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.update_restart) { installState.completeUpdate() }
        if (binding.bottomNavView.isVisible) snackbar.anchorView = binding.bottomNavView
        snackbar.show()
        updateDownloaded = snackbar
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        // An intent can arrive after onSaveInstanceState. Apply it only once transactions are safe.
        renderSession(sessionRepository.state.value)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val route = routes.pending
        outState.putInt(PENDING_ROOT, route?.rootDestination ?: 0)
        outState.putInt(PENDING_USER, route?.userIsu ?: 0)
        outState.putString(PENDING_SCREEN, route?.screen?.name)
        outState.putBundle(PENDING_SUBJECT, route?.subject?.toBundle())
        outState.putBoolean(PENDING_BARS_LOGIN, route?.barsLogin ?: false)
        outState.putBoolean(PENDING_TODAY, route?.today ?: false)
        outState.putLong(PENDING_SPORT_LESSON, route?.sportLessonId ?: 0L)
        outState.putBoolean(PENDING_SPORT_PREDICTED, route?.sportLessonPredicted ?: false)
        outState.putBoolean(PENDING_LINK_UNAVAILABLE, route?.linkUnavailable ?: false)
        super.onSaveInstanceState(outState)
    }

    private fun restoreRoute(state: Bundle) {
        val root = state.getInt(PENDING_ROOT).takeIf { it != 0 } ?: return
        routes.offer(
            MainActivityRoute(
                rootDestination = root,
                userIsu = state.getInt(PENDING_USER).takeIf { it > 0 },
                screen = state.getString(PENDING_SCREEN)?.let { name -> AppScreen.entries.firstOrNull { it.name == name } },
                subject = RecordbookSubjectArgs.from(state.getBundle(PENDING_SUBJECT)),
                barsLogin = state.getBoolean(PENDING_BARS_LOGIN),
                today = state.getBoolean(PENDING_TODAY),
                sportLessonId = state.getLong(PENDING_SPORT_LESSON).takeIf { it > 0 },
                sportLessonPredicted = state.getBoolean(PENDING_SPORT_PREDICTED),
                linkUnavailable = state.getBoolean(PENDING_LINK_UNAVAILABLE)
            )
        )
    }

    private fun renderSession(state: SessionState) {
        if (supportFragmentManager.isStateSaved) return
        val controller = navigation.rootHost.navController
        binding.demoBanner.isVisible = (state as? SessionState.SignedIn)?.demo == true
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
                if (!state.demo && gate == OnboardingGate.Unknown) {
                    // The stored flag decides the start destination; do not guess it for one frame.
                    binding.bottomNavView.isVisible = false
                    binding.navHostFragment.isVisible = false
                    binding.overlayContainer.isVisible = false
                    binding.sessionProgress.isVisible = true
                    return
                }
                val onboarding = !state.demo && gate == OnboardingGate.Required
                val current = controller.currentDestination?.id
                if (current == null || current == R.id.auth) {
                    controller.graph = controller.navInflater.inflate(R.navigation.main_nav_graph).apply {
                        setStartDestination(if (onboarding) R.id.onboarding else R.id.navigation_home)
                    }
                } else if (!onboarding && current == R.id.onboarding) {
                    // NavigationUI keeps every tab's state through popUpTo the graph's start, and onboarding leaves
                    // the stack now: without this, tabs would pile up until the Activity is recreated.
                    controller.graph.setStartDestination(R.id.navigation_home)
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
                    routes.take(ready = true, navigation::selectRoot)?.let(::applyRoute)
                    // A signed-in session is what the update check needs; it runs once per process.
                    if (!state.demo) updateGate.checkForUpdate()
                }
                // Contextual navigation covers this surface instead of resizing it.
                binding.bottomNavView.isVisible = !onboarding
                binding.overlayContainer.isVisible = !onboarding
                revealResolvedGraph()
            }
        }
    }

    /** Runs once the route's root is selected. */
    private fun applyRoute(route: MainActivityRoute) {
        // The subject page cannot open without its arguments; the recordbook root stays then.
        route.screen?.takeIf { it != AppScreen.RECORDBOOK_SUBJECT || route.subject != null }
            ?.let { navigation.openScreen(it, route.subject?.toBundle()) }
        route.userIsu?.let { isu ->
            navigation.openScreen(AppScreen.USER_PROFILE, Bundle().apply { putInt(UserScreenArgs.ISU, isu) })
        }
        if (route.barsLogin) startActivity(Intent(this, BarsLoginActivity::class.java))
        if (route.today) supportFragmentManager.setFragmentResult(ScheduleTodayRequest.KEY, Bundle.EMPTY)
        route.sportLessonId?.let { lessonId ->
            supportFragmentManager.setFragmentResult(
                SportLessonRequest.KEY,
                bundleOf(SportLessonRequest.LESSON_ID to lessonId, SportLessonRequest.PREDICTED to route.sportLessonPredicted)
            )
        }
        if (route.linkUnavailable) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_link_unavailable_title)
                .setMessage(R.string.app_link_unavailable_text)
                .setPositiveButton(R.string.common_got_it, null)
                .show()
        }
        route.shortcutId()?.let { ShortcutManagerCompat.reportShortcutUsed(this, it) }
    }

    private fun revealResolvedGraph() {
        navigation.rootHost.childFragmentManager.executePendingTransactions()
        binding.sessionProgress.isVisible = false
        binding.navHostFragment.isVisible = true
    }

    private fun acceptIntent(intent: Intent) {
        val route = MainActivityIntentRouting.parse(
            intent.action,
            intent.getIntExtra(UserScreenArgs.ISU, 0),
            RecordbookSubjectArgs.from(intent.extras),
            launchedFromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0,
            link = intent.dataString
        ) ?: return
        routes.offer(route)
    }

    companion object {
        private const val BOOKINGS_WAIT_MILLIS = 8_000L
        private const val SPORT_TYPE_ID = 11
        private const val PENDING_USER = "pending_user_isu"
        private const val PENDING_ROOT = "pending_root_destination"
        private const val PENDING_SCREEN = "pending_screen"
        private const val PENDING_SUBJECT = "pending_subject"
        private const val PENDING_BARS_LOGIN = "pending_bars_login"
        private const val PENDING_TODAY = "pending_today"
        private const val PENDING_SPORT_LESSON = "pending_sport_lesson"
        private const val PENDING_SPORT_PREDICTED = "pending_sport_predicted"
        private const val PENDING_LINK_UNAVAILABLE = "pending_link_unavailable"
    }
}
