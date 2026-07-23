package dev.alllexey.itmowidgets.feature.sport.sign

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentSportSignBinding
import dev.alllexey.itmowidgets.domain.model.sport.SectionName
import dev.alllexey.itmowidgets.domain.model.sport.SportLesson
import dev.alllexey.itmowidgets.feature.sport.common.SportCommonDetailsBottomSheet
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import javax.inject.Inject
import kotlin.getValue

@AndroidEntryPoint
class SportSignFragment : Fragment(), FilterActionsListener, SportSignActionsListener {

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
    private lateinit var concatAdapter: ConcatAdapter

    private val viewModel: SportSignViewModel by activityViewModels()

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var settings: AppSettingsStorage

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
        setupRecyclerView()
        val color = requireContext().color
        binding.swipeRefreshLayout.setColorSchemeColors(color.primary)
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(color.background)
        setupUIListeners()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // endregion

    // region Setup

    private fun setupRecyclerView() {
        headerAdapter = FiltersHeaderAdapter(
            listener = this,
            hideTeacherSelector = settings.getSportSignHideTeacherSelectorEnabled(),
            hideTimeSelector = settings.getSportSignHideTimeSelectorEnabled()
        )
        lessonsAdapter = SportLessonsAdapter(this)
        contentStateAdapter = ContentStateAdapter { viewModel.refreshAllData() }
        concatAdapter = ConcatAdapter(headerAdapter, lessonsAdapter, contentStateAdapter)

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
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { state ->
                when (state) {
                    is SportSignUiState.Error -> showError(state)

                    SportSignUiState.Loading -> showLoading()

                    is SportSignUiState.Success -> onSuccess(state)
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.events
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { event ->
                when (event) {
                    is SportSignEvent.ShowToast -> Toast.makeText(
                        context,
                        event.message.resolve(requireContext()),
                        Toast.LENGTH_SHORT
                    ).show()
                    is SportSignEvent.ShowError -> Toast.makeText(
                        context,
                        event.error.messageRes(),
                        Toast.LENGTH_LONG
                    ).show()
                    is SportSignEvent.ShowAutoSignConfirmDialog -> showConfirmDialog(event)
                    is SportSignEvent.ShowAutoSignDeleteDialog -> showDeleteDialog(event)
                    is SportSignEvent.ShowInfoDialog -> showInfoDialog(event)
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun showLoading() {
        swipe.isRefreshing = true
        contentStateAdapter.submitState(null)
    }

    private fun onSuccess(state: SportSignUiState.Success) {
        swipe.isRefreshing = false
        headerAdapter.updateState(state)
        val contentState = if (state.displayedLessons.isEmpty()) {
            ContentState(
                iconRes = R.drawable.ic_event_note,
                title = getString(R.string.sport_lessons_empty_title),
                description = getString(R.string.sport_lessons_empty_description)
            )
        } else {
            null
        }
        submitLessonsWithState(state.displayedLessons, contentState)
        if (state.hasPartialError) {
            Toast.makeText(
                requireContext(),
                R.string.common_partial_load_error,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showError(state: SportSignUiState.Error) {
        swipe.isRefreshing = false
        val contentState = ContentState(
            iconRes = R.drawable.ic_error,
            title = getString(R.string.common_load_error_title),
            description = getString(state.error.messageRes()),
            action = getString(R.string.common_retry)
        )
        submitLessonsWithState(emptyList(), contentState)
    }

    private fun submitLessonsWithState(
        lessons: List<SportLesson>,
        contentState: ContentState?
    ) {
        contentStateAdapter.submitState(null)
        lessonsAdapter.submitList(lessons) {
            contentStateAdapter.submitState(contentState)
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
        SportCommonDetailsBottomSheet.newInstance(lesson, gson)
            .show(parentFragmentManager, SportCommonDetailsBottomSheet.TAG)
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
