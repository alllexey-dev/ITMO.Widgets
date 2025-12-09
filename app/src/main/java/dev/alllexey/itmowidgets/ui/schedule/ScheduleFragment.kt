package dev.alllexey.itmowidgets.ui.schedule

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.util.getColorFromAttr
import java.time.Duration
import java.time.LocalDate

class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var outerRecyclerView: RecyclerView
    private lateinit var dayScheduleAdapter: DayScheduleAdapter

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateTimeRunnable: Runnable

    private val scheduleViewModel: ScheduleViewModel by viewModels {
        ScheduleViewModelFactory(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        outerRecyclerView = view.findViewById(R.id.outerRecyclerView)

        val colorPrimary = requireContext().getColorFromAttr(android.R.attr.colorPrimary)
        swipeRefreshLayout.setColorSchemeColors(colorPrimary)

        val colorBackground = requireContext().getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh)
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(colorBackground)

        setupRecyclerView()
        observeUiState()

        swipeRefreshLayout.setOnRefreshListener {
            scheduleViewModel.fetchScheduleData()
        }

        if (savedInstanceState == null) {
            scheduleViewModel.fetchScheduleData()
        }
    }

    override fun onResume() {
        super.onResume()
        startLessonStateUpdater()
    }

    override fun onPause() {
        super.onPause()
        stopLessonStateUpdater()
    }

    private fun setupRecyclerView() {
        outerRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        dayScheduleAdapter = DayScheduleAdapter()
        outerRecyclerView.adapter = dayScheduleAdapter

        outerRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val total = dayScheduleAdapter.itemCount
                if (total > 0 && lastVisible > total - 3) {
                    scheduleViewModel.fetchNextDays()
                }
            }
        })
    }

    private fun observeUiState() {
        scheduleViewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ScheduleUiState.Loading -> {
                    if (dayScheduleAdapter.itemCount == 0) swipeRefreshLayout.isRefreshing = true
                }
                is ScheduleUiState.Success -> {
                    swipeRefreshLayout.isRefreshing = state.isStillUpdating
                    dayScheduleAdapter.submitList(state.schedule) {
                        if (dayScheduleAdapter.itemCount > 0 && state.schedule.isNotEmpty()) {
                            scrollToToday(state.schedule)
                        }
                    }
                }
                is ScheduleUiState.Error -> {
                    swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var hasScrolledToToday = false

    private fun scrollToToday(schedule: List<api.myitmo.model.schedule.Schedule>) {
        if (hasScrolledToToday) return

        val today = LocalDate.now()
        val index = schedule.indexOfFirst { it.date >= today }
        if (index != -1) {
            (outerRecyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 20)
            hasScrolledToToday = true
        }
    }

    private fun startLessonStateUpdater() {
        updateTimeRunnable = object : Runnable {
            override fun run() {
                dayScheduleAdapter.updateLessonStates()
                handler.postDelayed(this, 60_000)
            }
        }
        handler.post(updateTimeRunnable)
    }

    private fun stopLessonStateUpdater() {
        if(::updateTimeRunnable.isInitialized) {
            handler.removeCallbacks(updateTimeRunnable)
        }
    }
}