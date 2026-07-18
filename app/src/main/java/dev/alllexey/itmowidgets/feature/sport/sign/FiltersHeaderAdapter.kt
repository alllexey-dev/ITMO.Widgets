package dev.alllexey.itmowidgets.feature.sport.sign

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
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
    private val weekPagerAdapter = SportSignWeekPagerAdapter { listener.onDateSelected(it) }

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

        private var boundState = SportSignUiState.Success()
        private var pendingPage = 0

        init {
            binding.calendarWeekPager.adapter = weekPagerAdapter
            binding.calendarWeekPager.isUserInputEnabled = false
            binding.calendarWeekPager.addOnLayoutChangeListener {
                    pager, left, _, right, _, oldLeft, _, oldRight, _ ->
                val width = right - left
                val oldWidth = oldRight - oldLeft
                if (width > 0 && width != oldWidth) {
                    pager.post { weekPagerAdapter.setPageWidth(width) }
                }
            }
            binding.calendarWeekPager.registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        pendingPage = position
                    }

                    override fun onPageScrollStateChanged(state: Int) {
                        if (state != ViewPager2.SCROLL_STATE_IDLE) return
                        if (pendingPage == boundState.selectedWeekIndex) return
                        boundState.calendarWeeks.getOrNull(pendingPage)
                            ?.firstOrNull()
                            ?.date
                            ?.let(listener::onDateSelected)
                    }
                }
            )

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

            binding.prevWeekButton.setOnClickListener {
                val target = (binding.calendarWeekPager.currentItem - 1).coerceAtLeast(0)
                binding.calendarWeekPager.setCurrentItem(target, true)
            }
            binding.nextWeekButton.setOnClickListener {
                val lastPage = (weekPagerAdapter.itemCount - 1).coerceAtLeast(0)
                val target = (binding.calendarWeekPager.currentItem + 1).coerceAtMost(lastPage)
                binding.calendarWeekPager.setCurrentItem(target, true)
            }
            binding.resetFiltersChip.setOnClickListener { listener.onResetFiltersClick() }
        }

        fun bind(state: SportSignUiState.Success) {
            boundState = state
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

            updateMonthName(state.currentMonthName)

            binding.prevWeekButton.isEnabled = state.canGoToPrevWeek
            binding.prevWeekButton.alpha = if (state.canGoToPrevWeek) 1.0f else 0.5f

            binding.nextWeekButton.isEnabled = state.canGoToNextWeek
            binding.nextWeekButton.alpha = if (state.canGoToNextWeek) 1.0f else 0.5f

            weekPagerAdapter.submitWeeks(state.calendarWeeks)
            if (binding.calendarWeekPager.currentItem != state.selectedWeekIndex) {
                binding.calendarWeekPager.setCurrentItem(state.selectedWeekIndex, false)
            }
        }

        private fun updateMonthName(monthName: String) {
            val monthView = binding.monthNameTextView
            if (monthView.text.toString() == monthName) return

            val shouldAnimate = monthView.text.isNotEmpty() && monthView.isLaidOut
            monthView.animate().cancel()
            monthView.text = monthName
            if (!shouldAnimate) {
                monthView.alpha = 1f
                return
            }
            monthView.alpha = MONTH_START_ALPHA
            monthView.animate()
                .alpha(1f)
                .setDuration(MONTH_ANIMATION_DURATION)
                .setInterpolator(MOTION_INTERPOLATOR)
                .start()
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
        const val MONTH_ANIMATION_DURATION = 180L
        const val MONTH_START_ALPHA = 0.45f
        val MOTION_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)
    }
}
