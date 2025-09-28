package me.alllexey123.itmowidgets.ui.schedule

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import me.alllexey123.itmowidgets.ItmoWidgetsApp
import me.alllexey123.itmowidgets.R
import java.time.LocalDate

class ScheduleActivity : AppCompatActivity() {
    private lateinit var outerRecyclerView: RecyclerView
    private lateinit var dayScheduleAdapter: DayScheduleAdapter
    private lateinit var progressBar: ProgressBar

    private val snapHelper = PagerSnapHelper()

    private val scheduleViewModel: ScheduleViewModel by viewModels {
        val appContainer = (application as ItmoWidgetsApp).appContainer
        ScheduleViewModelFactory(appContainer.scheduleRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        setupRecyclerView()
        progressBar = findViewById(R.id.loadingProgressBar)

        observeUiState()

        scheduleViewModel.fetchScheduleData()
    }

    private fun setupRecyclerView() {
        outerRecyclerView = findViewById(R.id.outerRecyclerView)
        outerRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        dayScheduleAdapter = DayScheduleAdapter(listOf())
        outerRecyclerView.adapter = dayScheduleAdapter

        snapHelper.attachToRecyclerView(outerRecyclerView)
    }

    private var isInitialLoad = true

    private var layoutManagerState: Parcelable? = null

    private fun observeUiState() {
        scheduleViewModel.uiState.observe(this) { state ->
            when (state) {
                is ScheduleUiState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    outerRecyclerView.visibility = View.GONE
                }

                is ScheduleUiState.Success -> {
                    val scheduleList = state.schedule
                    if (scheduleList.isEmpty()) {
                        outerRecyclerView.visibility = View.GONE
                        progressBar.visibility = View.GONE
                        return@observe
                    }

                    val layoutManager = outerRecyclerView.layoutManager as LinearLayoutManager
                    if (!isInitialLoad) {
                        layoutManagerState = layoutManager.onSaveInstanceState()
                    }

                    dayScheduleAdapter.updateData(scheduleList)

                    if (isInitialLoad) {
                        val today = LocalDate.now()
                        val todayIndex = scheduleList.indexOfFirst { it.date == today }
                        if (todayIndex != -1) {
                            layoutManager.scrollToPositionWithOffset(todayIndex, 0)
                        }
                        isInitialLoad = false
                    } else if (layoutManagerState != null) {
                        layoutManager.onRestoreInstanceState(layoutManagerState)
                    }

                    if (scheduleList.isNotEmpty()) {
                        isInitialLoad = false
                    }

                    progressBar.visibility = if (state.isCached) View.VISIBLE else View.GONE
                    outerRecyclerView.visibility = View.VISIBLE
                }

                is ScheduleUiState.Error -> {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

}