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
import api.myitmo.model.sport.SportLesson
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.SportFreeSignRequest
import dev.alllexey.itmowidgets.databinding.FragmentSportSignBinding
import dev.alllexey.itmowidgets.ui.misc.SelectableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
                return SportSignViewModel(myItmo.api()) as T
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
            val animator = itemAnimator
            if (animator is SimpleItemAnimator) {
                animator.supportsChangeAnimations = false
            }
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

                if (state.errorMessage != null) {
                    Toast.makeText(context, state.errorMessage, Toast.LENGTH_SHORT).show()
                }

                headerAdapter.updateState(state)

                lessonsAdapter.submitList(state.displayedLessons)
            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onSportClick() {
        showMultiSelectSearchableDialog(viewModel.uiState.value)
    }

    override fun onFreeAttendanceChanged(isChecked: Boolean) {
        viewModel.setFreeAttendance(isChecked)
    }

    override fun onShowOnlyAvailableChanged(isChecked: Boolean) {
        viewModel.setShowOnlyAvailable(isChecked)
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

    override fun onDateSelected(date: LocalDate) {
        viewModel.selectDate(date)
    }

    override fun onPrevWeekClick() {
        viewModel.prevWeek()
    }

    override fun onNextWeekClick() {
        viewModel.nextWeek()
    }

    override fun onSignUpClick(lesson: SportLesson) {
        viewModel.signUpForLesson(lesson)
    }

    override fun onUnSignClick(lesson: SportLesson) {
        viewModel.unSignForLesson(lesson)
    }

    override fun onAutoSignClick(lesson: SportLesson) {
        showAutoSelectDialog(lesson, viewModel.uiState.value)
    }

    private fun showAutoSelectDialog(lesson: SportLesson, state: SportSignUiState) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContainer =
                    (requireContext().applicationContext as ItmoWidgetsApp).appContainer
                if (appContainer.storage.settings.getCustomServicesState()) {
                    val allEntries = appContainer.itmoWidgets.api().allFreeSignEntries()
                    val entry = allEntries.data?.find { it.lessonId == lesson.id }
                    if (entry != null) {
                        CoroutineScope(Dispatchers.Main).launch {
                            MaterialAlertDialogBuilder(requireContext())
                                .setMessage("У вас уже есть автозапись на это число. Позиция в очереди: ${entry.position} из ${entry.total}")
                                .setNegativeButton("Назад", null)
                                .setPositiveButton("Отписаться") { _, _ ->
                                    CoroutineScope(Dispatchers.IO).launch {
                                        appContainer.itmoWidgets.api().deleteFreeSignEntry(entry.id)
                                    }
                                }
                                .show()
                        }
                    } else {
                        CoroutineScope(Dispatchers.Main).launch {
                            MaterialAlertDialogBuilder(requireContext())
                                .setMessage("Вы можете встать в очередь на автозапись.")
                                .setNegativeButton("Назад", null)
                                .setPositiveButton("Автозапись") { _, _ ->
                                    CoroutineScope(Dispatchers.IO).launch {
                                        appContainer.itmoWidgets.api().createFreeSignEntry(
                                            SportFreeSignRequest(lessonId = lesson.id)
                                        )
                                    }
                                }
                                .show()
                        }
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        MaterialAlertDialogBuilder(requireContext())
                            .setMessage("У вас выключены неофициальные сервисы \uD83D\uDE1D\n\nИх можно включить в настройках")
                            .setPositiveButton("Хорошо", null)
                            .show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
                }
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun showMultiSelectSearchableDialog(state: SportSignUiState) {
        val selectableItems = state.availableSports.map { sport ->
            SelectableItem(
                name = sport.name,
                isSelected = state.selectedSportNames.contains(sport.name)
            )
        }.sortedWith(
            compareBy<SelectableItem> { !it.isSelected }.thenBy { it.name }
        )

        val dialogView = layoutInflater.inflate(R.layout.dialog_searchable_list, null)
        val searchEditText = dialogView.findViewById<TextInputEditText>(R.id.search_edit_text)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.items_recycler_view)

        val adapter = MultiSelectSearchableAdapter(selectableItems)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        searchEditText.addTextChangedListener { text ->
            adapter.filter(text.toString())
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Готово") { _, _ ->
                val selectedNames = adapter.getSelectedItems().map { it.name }.toSet()
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