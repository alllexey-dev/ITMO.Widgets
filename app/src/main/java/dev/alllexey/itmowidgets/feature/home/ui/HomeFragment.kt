package dev.alllexey.itmowidgets.feature.home.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.navigation.SettingsScreenArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.navigation.WidgetProviders
import dev.alllexey.itmowidgets.core.qr.QrPassImages
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.applyAppRefreshColors
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openLessonDetails
import dev.alllexey.itmowidgets.core.ui.navigation.openPendingSportDetails
import dev.alllexey.itmowidgets.core.ui.navigation.openRoot
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPinRequester
import dev.alllexey.itmowidgets.databinding.FragmentHomeBinding
import dev.alllexey.itmowidgets.feature.home.presentation.HomeEvent
import dev.alllexey.itmowidgets.feature.home.presentation.HomeUiState
import dev.alllexey.itmowidgets.feature.home.presentation.HomeViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class HomeFragment : Fragment() {

    // region Binding

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // endregion

    // region State

    private val viewModel: HomeViewModel by viewModels()
    private var adapter: HomeFeedAdapter? = null
    private var pinRequester: WidgetPinRequester? = null
    private var feedbackSnackbar: Snackbar? = null

    @Inject
    lateinit var qrImages: QrPassImages

    @Inject
    lateinit var timeProvider: AcademicTimeProvider

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onScreenResumed() }

    // endregion

    // region Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The launcher confirms a pin with this screen stopped; the hint source re-checks on return.
        pinRequester = WidgetPinRequester(requireContext()).also { it.start { viewModel.onScreenResumed() } }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFeed()
        setupListeners()
        setupObservers()
        viewModel.ensureDataLoaded()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
    }

    override fun onPause() {
        feedbackSnackbar?.dismiss()
        super.onPause()
    }

    override fun onDestroyView() {
        feedbackSnackbar = null
        adapter = null
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        pinRequester?.stop()
        pinRequester = null
        super.onDestroy()
    }

    // endregion

    // region Setup

    private fun setupFeed() {
        val feed = HomeFeedAdapter(
            actions = HomeFeedActions(
                onLesson = ::openLessonDetails,
                onPendingSport = ::openPendingSportDetails,
                onOpenQr = { openScreen(AppScreen.QR_PASS) },
                onOpenSport = { openRoot(AppRoot.SPORT) },
                onOpenFriends = { openScreen(AppScreen.FRIENDS) },
                onOpenUser = { user ->
                    openScreen(AppScreen.USER_PROFILE, bundleOf(UserScreenArgs.ISU to user.isu, UserScreenArgs.NAME to user.name))
                },
                onHint = ::actOnHint,
                onDismissHint = viewModel::dismissHint
            ),
            qrImages = qrImages,
            scope = viewLifecycleOwner.lifecycleScope,
            zoneId = timeProvider.zoneId
        )
        adapter = feed
        binding.homeFeed.adapter = feed
        binding.homeFeed.itemAnimator = null
        binding.swipeRefresh.applyAppRefreshColors()
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.webFab.setOnClickListener { openScreen(AppScreen.MY_ITMO_WEB) }
    }

    private fun setupObservers() {
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                when (state) {
                    HomeUiState.Loading -> showLoading()
                    is HomeUiState.Content -> showContent(state)
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.events.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.RESUMED)
            .onEach { event ->
                when (event) {
                    is HomeEvent.RefreshFailed -> showFeedback()
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    // endregion

    // region UI

    private fun showLoading() {
        binding.loading.isVisible = true
        binding.homeFeed.isVisible = false
        binding.emptyState.isVisible = false
        binding.swipeRefresh.isRefreshing = false
    }

    private fun showContent(state: HomeUiState.Content) {
        binding.loading.isVisible = false
        binding.swipeRefresh.isRefreshing = state.refreshing
        val rendered = binding
        adapter?.submitCards(state.cards) {
            if (_binding !== rendered) return@submitCards
            rendered.homeFeed.isVisible = state.cards.isNotEmpty()
            rendered.emptyState.isVisible = state.cards.isEmpty()
        }
    }

    private fun showFeedback() {
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = Snackbar.make(binding.root, R.string.common_partial_load_error, Snackbar.LENGTH_LONG)
            .setAnchorView(binding.webFab)
            .setAction(R.string.common_retry) { viewModel.refresh() }
            .also(Snackbar::show)
    }

    private fun actOnHint(hint: HomeHint) {
        when (hint) {
            HomeHint.WIDGETS -> pinRequester?.request(WidgetProviders.SINGLE_LESSON)
            HomeHint.NOTIFICATIONS -> requestNotifications()
            HomeHint.SERVICES -> openScreen(AppScreen.SETTINGS, bundleOf(SettingsScreenArgs.PAGE to SERVICES_PAGE))
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            )
        }
    }

    // endregion

    private companion object {
        const val SERVICES_PAGE = "SERVICES"
    }
}
