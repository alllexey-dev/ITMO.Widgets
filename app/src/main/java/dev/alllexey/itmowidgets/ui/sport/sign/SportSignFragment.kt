package dev.alllexey.itmowidgets.ui.sport.sign

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.FragmentSportSignBinding
import dev.alllexey.itmowidgets.ui.misc.SelectableItem
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate

class SportSignFragment : Fragment(R.layout.fragment_sport_sign), FilterActionsListener,
    SportSignActionsListener {

    private var _binding: FragmentSportSignBinding? = null
    private val binding get() = _binding!!

    private lateinit var headerAdapter: FiltersHeaderAdapter
    private lateinit var lessonsAdapter: SportLessonsAdapter
    private lateinit var concatAdapter: ConcatAdapter

    private val viewModel: SportSignViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val myItmo = (requireActivity().application as ItmoWidgetsApp).appContainer.myItmo
                return SportSignViewModel(myItmo.api(), requireContext()) as T
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSportSignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupUIListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        headerAdapter = FiltersHeaderAdapter(this)
        lessonsAdapter = SportLessonsAdapter(this)
        lessonsAdapter.setBuildingsMap(viewModel.allBuildingsMap)
        concatAdapter = ConcatAdapter(headerAdapter, lessonsAdapter)

        binding.mainRecyclerView.apply {
            adapter = concatAdapter
            layoutManager = LinearLayoutManager(requireContext())
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }
    }

    private fun setupUIListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadInitialData()
        }
    }

    private fun observeViewModel() {
        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { state ->
                binding.swipeRefreshLayout.isRefreshing = state.isLoading
                headerAdapter.updateState(state)
                lessonsAdapter.submitList(state.displayedLessons)
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

    override fun onSportClick() {
        showMultiSelectSearchableDialog(viewModel.uiState.value)
    }

    override fun onFreeAttendanceChanged(isChecked: Boolean) = viewModel.setFreeAttendance(isChecked)
    override fun onShowOnlyAvailableChanged(isChecked: Boolean) = viewModel.setShowOnlyAvailable(isChecked)
    override fun onShowAutoSignChanged(isChecked: Boolean) = viewModel.setShowAutoSign(isChecked)
    override fun onBuildingSelected(building: String) = viewModel.selectBuilding(building)
    override fun onTeacherSelected(teacher: String) = viewModel.selectTeacher(teacher)
    override fun onTimeSelected(time: String) = viewModel.selectTime(time)
    override fun onDateSelected(date: LocalDate) = viewModel.selectDate(date)
    override fun onPrevWeekClick() = viewModel.prevWeek()
    override fun onNextWeekClick() = viewModel.nextWeek()

    override fun onSignUpClick(lesson: SportLessonData) {
        Toast.makeText(requireContext(), "Выполняем запись...", Toast.LENGTH_SHORT).show()
        viewModel.signUpForLesson(lesson)
    }

    override fun onUnSignClick(lesson: SportLessonData) {
        Toast.makeText(requireContext(), "Отменяем запись...", Toast.LENGTH_SHORT).show()
        viewModel.unSignForLesson(lesson)
    }

    override fun onAutoSignClick(lesson: SportLessonData) = viewModel.handleAutoSignClick(lesson)
    override fun onUnAutoSignClick(lesson: SportLessonData) = viewModel.handleAutoSignClick(lesson)

    private fun showConfirmDialog(event: SportSignEvent.ShowAutoSignConfirmDialog) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(event.title)
            .setMessage(event.message)
            .setNegativeButton("Назад", null)
            .setPositiveButton("Автозапись") { _, _ -> event.action() }
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

    private fun showMultiSelectSearchableDialog(state: SportSignUiState) {
        val selectableItems = state.availableSports.map { sport ->
            SelectableItem(
                name = sport.name + if (viewModel.usedSportNames.contains(sport.name)) " \uD83D\uDD25" else "",
                isSelected = state.selectedSportNames.contains(sport.name)
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
                val selectedNames = adapter.getSelectedItems().map { it.name.replace(" \uD83D\uDD25", "") }.toSet()
                viewModel.selectSports(selectedNames)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mainRecyclerView.adapter = null
        _binding = null
    }
}