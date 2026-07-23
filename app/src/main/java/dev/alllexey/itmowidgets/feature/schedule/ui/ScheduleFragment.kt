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
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentScheduleBinding
import dev.alllexey.itmowidgets.domain.model.schedule.DaySchedule
import dev.alllexey.itmowidgets.feature.friendselector.FriendSelectorDialogFragment
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleEvent
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.SelectedUser
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

        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { state ->
                renderSelectedUser(state.selectedUser)
                when (state) {
                    is ScheduleUiState.Loading -> showLoading()
                    is ScheduleUiState.Content -> renderSchedule(state)
                    is ScheduleUiState.Empty -> showEmptySchedule()
                    is ScheduleUiState.Error -> showError(state)
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.events.flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { event ->
                when (event) {
                    is ScheduleEvent.ShowError -> Snackbar.make(
                        binding.root,
                        event.error.messageRes(),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun setupFriendSelector() {

        parentFragmentManager.setFragmentResultListener(
            FriendSelectorDialogFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->

            if (bundle.getBoolean(FriendSelectorDialogFragment.RESULT_USE_MY_SCHEDULE)) {
                selectUser(null)
            } else {
                selectUser(
                    SelectedUser(
                        isu = bundle.getInt(FriendSelectorDialogFragment.RESULT_USER_ISU),
                        name = bundle.getString(
                            FriendSelectorDialogFragment.RESULT_USER_NAME
                        ).orEmpty(),
                        avatar = bundle.getString(
                            FriendSelectorDialogFragment.RESULT_USER_PICTURE_URL
                        )
                    )
                )
            }
        }
    }

    private fun openFriendSelector() {
        FriendSelectorDialogFragment.show(
            parentFragmentManager,
            viewModel.uiState.value.selectedUser?.isu
        )
    }

    private fun selectUser(user: SelectedUser?) {
        viewModel.setSelectedUser(user)
        swipe.isRefreshing = true
        viewModel.loadInitialSchedule()
    }

    // endregion

    // region Ui

    private fun showLoading() {
        swipe.isRefreshing = true
        if (adapter.itemCount == 0) {
            binding.scheduleStateContainer.isVisible = false
        }
    }

    private fun renderSchedule(state: ScheduleUiState.Content) {
        swipe.isRefreshing = state.loadingMore

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
        binding.scheduleStateDescription.setText(state.error.messageRes())
        binding.scheduleStateAction.isVisible = true
    }

    private fun renderSelectedUser(user: SelectedUser?) {
        binding.selectedUserCard.isVisible = user != null
        binding.selectedUserName.text = user?.name
        binding.selectedUserAvatar.setUser(user?.name, user?.avatar)
        fabFriend.isVisible = user == null && userIsu == null
    }

    private fun showEmptySchedule() {
        swipe.isRefreshing = false
        adapter.submitList(emptyList())
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
