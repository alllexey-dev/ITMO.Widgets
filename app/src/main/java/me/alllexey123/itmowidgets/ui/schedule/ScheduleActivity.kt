package me.alllexey123.itmowidgets.ui.schedule

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import me.alllexey123.itmowidgets.ItmoWidgetsApp
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.ui.qr.QrCodeActivity
import me.alllexey123.itmowidgets.ui.settings.SettingsActivity
import java.time.Duration
import java.time.LocalDate

class ScheduleActivity : AppCompatActivity() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var outerRecyclerView: RecyclerView
    private lateinit var dayScheduleAdapter: DayScheduleAdapter

    private lateinit var fabSettings: FloatingActionButton

    private lateinit var fabQr: FloatingActionButton

    private val snapHelper = PagerSnapHelper()

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateTimeRunnable: Runnable

    private val scheduleViewModel: ScheduleViewModel by viewModels {
        val appContainer = (application as ItmoWidgetsApp).appContainer
        ScheduleViewModelFactory(appContainer.scheduleRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        setupRecyclerView()
        setupButtons()
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)

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

    private fun setupButtons() {
        fabSettings = findViewById(R.id.fab_settings)
        fabSettings.setOnClickListener { view ->
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        fabQr = findViewById(R.id.fab_qr)
        fabQr.setOnClickListener { view ->
            val intent = Intent(this, QrCodeActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        outerRecyclerView = findViewById(R.id.outerRecyclerView)
        outerRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        dayScheduleAdapter = DayScheduleAdapter()
        outerRecyclerView.adapter = dayScheduleAdapter

        snapHelper.attachToRecyclerView(outerRecyclerView)

        outerRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = dayScheduleAdapter.itemCount

                if (totalItemCount > 0) {
                    if (firstVisibleItemPosition < 2) {
                        scheduleViewModel.fetchPreviousDays()
                    }
                    if (lastVisibleItemPosition > totalItemCount - 3) {
                        scheduleViewModel.fetchNextDays()
                    }
                }
            }
        })
    }

    private var isInitialLoad = true

    private fun observeUiState() {
        scheduleViewModel.uiState.observe(this) { state ->
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
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
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

    companion object {
        fun getOnClickPendingIntent(context: Context): PendingIntent? {
            val clickIntent = Intent(context, ScheduleActivity::class.java)
            return PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

}