package dev.alllexey.itmowidgets.feature.sport.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.CircularProgressBar
import dev.alllexey.itmowidgets.databinding.FragmentSportMyBinding
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportBookingAdapter
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportBookingListener
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportScoreCollapseController
import java.util.Locale

/**
 * Hosts the real My Sport layout, booking adapter and collapse controller with synthetic data, so
 * the scroll-driven header can be exercised without an authenticated session or a network call.
 * Score values are placeholders: this host covers the collapse geometry, not the score binding.
 */
@AndroidEntryPoint
class SportScoreCollapsePreviewActivity : AppCompatActivity(), SportBookingListener {

    lateinit var binding: FragmentSportMyBinding
    lateinit var collapse: SportScoreCollapseController
    private lateinit var adapter: SportBookingAdapter

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply {
            fontScale = appearance.fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (appearance.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            setLocale(Locale.forLanguageTag("ru"))
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode =
            if (appearance.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        appearance.seedColor?.let {
            DynamicColors.applyToActivityIfAvailable(this, DynamicColorsOptions.Builder().setContentBasedSource(it).build())
        }
        binding = FragmentSportMyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SportBookingAdapter(SportCardsPreviewActivity.FixedTime, this)
        binding.mainRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.mainRecyclerView.adapter = adapter
        binding.emptyStateLayout.visibility = android.view.View.GONE
        bindPlaceholderScore()

        collapse = SportScoreCollapseController(
            card = binding.pointsCard,
            content = binding.scoreContent,
            details = binding.scoreDetails,
            detailsContent = binding.scoreDetailsContent,
            scrim = binding.scoreScrim,
            recycler = binding.mainRecyclerView
        ).also { it.attach() }
        if (intent.getBooleanExtra(EXTRA_START_LOADING, false)) showLoading()
    }

    fun showBookings(items: List<SportBooking>) = adapter.submitList(items) { collapse.refresh() }

    /** Replays [dev.alllexey.itmowidgets.feature.sport.ui.my.SportMyFragment]'s loading phase, which
     *  keeps the card out of the layout until the first content arrives. */
    fun showLoading() {
        binding.pointsCard.isVisible = false
        binding.mainRecyclerView.isVisible = false
    }

    fun showContent(items: List<SportBooking>) {
        binding.pointsCard.isVisible = true
        showBookings(items)
        binding.mainRecyclerView.isVisible = items.isNotEmpty()
        binding.emptyStateLayout.isVisible = items.isEmpty()
    }

    override fun onDestroy() {
        collapse.detach()
        super.onDestroy()
    }

    @android.annotation.SuppressLint("SetTextI18n")
    private fun bindPlaceholderScore() {
        binding.progressCircle.progressTextView.text = "120"
        binding.attendancePointsTextView.text = "100"
        binding.bonusPointsTextView.text = "+20"
        binding.progressCircle.circularProgressBar.setSectors(
            listOf(
                CircularProgressBar.Sector(ContextCompat.getColor(this, R.color.sport_score_attendance), 100f),
                CircularProgressBar.Sector(ContextCompat.getColor(this, R.color.sport_score_bonus), 20f)
            )
        )
    }

    override fun onUnSign(booking: SportBooking) = Unit
    override fun onLocationClick(booking: SportBooking) = Unit
    override fun onBookingClick(booking: SportBooking) = Unit

    data class Appearance(val fontScale: Float = 1f, val dark: Boolean = false, val seedColor: Int? = null)
    companion object {
        const val EXTRA_START_LOADING = "start_loading"
        @Volatile var appearance = Appearance()
    }
}
