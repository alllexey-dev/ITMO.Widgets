package dev.alllexey.itmowidgets.feature.schedule.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.applyAppRefreshColors
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.openPendingSportDetails
import dev.alllexey.itmowidgets.databinding.FragmentScheduleBinding
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDisplayDay
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleEvent
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.SelectedUser
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.getValue

@AndroidEntryPoint
class ScheduleFragment : Fragment() {

    // region Binding

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    // endregion

    // region Views

    private val recycler get() = binding.outerRecyclerView
    private val swipe get() = binding.swipeRefreshLayout
    private val fabTop get() = binding.fabScrollToTop
    private val fabFriend get() = binding.fabOpenFriendSelector

    // endregion

    // region State

    private var scheduleAdapter: DayScheduleAdapter? = null
    private val adapter get() = checkNotNull(scheduleAdapter)
    private var listState: Parcelable? = null
    private var hasScrolledToToday = false
    private var errorSnackbar: Snackbar? = null
    private var pendingDayAnchor: DayAnchor? = null
    private var anchorUserIsu: Int? = null
    private var anchorPageRequests = 0
    private var anchorPagedThrough: LocalDate? = null

    private val viewModel: ScheduleViewModel by viewModels()

    @Inject
    lateinit var timeProvider: AcademicTimeProvider

    // endregion

    // region Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        setupUI()
        setupRecycler()
        setupListeners()
        setupObservers()
        setupFriendSelector()

        restoreState(savedInstanceState)

        viewModel.ensureDataLoaded()
    }

    override fun onDestroyView() {
        errorSnackbar?.dismiss()
        errorSnackbar = null
        listState = currentScrollState() ?: listState
        _binding?.outerRecyclerView?.apply {
            clearOnScrollListeners()
            stopScroll()
            adapter = null
        }
        scheduleAdapter = null
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // A back-stack Fragment can be saved after its view has been destroyed.
        outState.putParcelable(KEY_LIST_STATE, currentScrollState() ?: listState)
        super.onSaveInstanceState(outState)
    }

    // endregion

    // region Setup

    private fun setupUI() {
        swipe.applyAppRefreshColors()
        fabFriend.visibility = if (userIsu == null) View.VISIBLE else View.GONE
    }

    /**
     * The own schedule's sport lessons are bookings: the navigator hands them to the
     * sport tab's sheet with `Отменить`. Everything else, and a friend's schedule,
     * opens the lesson sheet here.
     */
    private fun showLessonDetails(lesson: Lesson, date: LocalDate) {
        if (childFragmentManager.findFragmentByTag(LessonDetailsBottomSheet.TAG) != null) return
        val navigator = activity as? AppNavigator
        if (navigator != null && userIsu == null && lesson.typeId.raw == SPORT_TYPE_ID) {
            navigator.openLessonDetails(lesson.toDetailsArgs(date))
            return
        }
        LessonDetailsBottomSheet.newInstance(lesson, date).show(childFragmentManager, LessonDetailsBottomSheet.TAG)
    }

    /** Through the navigator, so the sport tab's sheet with its actions can answer when it knows the queue. */
    private fun showPendingSportDetails(booking: PendingSportBooking) = openPendingSportDetails(booking.toDetailsArgs())

    private fun setupRecycler() {
        recycler.itemAnimator = null

        scheduleAdapter = DayScheduleAdapter(timeProvider, ::showLessonDetails, ::showPendingSportDetails).apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {

                val lm = rv.layoutManager as LinearLayoutManager

                val first = lm.findFirstVisibleItemPosition()
                val last = lm.findLastVisibleItemPosition()
                val total = adapter.itemCount

                if (total > 0 && last > total - 3) {
                    viewModel.fetchNextDays()
                }

                toggleScrollFab(first)
            }
        })
    }

    private fun setupListeners() {

        swipe.setOnRefreshListener {
            viewModel.loadInitialSchedule(forceRefresh = true)
        }

        fabTop.setOnClickListener {
            recycler.smoothScrollToPosition(1)
        }

        fabFriend.setOnClickListener {
            openFriendSelector()
        }

        binding.selectedUserChangeButton.setOnClickListener {
            openFriendSelector()
        }

        binding.selectedUserClearButton.setOnClickListener {
            selectUser(null)
        }

        binding.scheduleStateAction.setOnClickListener {
            viewModel.loadInitialSchedule(forceRefresh = true)
        }
    }

    private fun setupObservers() {

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    // Time changes presentation only: do not reload data or reset the list.
                    viewModel.updateTimeState()
                    scheduleAdapter?.updateLessonStates()
                    val now = timeProvider.now()
                    delay(60_000L - now.second * 1_000L - now.nano / 1_000_000L)
                }
            }
        }

        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { state ->
                renderSelectedUser(state.selectedUser)
                when (state) {
                    is ScheduleUiState.Content -> renderSchedule(state)
                    else -> renderNonContent(state)
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.events.flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { event ->
                when (event) {
                    is ScheduleEvent.ShowError -> {
                        errorSnackbar?.dismiss()
                        errorSnackbar = Snackbar.make(binding.root, event.error.messageRes(), Snackbar.LENGTH_LONG)
                            .setAction(R.string.common_retry) { viewModel.loadInitialSchedule(forceRefresh = true) }
                            .also(Snackbar::show)
                    }
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun setupFriendSelector() {

        parentFragmentManager.setFragmentResultListener(
            FriendSelectionContract.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->

            if (bundle.getBoolean(FriendSelectionContract.RESULT_USE_MY_SCHEDULE)) {
                selectUser(null)
            } else {
                selectUser(
                    SelectedUser(
                        isu = bundle.getInt(FriendSelectionContract.RESULT_USER_ISU),
                        name = bundle.getString(
                            FriendSelectionContract.RESULT_USER_NAME
                        ).orEmpty(),
                        avatar = bundle.getString(
                            FriendSelectionContract.RESULT_USER_PICTURE_URL
                        )
                    )
                )
            }
        }
    }

    private fun openFriendSelector() {
        findNavController().navigate(
            R.id.friend_selector,
            Bundle().apply {
                putInt(
                    FriendSelectionContract.ARG_SELECTED_ISU,
                    viewModel.uiState.value.selectedUser?.isu
                        ?: FriendSelectionContract.NO_USER_ISU
                )
            }
        )
    }

    private fun selectUser(user: SelectedUser?) {
        // Another schedule of the same days: keep the reader on the day and the
        // offset they were reading instead of rewinding the list to today. An
        // empty list (a denied or still loading schedule) keeps the last anchor.
        pendingDayAnchor = currentDayAnchor() ?: pendingDayAnchor
        anchorUserIsu = user?.isu
        anchorPageRequests = 0
        anchorPagedThrough = null
        viewModel.setSelectedUser(user)
        swipe.isRefreshing = true
        viewModel.loadInitialSchedule()
    }

    // endregion

    // region Ui

    private fun renderSchedule(state: ScheduleUiState.Content) {
        swipe.isRefreshing = state.loadingMore

        binding.scheduleStateContainer.isVisible = false
        binding.scheduleSkeleton.isVisible = false

        val renderedBinding = binding
        adapter.submitList(state.displayDays) {
            // AsyncListDiffer may finish after navigation or even after a new view exists.
            if (_binding !== renderedBinding) return@submitList
            if (viewModel.uiState.value !is ScheduleUiState.Content) return@submitList
            restoreScrollState()
            if (restoreDayAnchor(state)) {
                // Never show the new schedule rewound to its first day: the
                // anchored day is still being paged in.
                renderedBinding.outerRecyclerView.visibility = View.INVISIBLE
                return@submitList
            }
            tryScrollToToday(state.displayDays)
            renderedBinding.outerRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun renderSelectedUser(user: SelectedUser?) {
        binding.selectedUserCard.isVisible = user != null
        binding.selectedUserName.text = user?.name
        binding.selectedUserAvatar.setUser(user?.name, user?.avatar)
        fabFriend.isVisible = user == null && userIsu == null
    }

    private fun renderNonContent(state: ScheduleUiState) {
        swipe.isRefreshing = state is ScheduleUiState.Loading
        binding.scheduleStateContainer.isVisible = false
        // Only a first load without any cache shows the placeholder; a refresh keeps its rows.
        binding.scheduleSkeleton.isVisible = state is ScheduleUiState.Loading
        // Only Content may retain rows during a refresh. Pending-only rows may
        // already be disabled/private while an academic request is still running.
        // Hide them immediately and reconcile the list before showing a state.
        recycler.visibility = View.INVISIBLE
        val renderedBinding = binding
        adapter.submitList(emptyList()) {
            if (_binding !== renderedBinding) return@submitList
            when (val latest = viewModel.uiState.value) {
                is ScheduleUiState.Empty -> {
                    renderedBinding.swipeRefreshLayout.isRefreshing = false
                    renderedBinding.scheduleStateIcon.setImageResource(R.drawable.ic_event_note)
                    renderedBinding.scheduleStateTitle.setText(R.string.schedule_empty_title)
                    renderedBinding.scheduleStateDescription.setText(R.string.schedule_empty_description)
                    renderedBinding.scheduleStateAction.isVisible = false
                    renderedBinding.scheduleStateContainer.isVisible = true
                }
                is ScheduleUiState.Error -> {
                    renderedBinding.swipeRefreshLayout.isRefreshing = false
                    renderedBinding.scheduleStateIcon.setImageResource(R.drawable.ic_error_rounded)
                    renderedBinding.scheduleStateTitle.setText(R.string.common_load_error_title)
                    renderedBinding.scheduleStateDescription.setText(latest.error.messageRes())
                    renderedBinding.scheduleStateAction.isVisible = true
                    renderedBinding.scheduleStateContainer.isVisible = true
                }
                is ScheduleUiState.Loading -> {
                    renderedBinding.swipeRefreshLayout.isRefreshing = true
                    renderedBinding.scheduleStateContainer.isVisible = false
                }
                is ScheduleUiState.Content -> return@submitList
            }
            hideFabScrollToTop()
        }
    }

    // endregion

    // region Scroll helpers

    private fun toggleScrollFab(firstVisible: Int) {
        if (firstVisible > 2) showFabScrollToTop()
        else hideFabScrollToTop()
    }

    private fun showFabScrollToTop() {
        if (!fabTop.isShown) fabTop.show()
    }

    private fun hideFabScrollToTop() {
        if (fabTop.isShown) fabTop.hide()
    }

    private fun currentDayAnchor(): DayAnchor? {
        val layout = recycler.layoutManager as? LinearLayoutManager ?: return null
        val position = layout.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) return null
        val date = adapter.currentList.getOrNull(position)?.date ?: return null
        val top = layout.findViewByPosition(position)?.top ?: return null
        return DayAnchor(date, top - recycler.paddingTop)
    }

    /** Returns true while the anchored day is still outside the loaded range. */
    private fun restoreDayAnchor(state: ScheduleUiState.Content): Boolean {

        val anchor = pendingDayAnchor ?: return false
        // A diff committed after the switch can still carry the previous
        // schedule; anchoring it would fix the list on the wrong days.
        if (state.selectedUser?.isu != anchorUserIsu) return true
        val schedule = state.displayDays
        // A day without lessons is missing from another user's list: the next
        // day it does have is the closest thing to the same place in the term.
        val index = schedule.indexOfFirst { !it.date.isBefore(anchor.date) }

        if (index != -1) {
            (recycler.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(index, anchor.offset)
            pendingDayAnchor = null
            hasScrolledToToday = true
            return false
        }

        // Another user's schedule starts from the initial range again, so a day
        // reached by pagination needs its pages before it can be anchored.
        if (state.loadingMore) return true
        val lastDate = schedule.lastOrNull()?.date
        if (lastDate != null && lastDate != anchorPagedThrough &&
            anchorPageRequests < MAX_ANCHOR_PAGE_REQUESTS) {
            anchorPagedThrough = lastDate
            anchorPageRequests++
            viewModel.fetchNextDays()
            return true
        }

        // An unreachable day: open the schedule on today, as a fresh screen does.
        pendingDayAnchor = null
        hasScrolledToToday = false
        return false
    }

    private fun tryScrollToToday(schedule: List<ScheduleDisplayDay>) {

        if (hasScrolledToToday) return

        val today = timeProvider.today()
        val index = schedule.indexOfFirst { it.date >= today }

        if (index != -1) {
            (recycler.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(index, 20)

            hasScrolledToToday = true
        }
    }

    // endregion

    // region State

    private fun currentScrollState(): Parcelable? {
        val currentRecycler = _binding?.outerRecyclerView ?: return null
        // Keep a pending restored anchor while data has not reached the new adapter.
        if (currentRecycler.adapter?.itemCount == 0) return null
        return currentRecycler.layoutManager?.onSaveInstanceState()
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        listState = listState ?: savedInstanceState?.getParcelable(KEY_LIST_STATE)
    }

    private fun restoreScrollState() {
        listState?.let {
            recycler.layoutManager?.onRestoreInstanceState(it)
            listState = null
            hasScrolledToToday = true
        }
    }

    // endregion

    /** The day a reader is looking at, and how far into it they have scrolled. */
    private data class DayAnchor(val date: LocalDate, val offset: Int)

    companion object {
        private const val SPORT_TYPE_ID = 11

        private const val KEY_LIST_STATE = "list_state"
        private const val MAX_ANCHOR_PAGE_REQUESTS = 4

        fun newInstance(userIsu: Int?): ScheduleFragment {
            return ScheduleFragment().apply {
                arguments = Bundle().apply {
                    putInt(ScheduleViewModel.ARG_USER_ISU, userIsu ?: -1)
                }
            }
        }
    }

    private val userIsu: Int? by lazy {
        val value = arguments?.getInt(ScheduleViewModel.ARG_USER_ISU, -1) ?: -1
        if (value == -1) null else value
    }
}
