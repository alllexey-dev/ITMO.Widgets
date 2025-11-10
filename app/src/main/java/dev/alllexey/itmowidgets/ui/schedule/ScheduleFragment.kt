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
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import java.time.Duration
import java.time.LocalDate

class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var outerRecyclerView: RecyclerView
    private lateinit var dayScheduleAdapter: DayScheduleAdapter

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateTimeRunnable: Runnable

    private val scheduleViewModel: ScheduleViewModel by viewModels {
        val appContainer = (requireActivity().application as ItmoWidgetsApp).appContainer
        ScheduleViewModelFactory(appContainer.scheduleRepository)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        outerRecyclerView = view.findViewById(R.id.outerRecyclerView)

        setupRecyclerView()
        observeUiState()

        swipeRefreshLayout.setOnRefreshListener {
            scheduleViewModel.fetchScheduleData(forceRefresh = true)
        }

        scheduleViewModel.fetchScheduleData(forceRefresh = false)
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
        outerRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        dayScheduleAdapter = DayScheduleAdapter()
        outerRecyclerView.adapter = dayScheduleAdapter

        outerRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = dayScheduleAdapter.itemCount

                if (totalItemCount > 0) {
                    if (lastVisibleItemPosition > totalItemCount - 3 && dy > 0) {
                        scheduleViewModel.fetchNextDays()
                    }
                }
            }
        })
    }

    private var isInitialLoad = true

    private fun observeUiState() {
        scheduleViewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ScheduleUiState.Loading -> {
                    swipeRefreshLayout.isRefreshing = true
                    outerRecyclerView.visibility = View.VISIBLE
                }

                is ScheduleUiState.Success -> {
                    val scheduleList = state.schedule
                    val layoutManager = outerRecyclerView.layoutManager as LinearLayoutManager

                    if (isInitialLoad && scheduleList.isNotEmpty()) {
                        dayScheduleAdapter.submitList(scheduleList) {
                            val todayIndex = scheduleList.indexOfFirst { it.date == LocalDate.now() }
                            if (todayIndex != -1) {
                                layoutManager.scrollToPosition(todayIndex)
                            }
                        }
                        isInitialLoad = false
                    } else {
                        dayScheduleAdapter.submitList(scheduleList)
                    }

                    swipeRefreshLayout.isRefreshing = state.isStillUpdating
                    outerRecyclerView.visibility = View.VISIBLE
                }

                is ScheduleUiState.Error -> {
                    swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startLessonStateUpdater() {
        updateTimeRunnable = object : Runnable {
            override fun run() {
                dayScheduleAdapter.updateLessonStates()
                handler.postDelayed(this, Duration.ofMinutes(1).toMillis())
            }
        }
        handler.post(updateTimeRunnable)
    }

    private fun stopLessonStateUpdater() {
        handler.removeCallbacks(updateTimeRunnable)
    }
}