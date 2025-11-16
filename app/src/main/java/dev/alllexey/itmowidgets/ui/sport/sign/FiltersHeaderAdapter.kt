package dev.alllexey.itmowidgets.ui.sport.sign

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.databinding.ItemSportFiltersHeaderBinding
import java.time.LocalDate
import kotlin.concurrent.thread

interface FilterActionsListener {
    fun onSportClick()
    fun onFreeAttendanceChanged(isChecked: Boolean)
    fun onShowOnlyAvailableChanged(isChecked: Boolean)
    fun onBuildingSelected(building: String)
    fun onTeacherSelected(teacher: String)
    fun onTimeSelected(time: String)
    fun onPrevWeekClick()
    fun onNextWeekClick()
    fun onDateSelected(date: LocalDate)
}

class FiltersHeaderAdapter(
    private val listener: FilterActionsListener
) : RecyclerView.Adapter<FiltersHeaderAdapter.HeaderViewHolder>() {

    private var uiState: SportSignUiState = SportSignUiState()
    private val calendarAdapter = CalendarAdapter { listener.onDateSelected(it) }

    fun updateState(newState: SportSignUiState) {
        this.uiState = newState
        notifyItemChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val binding = ItemSportFiltersHeaderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HeaderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind(uiState)
    }

    override fun getItemCount(): Int = 1

    inner class HeaderViewHolder(private val binding: ItemSportFiltersHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            val appContainer = (binding.root.context.applicationContext as ItmoWidgetsApp).appContainer
            binding.calendarRecyclerView.adapter = calendarAdapter
            binding.calendarRecyclerView.layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)

            val animator = binding.calendarRecyclerView.itemAnimator
            if (animator is SimpleItemAnimator) {
                animator.supportsChangeAnimations = false
            }

            if (appContainer.storage.settings.getSportSignHideTeacherSelectorState()) {
                binding.teacherInputLayout.visibility = View.GONE
            } else {
                binding.teacherInputLayout.visibility = View.VISIBLE
            }

            if (appContainer.storage.settings.getSportSignHideTimeSelectorState()) {
                binding.timeInputLayout.visibility = View.GONE
            } else {
                binding.timeInputLayout.visibility = View.VISIBLE
            }

            binding.freeSportSwitch.setOnCheckedChangeListener { _, isChecked ->
                listener.onFreeAttendanceChanged(isChecked)
            }
            binding.availableSportSwitch.setOnCheckedChangeListener { _, isChecked ->
                listener.onShowOnlyAvailableChanged(isChecked)
            }

            binding.sportEditText.setOnClickListener { listener.onSportClick() }
            binding.buildingAutoComplete.setOnItemClickListener { parent, _, position, _ ->
                listener.onBuildingSelected(parent.adapter.getItem(position) as String)
            }
            binding.buildingAutoComplete.setupDismissWorkaround()
            binding.teacherAutoComplete.setOnItemClickListener { parent, _, position, _ ->
                listener.onTeacherSelected(parent.adapter.getItem(position) as String)
            }
            binding.teacherAutoComplete.setupDismissWorkaround()
            binding.timeAutoComplete.setOnItemClickListener { parent, _, position, _ ->
                listener.onTimeSelected(parent.adapter.getItem(position) as String)
            }
            binding.timeAutoComplete.setupDismissWorkaround()

            binding.prevWeekButton.setOnClickListener { listener.onPrevWeekClick() }
            binding.nextWeekButton.setOnClickListener { listener.onNextWeekClick() }
        }

        fun bind(state: SportSignUiState) {
            binding.sportEditText.setText(state.selectedSportNames.joinToString(", ").ifEmpty { null })

            updateAdapter(binding.buildingAutoComplete, state.availableBuildings)
            updateAdapter(binding.teacherAutoComplete, state.availableTeachers)
            updateAdapter(binding.timeAutoComplete, state.availableTimeSlots)

            binding.buildingAutoComplete.setText(state.selectedBuildingName ?: "", false)
            binding.teacherAutoComplete.setText(state.selectedTeacherName ?: "", false)
            binding.timeAutoComplete.setText(state.selectedTimeSlot ?: "", false)

            binding.monthNameTextView.text = state.currentMonthName
            calendarAdapter.submitList(state.displayedWeek)

            binding.prevWeekButton.isEnabled = state.canGoToPrevWeek
            binding.prevWeekButton.alpha = if (state.canGoToPrevWeek) 1.0f else 0.5f

            binding.nextWeekButton.isEnabled = state.canGoToNextWeek
            binding.nextWeekButton.alpha = if (state.canGoToNextWeek) 1.0f else 0.5f
        }

        private fun <T> updateAdapter(autoCompleteTextView: AutoCompleteTextView, data: List<T>) {
            val adapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_dropdown_item, data)
            autoCompleteTextView.setAdapter(adapter)
        }

        private fun AutoCompleteTextView.setupDismissWorkaround() {
            setOnDismissListener {
                dismissDropDown()
                thread {
                    Thread.sleep(50)
                    post { clearFocus() }
                }
            }
        }
    }
}