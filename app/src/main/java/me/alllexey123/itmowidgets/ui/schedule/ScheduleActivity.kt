package me.alllexey123.itmowidgets.ui.schedule

import android.os.Bundle
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

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(outerRecyclerView)
    }

    private fun observeUiState() {
        scheduleViewModel.uiState.observe(this) { state ->
            when (state) {
                is ScheduleUiState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    outerRecyclerView.visibility = View.GONE
                }
                is ScheduleUiState.Success -> {
                    progressBar.visibility = View.GONE
                    outerRecyclerView.visibility = View.VISIBLE

                    val scheduleList = state.schedule
                    dayScheduleAdapter.updateData(scheduleList)

                    val today = LocalDate.now()
                    val todayIndex = scheduleList.indexOfFirst { it.date == today }
                    if (todayIndex != -1) {
                        outerRecyclerView.post {
                            val layoutManager = outerRecyclerView.layoutManager as LinearLayoutManager
                            layoutManager.scrollToPosition(todayIndex)
                        }
                    }
                }
                is ScheduleUiState.Error -> {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

}