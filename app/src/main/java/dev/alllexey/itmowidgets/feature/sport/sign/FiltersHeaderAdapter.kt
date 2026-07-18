package dev.alllexey.itmowidgets.feature.sport.sign

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import dev.alllexey.itmowidgets.databinding.ItemSportFiltersHeaderBinding
import java.time.LocalDate
import kotlin.concurrent.thread

interface FilterActionsListener {
    fun onSportClick()
    fun onShowOnlyAvailableChanged(isChecked: Boolean)
    fun onShowOnlyFriendsChanged(isChecked: Boolean)
    fun onShowAutoSignChanged(isChecked: Boolean)
    fun onBuildingSelected(building: String)
    fun onTeacherSelected(teacher: String)
    fun onTimeSelected(time: String)
    fun onPrevWeekClick()
    fun onNextWeekClick()
    fun onDateSelected(date: LocalDate)
    fun onResetFiltersClick()
}

class FiltersHeaderAdapter(
    private val listener: FilterActionsListener,
    private val hideTeacherSelector: Boolean,
    private val hideTimeSelector: Boolean
) : RecyclerView.Adapter<FiltersHeaderAdapter.HeaderViewHolder>() {

    private var uiState: SportSignUiState.Success = SportSignUiState.Success()
    private val calendarAdapter = SportSignCalendarAdapter { listener.onDateSelected(it) }

    fun updateState(newState: SportSignUiState.Success) {
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
            binding.calendarRecyclerView.adapter = calendarAdapter
            binding.calendarRecyclerView.layoutManager =
                LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
            binding.calendarRecyclerView.doOnLayout { calendar ->
                calendarAdapter.setItemWidth(calendar.width / DAYS_IN_WEEK)
            }

            val animator = binding.calendarRecyclerView.itemAnimator
            if (animator is SimpleItemAnimator) {
                animator.supportsChangeAnimations = false
            }

            if (hideTeacherSelector) {
                binding.teacherInputLayout.visibility = View.GONE
            } else {
                binding.teacherInputLayout.visibility = View.VISIBLE
            }

            if (hideTimeSelector) {
                binding.timeInputLayout.visibility = View.GONE
            } else {
                binding.timeInputLayout.visibility = View.VISIBLE
            }

            binding.availableSportChip.setOnCheckedChangeListener { _, isChecked ->
                listener.onShowOnlyAvailableChanged(isChecked)
            }
            binding.friendsSportChip.setOnCheckedChangeListener { _, isChecked ->
                listener.onShowOnlyFriendsChanged(isChecked)
            }
            binding.autoSignSportChip.setOnCheckedChangeListener { _, isChecked ->
                listener.onShowAutoSignChanged(isChecked)
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
            binding.resetFiltersChip.setOnClickListener { listener.onResetFiltersClick() }
        }

        fun bind(state: SportSignUiState.Success) {
            binding.sportEditText.setText(state.selectedSportNames.joinToString(", ") { it.shorten() }
                .ifEmpty { null })

            updateAdapter(binding.buildingAutoComplete, state.availableBuildings)
            updateAdapter(binding.teacherAutoComplete, state.availableTeachers)
            updateAdapter(binding.timeAutoComplete, state.availableTimeSlots)

            binding.buildingAutoComplete.setText(state.selectedBuildingName ?: "", false)
            binding.teacherAutoComplete.setText(state.selectedTeacherName ?: "", false)
            binding.timeAutoComplete.setText(state.selectedTimeSlot ?: "", false)

            binding.availableSportChip.isChecked = state.showOnlyAvailable
            binding.autoSignSportChip.isChecked = state.showAutoSign
            binding.friendsSportChip.isChecked = state.showOnlyFriends
            binding.resetFiltersChip.isVisible = state.hasActiveFilters

            binding.monthNameTextView.text = state.currentMonthName

            binding.prevWeekButton.isEnabled = state.canGoToPrevWeek
            binding.prevWeekButton.alpha = if (state.canGoToPrevWeek) 1.0f else 0.5f

            binding.nextWeekButton.isEnabled = state.canGoToNextWeek
            binding.nextWeekButton.alpha = if (state.canGoToNextWeek) 1.0f else 0.5f

            calendarAdapter.submitList(state.displayedWeek)
        }

        private fun <T> updateAdapter(autoCompleteTextView: AutoCompleteTextView, data: List<T>) {
            val adapter =
                ArrayAdapter(itemView.context, android.R.layout.simple_spinner_dropdown_item, data)
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

    private companion object {
        const val DAYS_IN_WEEK = 7
    }
}
