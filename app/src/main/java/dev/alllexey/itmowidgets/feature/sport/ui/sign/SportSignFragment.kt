package dev.alllexey.itmowidgets.feature.sport.ui.sign

import android.os.Bundle
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingConditions
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import dev.alllexey.itmowidgets.core.ui.SkeletonListAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.applyAppRefreshColors
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.databinding.FragmentSportSignBinding
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignCommand
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignEvent
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignViewModel
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCommonDetailsBottomSheet
import java.time.LocalDate
import javax.inject.Inject
import kotlin.getValue
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SportSignFragment : Fragment(), FilterActionsListener, SportSignActionsListener {

    @Inject lateinit var timeProvider: AcademicTimeProvider

    // region Binding

    private var _binding: FragmentSportSignBinding? = null
    private val binding get() = _binding!!

    // endregion

    // region Views

    private val recycler get() = binding.mainRecyclerView
    private val swipe get() = binding.swipeRefreshLayout

    // endregion

    // region State

    private lateinit var headerAdapter: FiltersHeaderAdapter
    private lateinit var lessonsAdapter: SportLessonsAdapter
    private lateinit var contentStateAdapter: ContentStateAdapter
    private val skeletonAdapter = SkeletonListAdapter()
    private lateinit var concatAdapter: ConcatAdapter
    private var feedbackSnackbar: Snackbar? = null

    private val viewModel: SportSignViewModel by activityViewModels()

    // endregion

    // region Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSportSignBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.setFragmentResultListener(SportCommonDetailsBottomSheet.ACTION_REQUEST, viewLifecycleOwner) { _, result ->
            val current = lessonsAdapter.currentList.firstOrNull {
                it.lesson.lessonId == result.getLong(SportCommonDetailsBottomSheet.RESULT_LESSON_ID)
            } ?: return@setFragmentResultListener
            if (current.isBusy) return@setFragmentResultListener
            val action = current.lesson.bookingConditions().evaluate(timeProvider.now()).action
            if (action.name != result.getString(SportCommonDetailsBottomSheet.RESULT_ACTION)) {
                showFeedback(R.string.sport_lesson_unavailable)
                return@setFragmentResultListener
            }
            when (action) {
                SportBookingAction.SIGN -> onSignUpClick(current.lesson)
                SportBookingAction.CANCEL -> onUnSignClick(current.lesson)
                SportBookingAction.AUTO -> onAutoSignClick(current.lesson)
                SportBookingAction.CANCEL_AUTO -> onUnAutoSignClick(current.lesson)
                SportBookingAction.NONE -> Unit
            }
        }
        setupRecyclerView()
        binding.swipeRefreshLayout.applyAppRefreshColors()
        setupUIListeners()
        observeViewModel()
    }

    override fun onPause() {
        feedbackSnackbar?.dismiss()
        super.onPause()
    }

    override fun onDestroyView() {
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = null
        binding.mainRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }

    // endregion

    // region Setup

    private fun setupRecyclerView() {
        headerAdapter = FiltersHeaderAdapter(
            listener = this
        )
        lessonsAdapter = SportLessonsAdapter(this, timeProvider)
        contentStateAdapter = ContentStateAdapter { viewModel.refreshAllData() }
        concatAdapter = ConcatAdapter(headerAdapter, skeletonAdapter, lessonsAdapter, contentStateAdapter)

        binding.mainRecyclerView.apply {
            adapter = concatAdapter
            layoutManager = LinearLayoutManager(requireContext())
            itemAnimator = null
        }
    }

    private fun setupUIListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshAllData()
        }
    }

    private fun observeViewModel() {
        viewModel.uiState
            // Populate the adjacent page before a ViewPager swipe finishes.
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                when (state) {
                    is SportSignUiState.Error -> showError(state)

                    SportSignUiState.Loading -> showLoading()

                    is SportSignUiState.Content -> onContent(state)
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // Only the active page reports its current error; resume starts a fresh feedback episode.
                var lastMessage: Int? = null
                viewModel.uiState.collect { state ->
                    val message = when (state) {
                        is SportSignUiState.Content -> if (state.hasPartialError) R.string.common_partial_load_error else null
                        is SportSignUiState.Error -> null
                        SportSignUiState.Loading -> null
                    }
                    if (message == lastMessage) return@collect
                    lastMessage = message
                    feedbackSnackbar?.dismiss()
                    feedbackSnackbar = null
                    if (message != null) showFeedback(message, retry = true)
                }
            }
        }

        viewModel.events
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.RESUMED)
            .onEach { event ->
                when (event) {
                    is SportSignEvent.ShowToast -> Toast.makeText(
                        context,
                        event.message.resolve(requireContext()),
                        Toast.LENGTH_SHORT
                    ).show()
                    is SportSignEvent.ShowError -> showFeedback(event.error.messageRes())
                    is SportSignEvent.ShowAutoSignConfirmDialog -> showConfirmDialog(event)
                    is SportSignEvent.ShowAutoSignDeleteDialog -> showDeleteDialog(event)
                    is SportSignEvent.ShowInfoDialog -> showInfoDialog(event)
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    /** Only before the first snapshot: a refresh with data arrives as `Content(refreshing = true)`. */
    private fun showLoading() {
        swipe.isRefreshing = false
        contentStateAdapter.submitState(null)
        skeletonAdapter.setVisible(true)
    }

    private fun onContent(state: SportSignUiState.Content) {
        swipe.isRefreshing = state.refreshing
        headerAdapter.updateState(state)
        if (state.initialLoading) {
            // Header and calendar are real; the list area waits for the catalogue.
            skeletonAdapter.setVisible(true)
            submitLessonsWithState(emptyList(), null)
            return
        }
        skeletonAdapter.setVisible(false)
        val contentState = if (state.displayedLessons.isEmpty()) {
            ContentState(
                iconRes = R.drawable.ic_event_note,
                title = getString(R.string.sport_lessons_empty_title),
                description = getString(R.string.sport_lessons_empty_description)
            )
        } else {
            null
        }
        submitLessonsWithState(
            state.displayedLessons.map { lesson ->
                SportLessonItem(
                    lesson = lesson,
                    isBusy = lesson.lessonId in state.busyLessonIds
                )
            },
            contentState
        )
    }

    private fun showError(state: SportSignUiState.Error) {
        swipe.isRefreshing = false
        skeletonAdapter.setVisible(false)
        val contentState = ContentState(
            iconRes = R.drawable.ic_error_rounded,
            title = getString(R.string.common_load_error_title),
            description = getString(state.error.messageRes()),
            action = getString(R.string.common_retry)
        )
        submitLessonsWithState(emptyList(), contentState)
    }

    private fun submitLessonsWithState(
        lessons: List<SportLessonItem>,
        contentState: ContentState?
    ) {
        // Both directions switch after the list commit, before its next draw.
        // Loading/error retain this latest successful content, even during a diff.
        val renderedBinding = binding
        lessonsAdapter.submitList(lessons) {
            if (_binding !== renderedBinding) return@submitList
            contentStateAdapter.submitState(contentState)
        }
    }

    private fun showFeedback(@androidx.annotation.StringRes message: Int, retry: Boolean = false) {
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).apply {
            if (retry) setAction(R.string.common_retry) { viewModel.refreshAllData() }
            show()
        }
    }

    override fun onSportClick() {
        showMultiSelectSearchableDialog()
    }

    override fun onShowOnlyAvailableChanged(isChecked: Boolean) {
        viewModel.showOnlyAvailable(isChecked)
    }

    override fun onShowOnlyFriendsChanged(isChecked: Boolean) {
        viewModel.showOnlyFriends(isChecked)
    }

    override fun onShowAutoSignChanged(isChecked: Boolean) {
        viewModel.showAutoSign(isChecked)
    }

    override fun onBuildingSelected(building: String?) {
        viewModel.selectBuilding(building)
    }

    override fun onTeacherSelected(teacher: String?) {
        viewModel.selectTeacher(teacher)
    }

    override fun onTimeSelected(time: String?) {
        viewModel.selectTime(time)
    }

    override fun onPrevWeekClick() {
        viewModel.prevWeek()
    }

    override fun onNextWeekClick() {
        viewModel.nextWeek()
    }

    override fun onDateSelected(date: LocalDate) {
        viewModel.selectDate(date)
    }

    override fun onResetFiltersClick() {
        viewModel.resetFilters()
    }

    override fun onSignUpClick(lesson: SportLesson) {
        if (handleTemplateAction(lesson)) return
        viewModel.signUpForLesson(lesson)
    }

    override fun onUnSignClick(lesson: SportLesson) {
        if (handleTemplateAction(lesson)) return
        viewModel.unSignForLesson(lesson)
    }

    override fun onAutoSignClick(lesson: SportLesson) {
        if (handleTemplateAction(lesson)) return
        viewModel.handleAutoSignClick(lesson)
    }

    override fun onUnAutoSignClick(lesson: SportLesson) {
        if (handleTemplateAction(lesson)) return
        viewModel.handleAutoSignClick(lesson)
    }

    private fun showMultiSelectSearchableDialog() {
        val selectableItems = viewModel.sportSections.map { sport ->
            SelectableItem(
                name = sport.shorten(),
                isSelected = viewModel.userFiltersFlow.value.selectedSportNames.contains(sport)
            )
        }.sortedWith(
            compareBy<SelectableItem> { !it.isSelected }
                .thenBy { item ->
                    SectionName.deshorten(item.name) !in viewModel.usedSportNames
                }
                .thenBy { it.name }
        )

        val dialogView = layoutInflater.inflate(R.layout.dialog_searchable_list, null)
        val searchEditText = dialogView.findViewById<TextInputEditText>(R.id.search_edit_text)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.items_recycler_view)

        val adapter = MultiSelectSearchableAdapter(selectableItems)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        searchEditText.addTextChangedListener { text -> adapter.filter(text.toString()) }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_done) { _, _ ->
                val selectedNames = adapter.getSelectedItems().map(SelectableItem::name)
                viewModel.selectSports(selectedNames.map { SectionName.deshorten(it) }.toSet())
            }
            .show()
    }

    private fun showConfirmDialog(event: SportSignEvent.ShowAutoSignConfirmDialog) {
        val view = layoutInflater.inflate(R.layout.dialog_free_sign, null)

        val toggle = view.findViewById<MaterialSwitch>(R.id.free_sign_switch)
        if (event.showForceSignButton) view.visibility = View.VISIBLE
        else view.visibility = View.GONE
        MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setTitle(event.title.resolve(requireContext()))
            .setMessage(event.message.resolve(requireContext()))
            .setNegativeButton(R.string.common_back, null)
            .setPositiveButton(R.string.sport_auto_sign_title) { _, _ ->
                viewModel.executeAutoSignCommand(event.command, toggle.isChecked)
            }
            .show()
    }

    private fun showDeleteDialog(event: SportSignEvent.ShowAutoSignDeleteDialog) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(event.message.resolve(requireContext()))
            .setNegativeButton(R.string.common_back, null)
            .setPositiveButton(R.string.sport_auto_sign_unsubscribe) { _, _ ->
                viewModel.executeAutoSignCommand(event.command)
            }
            .show()
    }

    private fun showInfoDialog(event: SportSignEvent.ShowInfoDialog) {
        MaterialAlertDialogBuilder(requireContext())
            .apply {
                event.title?.let { setTitle(it.resolve(requireContext())) }
            }
            .setMessage(event.message.resolve(requireContext()))
            .setPositiveButton(R.string.common_ok, null)
            .show()
    }

    override fun onLessonClick(lesson: SportLesson) {
        SportCommonDetailsBottomSheet.newInstance(lesson, actionsEnabled = true,
            busy = lessonsAdapter.currentList.any { it.lesson.lessonId == lesson.lessonId && it.isBusy })
            .show(childFragmentManager, SportCommonDetailsBottomSheet.TAG)
    }

    private fun handleTemplateAction(lesson: SportLesson): Boolean {
        if (lesson.lessonId >= 0) return false
        Toast.makeText(
            requireContext(),
            R.string.debug_sport_lesson_action_disabled,
            Toast.LENGTH_SHORT
        ).show()
        return true
    }

    // endregion
}
