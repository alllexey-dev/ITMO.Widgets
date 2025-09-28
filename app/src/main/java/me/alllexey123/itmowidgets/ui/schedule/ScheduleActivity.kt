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
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.providers.MyItmoProvider
import me.alllexey123.itmowidgets.providers.ScheduleProvider
import java.time.LocalDate

class ScheduleActivity : AppCompatActivity() {
    private lateinit var outerRecyclerView: RecyclerView
    private lateinit var dayScheduleAdapter: DayScheduleAdapter

    private val scheduleViewModel: ScheduleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        val cachedTodaySchedule = ScheduleProvider.readSchedule(
            LocalDate.now(), ScheduleProvider.cacheDir(applicationContext),
            MyItmoProvider.getMyItmo(applicationContext).gson
        )?.first

        outerRecyclerView = findViewById(R.id.outerRecyclerView)
        outerRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        dayScheduleAdapter = DayScheduleAdapter(
            if (cachedTodaySchedule != null) listOf(
                cachedTodaySchedule
            ) else listOf()
        )
        outerRecyclerView.adapter = dayScheduleAdapter

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(outerRecyclerView)
        val progressBar = findViewById<ProgressBar>(R.id.loadingProgressBar)

        scheduleViewModel.fetchScheduleData(applicationContext)
        scheduleViewModel.scheduleData.observe(this) { scheduleList ->
            val today = LocalDate.now()
            val todayIndex = scheduleList.indexOfFirst { it.date == today }

            if (todayIndex != -1) {
                outerRecyclerView.post {
                    val layoutManager = outerRecyclerView.layoutManager as LinearLayoutManager
                    layoutManager.scrollToPosition(todayIndex)
                }
            }

            progressBar.visibility = View.GONE
            outerRecyclerView.visibility = View.VISIBLE
            dayScheduleAdapter.updateData(scheduleList)
        }

        scheduleViewModel.error.observe(this) { errorMessage ->
            progressBar.visibility = View.GONE
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

}