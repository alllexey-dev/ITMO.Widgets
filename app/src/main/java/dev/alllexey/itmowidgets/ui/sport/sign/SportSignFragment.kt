package dev.alllexey.itmowidgets.ui.sport.sign

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.FragmentSportSignBinding
import dev.alllexey.itmowidgets.ui.misc.SelectableItem
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.concurrent.thread

class SportSignFragment : Fragment(R.layout.fragment_sport_sign) {

    private var _binding: FragmentSportSignBinding? = null
    private val binding get() = _binding!!

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

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUIListeners()
        view.setOnTouchListener { _, _ ->
            binding.buildingAutoComplete.clearFocus()
            binding.teacherAutoComplete.clearFocus()
            binding.timeAutoComplete.clearFocus()
            false
        }
        observeViewModel()
    }

    private fun setupUIListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadInitialData()
        }

        binding.sportEditText.setOnClickListener {
            showMultiSelectSearchableDialog(viewModel.uiState.value)
        }

        binding.freeSportSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setFreeAttendance(isChecked)
        }

        binding.buildingAutoComplete.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.adapter.getItem(position) as String
            viewModel.selectBuilding(selected)
            binding.buildingAutoComplete.clearFocus()
        }

        // weird workaround
        binding.buildingAutoComplete.setOnDismissListener {
            binding.buildingAutoComplete.dismissDropDown()
            thread {
                Thread.sleep(50)
                binding.buildingAutoComplete.post { binding.buildingAutoComplete.clearFocus() }
            }
        }

        binding.teacherAutoComplete.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.adapter.getItem(position) as String
            viewModel.selectTeacher(selected)
            binding.teacherAutoComplete.clearFocus()
        }

        binding.teacherAutoComplete.setOnDismissListener {
            binding.teacherAutoComplete.dismissDropDown()
            thread {
                Thread.sleep(50)
                binding.teacherAutoComplete.post { binding.teacherAutoComplete.clearFocus() }
            }
        }

        binding.timeAutoComplete.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.adapter.getItem(position) as String
            viewModel.selectTime(selected)
            binding.timeAutoComplete.clearFocus()
        }

        binding.timeAutoComplete.setOnDismissListener {
            binding.timeAutoComplete.dismissDropDown()
            thread {
                Thread.sleep(50)
                binding.timeAutoComplete.post { binding.timeAutoComplete.clearFocus() }
            }
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

                binding.sportEditText.setText(state.selectedSportNames.joinToString(", ").ifEmpty { null })

                updateAdapter(binding.buildingAutoComplete, state.availableBuildings)
                updateAdapter(binding.teacherAutoComplete, state.availableTeachers)
                updateAdapter(binding.timeAutoComplete, state.availableTimeSlots)

                binding.buildingAutoComplete.setText(state.selectedBuildingName ?: "", false)
                binding.teacherAutoComplete.setText(state.selectedTeacherName ?: "", false)
                binding.timeAutoComplete.setText(state.selectedTimeSlot ?: "", false)

                // todo: update lesson list

            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun <T> updateAdapter(autoCompleteTextView: AutoCompleteTextView, data: List<T>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, data)
        autoCompleteTextView.setAdapter(adapter)
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
        _binding = null
    }
}