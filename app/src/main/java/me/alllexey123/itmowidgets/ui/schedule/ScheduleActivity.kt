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

    private fun observeUiState() {
        scheduleViewModel.uiState.observe(this) { state ->
            when (state) {
                is ScheduleUiState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    outerRecyclerView.visibility = View.GONE
                }

                is ScheduleUiState.Success -> {
                    val scheduleList = state.schedule
                    val layoutManager = outerRecyclerView.layoutManager as LinearLayoutManager

                    var dateToRestore: LocalDate? = null
                    var offsetToRestore = 0

                    if (!isInitialLoad) {
                        val snapView = snapHelper.findSnapView(layoutManager)
                        val snapPosition = snapView?.let { layoutManager.getPosition(it) }

                        if (snapPosition != null && snapPosition != RecyclerView.NO_POSITION) {
                            dateToRestore = dayScheduleAdapter.getItemAt(snapPosition)?.date
                            offsetToRestore =
                                layoutManager.getDecoratedLeft(snapView) - outerRecyclerView.paddingLeft * 2
                        }
                    }

                    dayScheduleAdapter.updateData(scheduleList)

                    if (dateToRestore != null) {
                        val newIndex = scheduleList.indexOfFirst { it.date == dateToRestore }
                        if (newIndex != -1) {
                            layoutManager.scrollToPositionWithOffset(newIndex, offsetToRestore)
                        }
                    } else {
                        val today = LocalDate.now()
                        val todayIndex = scheduleList.indexOfFirst { it.date == today }
                        if (todayIndex != -1) {
                            layoutManager.scrollToPosition(todayIndex)
                        }
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