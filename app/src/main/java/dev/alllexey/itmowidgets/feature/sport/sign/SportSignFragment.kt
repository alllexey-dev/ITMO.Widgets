package dev.alllexey.itmowidgets.feature.sport.sign

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.doOnLayout
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.gson.Gson
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.core.util.dp
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
        concatAdapter = ConcatAdapter(headerAdapter, lessonsAdapter)

        binding.mainRecyclerView.apply {
            adapter = concatAdapter
            layoutManager = LinearLayoutManager(requireContext())
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            doOnLayout {
                headerAdapter.setCalendarWidth(width - 32.dp)
            }
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
                    is SportSignEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    is SportSignEvent.ShowError -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    is SportSignEvent.ShowAutoSignConfirmDialog -> showConfirmDialog(event)
                    is SportSignEvent.ShowAutoSignDeleteDialog -> showDeleteDialog(event)
                    is SportSignEvent.ShowInfoDialog -> showInfoDialog(event)
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun showLoading() {
        swipe.isRefreshing = true
    }

    private fun onSuccess(state: SportSignUiState.Success) {
        swipe.isRefreshing = false
        headerAdapter.updateState(state)
        lessonsAdapter.submitList(state.displayedLessons)
        if (state.hasPartialError) {
            Toast.makeText(requireContext(), "Не удалось загрузить некоторые данные", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError(state: SportSignUiState.Error) {
        swipe.isRefreshing = false
        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
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

    override fun onBuildingSelected(building: String) {
        viewModel.selectBuilding(building)
    }

    override fun onTeacherSelected(teacher: String) {
        viewModel.selectTeacher(teacher)
    }

    override fun onTimeSelected(time: String) {
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

    override fun onSignUpClick(lesson: SportLesson) {
        viewModel.signUpForLesson(lesson)
    }

    override fun onUnSignClick(lesson: SportLesson) {
        viewModel.unSignForLesson(lesson)
    }

    override fun onAutoSignClick(lesson: SportLesson) {
        viewModel.handleAutoSignClick(lesson)
    }

    override fun onUnAutoSignClick(lesson: SportLesson) {
        viewModel.handleAutoSignClick(lesson)
    }

    private fun showMultiSelectSearchableDialog() {
        val selectableItems = viewModel.sportSections.map { sport ->
            SelectableItem(
                name = sport.shorten() + if (viewModel.usedSportNames.contains(sport)) " \uD83D\uDD25" else "",
                isSelected = viewModel.userFiltersFlow.value.selectedSportNames.contains(sport)
            )
        }.sortedWith(
            compareBy<SelectableItem> { !it.isSelected }
                .thenBy { !it.name.contains("\uD83D\uDD25") }
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
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Готово") { _, _ ->
                val selectedNames = adapter.getSelectedItems().map { it.name.replace(" \uD83D\uDD25", "") }
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
            .setTitle(event.title)
            .setMessage(event.message)
            .setNegativeButton("Назад", null)
            .setPositiveButton("Автозапись") { _, _ -> event.action(toggle.isChecked) }
            .show()
    }

    private fun showDeleteDialog(event: SportSignEvent.ShowAutoSignDeleteDialog) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(event.message)
            .setNegativeButton("Назад", null)
            .setPositiveButton("Отписаться") { _, _ -> event.action() }
            .show()
    }

    private fun showInfoDialog(event: SportSignEvent.ShowInfoDialog) {
        MaterialAlertDialogBuilder(requireContext())
            .apply { if (event.title != null) setTitle(event.title) }
            .setMessage(event.message)
            .setPositiveButton("Хорошо", null)
            .show()
    }

    override fun onLessonClick(lesson: SportLesson) {
        SportCommonDetailsBottomSheet.newInstance(lesson, gson)
            .show(parentFragmentManager, SportCommonDetailsBottomSheet.TAG)
    }

    // endregion
}
