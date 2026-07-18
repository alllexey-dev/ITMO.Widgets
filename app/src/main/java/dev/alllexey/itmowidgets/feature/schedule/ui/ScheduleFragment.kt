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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentScheduleBinding
import dev.alllexey.itmowidgets.domain.model.schedule.DaySchedule
import dev.alllexey.itmowidgets.feature.friendselector.FriendSelectorDialogFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    private val chipUser get() = binding.chipSelectedUser

    // endregion

    // region State

    private lateinit var adapter: DayScheduleAdapter
    private var listState: Parcelable? = null
    private var hasScrolledToToday = false

    private val KEY_LIST_STATE = "list_state"

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
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(
            KEY_LIST_STATE,
            recycler.layoutManager?.onSaveInstanceState()
        )
        super.onSaveInstanceState(outState)
    }

    // endregion

    // region Setup

    private fun setupUI() {
        val color = requireContext().color
        swipe.setColorSchemeColors(color.primary)
        swipe.setProgressBackgroundColorSchemeColor(color.background)
        fabFriend.visibility = if (userIsu == null) View.VISIBLE else View.GONE
    }

    private fun setupRecycler() {

        adapter = DayScheduleAdapter(timeProvider)

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
            FriendSelectorDialogFragment.show(parentFragmentManager)
        }

        chipUser.setOnCloseIconClickListener {
            viewModel.setSelectedUser(null)
            swipe.isRefreshing = true
            viewModel.loadInitialSchedule()
        }

        binding.scheduleStateAction.setOnClickListener {
            viewModel.loadInitialSchedule(forceRefresh = true)
        }
    }

    private fun setupObservers() {

        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { state ->
                when (state) {
                    is ScheduleUiState.Loading -> showLoading()
                    is ScheduleUiState.Success -> renderSchedule(state)
                    is ScheduleUiState.Error -> showError(state)
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.selectedUser.flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach {
                renderSelectedUser(it)
            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun setupFriendSelector() {

        parentFragmentManager.setFragmentResultListener(
            FriendSelectorDialogFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->

            val isu = bundle.getInt(FriendSelectorDialogFragment.RESULT_USER_ISU)
            val name = bundle.getString(FriendSelectorDialogFragment.RESULT_USER_NAME)
            val avatar = bundle.getString(FriendSelectorDialogFragment.RESULT_USER_PICTURE_URL)

            viewModel.setSelectedUser(
                SelectedUser(isu, name, avatar)
            )

            swipe.isRefreshing = true
            viewModel.loadInitialSchedule()
        }
    }

    // endregion

    // region Ui

    private fun showLoading() {
        swipe.isRefreshing = true
        if (adapter.itemCount == 0) {
            binding.scheduleStateContainer.isVisible = false
        }
    }

    private fun renderSchedule(state: ScheduleUiState.Success) {
        swipe.isRefreshing = state.isLoadingMore

        if (state.schedule.isEmpty()) {
            adapter.submitList(emptyList())
            showEmptySchedule()
            return
        }

        binding.scheduleStateContainer.isVisible = false

        adapter.submitList(state.schedule) {
            restoreScrollState()
            tryScrollToToday(state.schedule)
        }
    }

    private fun showError(state: ScheduleUiState.Error) {
        swipe.isRefreshing = false
        if (adapter.itemCount > 0) return

        binding.scheduleStateContainer.isVisible = true
        binding.scheduleStateIcon.setImageResource(R.drawable.ic_error)
        binding.scheduleStateTitle.setText(R.string.common_load_error_title)
        binding.scheduleStateDescription.text = state.message
        binding.scheduleStateAction.isVisible = true
    }

    private fun renderSelectedUser(user: SelectedUser?) {
        chipUser.isVisible = user != null
        chipUser.text = user?.name
    }

    private fun showEmptySchedule() {
        binding.scheduleStateContainer.isVisible = true
        binding.scheduleStateIcon.setImageResource(R.drawable.ic_event_note)
        binding.scheduleStateTitle.setText(R.string.schedule_empty_title)
        binding.scheduleStateDescription.setText(R.string.schedule_empty_description)
        binding.scheduleStateAction.isVisible = false
        hideFabScrollToTop()
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

    private fun tryScrollToToday(schedule: List<DaySchedule>) {

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

    private fun restoreState(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        listState = savedInstanceState?.getParcelable(KEY_LIST_STATE)
    }

    private fun restoreScrollState() {
        listState?.let {
            recycler.layoutManager?.onRestoreInstanceState(it)
            listState = null
            hasScrolledToToday = true
        }
    }

    // endregion

    companion object {

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
