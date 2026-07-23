package dev.alllexey.itmowidgets.feature.sport.my

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.CircularProgressBar
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentSportMyBinding
import dev.alllexey.itmowidgets.domain.model.sport.SportBooking
import dev.alllexey.itmowidgets.domain.model.sport.SportScore
import dev.alllexey.itmowidgets.feature.sport.common.SportCommonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.sport.common.SportFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class SportMyFragment : Fragment(), SportBookingListener {

    // region Binding

    private var _binding: FragmentSportMyBinding? = null
    private val binding get() = _binding!!

    // endregion

    // region Views

    private val recycler get() = binding.mainRecyclerView
    private val swipe get() = binding.swipeRefreshLayout

    // endregion

    // region State

    private lateinit var adapter: SportBookingAdapter
    private var hasRenderedContent = false
    private var scoreAnimator: ValueAnimator? = null
    private var lastRenderedScore: SportScore? = null
    private var displayedAttendances = 0
    private var displayedBonus = 0
    private var displayedTotal = 0

    private val viewModel: SportMyViewModel by activityViewModels()

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var timeProvider: AcademicTimeProvider

    // endregion

    // region Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSportMyBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        setupUI()
        setupRecycler()
        setupListeners()
        setupObservers()

        viewModel.ensureDataLoaded()
    }

    override fun onDestroyView() {
        scoreAnimator?.cancel()
        scoreAnimator = null
        lastRenderedScore = null
        displayedAttendances = 0
        displayedBonus = 0
        displayedTotal = 0
        super.onDestroyView()
        _binding = null
    }

    // endregion

    // region Setup

    private fun setupUI() {
        val color = requireContext().color
        swipe.setColorSchemeColors(color.primary)
        swipe.setProgressBackgroundColorSchemeColor(color.background)
    }

    private fun setupRecycler() {

        adapter = SportBookingAdapter(timeProvider, this)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
    }

    private fun setupListeners() {
        swipe.setOnRefreshListener {
            viewModel.refreshAllData()
        }
        binding.buttonGoToSchedule.setOnClickListener {
            (parentFragment as? SportFragment)?.changeView(1)
        }
    }

    private fun setupObservers() {
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { state ->
                when (state) {
                    is SportMyUiState.Loading -> showLoading()
                    is SportMyUiState.Success -> onSuccess(state)
                    is SportMyUiState.Error -> showError(state)
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.events.flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { event ->
                when (event) {
                    is SportMyEvent.ShowError -> Toast.makeText(
                        requireContext(),
                        event.error.messageRes(),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    // endregion

    // region UI

    private fun showLoading() {
        swipe.isRefreshing = true
        binding.emptyStateLayout.isVisible = false
        if (!hasRenderedContent) {
            binding.pointsCard.isVisible = false
            recycler.isVisible = false
        }
    }

    private fun onSuccess(state: SportMyUiState.Success) {
        swipe.isRefreshing = false
        hasRenderedContent = true
        binding.pointsCard.isVisible = true
        updateScoreUi(state.score)
        adapter.submitList(state.bookings)
        recycler.isVisible = state.bookings.isNotEmpty()
        binding.emptyStateLayout.isVisible = state.bookings.isEmpty()
        if (state.bookings.isEmpty()) {
            showEmptyState()
        }
        if (state.hasPartialError) {
            Toast.makeText(
                requireContext(),
                R.string.common_partial_load_error,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showError(state: SportMyUiState.Error) {
        swipe.isRefreshing = false
        recycler.isVisible = false
        if (!hasRenderedContent) {
            binding.pointsCard.isVisible = false
        }
        binding.emptyStateLayout.isVisible = true
        binding.stateIcon.setImageResource(R.drawable.ic_error)
        binding.stateTitle.setText(R.string.common_load_error_title)
        binding.stateDescription.setText(state.error.messageRes())
        binding.buttonGoToSchedule.setText(R.string.common_retry)
        binding.buttonGoToSchedule.setOnClickListener { viewModel.refreshAllData() }
    }

    private fun showEmptyState() {
        binding.stateIcon.setImageResource(R.drawable.ic_calendar_add)
        binding.stateTitle.setText(R.string.sport_bookings_empty_title)
        binding.stateDescription.setText(R.string.sport_bookings_empty_description)
        binding.buttonGoToSchedule.setText(R.string.sport_bookings_open_schedule)
        binding.buttonGoToSchedule.setOnClickListener {
            (parentFragment as? SportFragment)?.changeView(1)
        }
    }

    private fun updateScoreUi(score: SportScore) {
        val color = requireContext().color
        val attendanceColor = ContextCompat.getColor(requireContext(), R.color.sport_score_attendance)
        val bonusColor = ContextCompat.getColor(requireContext(), R.color.sport_score_bonus)

        binding.attendanceIndicator.imageTintList = ColorStateList.valueOf(attendanceColor)
        binding.bonusIndicator.imageTintList = ColorStateList.valueOf(bonusColor)

        val need = score.need
        val enough = need == 0
        if (enough) {
            binding.scoreStatusCard.setCardBackgroundColor(color.primaryContainer)
            binding.scoreStatusIcon.setImageResource(R.drawable.ic_check)
            binding.scoreStatusIcon.imageTintList = ColorStateList.valueOf(color.onPrimaryContainer)
            binding.scoreStatusText.setText(R.string.sport_score_passed_status)
            binding.scoreStatusText.setTextColor(color.onPrimaryContainer)
        } else {
            binding.scoreStatusCard.setCardBackgroundColor(color.secondaryContainer)
            binding.scoreStatusIcon.setImageResource(R.drawable.ic_history)
            binding.scoreStatusIcon.imageTintList = ColorStateList.valueOf(color.onSecondaryContainer)
            binding.scoreStatusText.text = getString(R.string.sport_score_remaining_status, need)
            binding.scoreStatusText.setTextColor(color.onSecondaryContainer)
        }

        val total = score.total
        val sectors = mutableListOf<CircularProgressBar.Sector>()
        if (total > 0) {
            val (attPct, bonPct) = if (total > 100) {
                (score.attendances.toFloat() / total) * 100 to (score.otherCapped.toFloat() / total) * 100
            } else {
                score.attendances.toFloat() to score.otherCapped.toFloat()
            }

            sectors += CircularProgressBar.Sector(attendanceColor, attPct)
            sectors += CircularProgressBar.Sector(bonusColor, bonPct)
        }

        val previousScore = lastRenderedScore
        val pointsChanged = previousScore == null ||
            previousScore.attendances != score.attendances ||
            previousScore.other != score.other
        if (pointsChanged) {
            animateScoreValues(score)
            binding.progressCircle.circularProgressBar.animateSectors(
                sectors,
                duration = SCORE_ANIMATION_DURATION
            )
            val statusChanged = previousScore == null ||
                (previousScore.need == 0) != (score.need == 0)
            if (statusChanged) {
                animateStatusChip()
            } else {
                resetStatusChipAnimation()
            }
        } else {
            renderScoreValues(score.attendances, score.otherCapped, total)
            renderFinalBonusValue(score)
            binding.progressCircle.circularProgressBar.setSectors(sectors)
        }
        lastRenderedScore = score
    }

    private fun animateScoreValues(score: SportScore) {
        scoreAnimator?.cancel()
        val startAttendances = displayedAttendances
        val startBonus = displayedBonus
        val startTotal = displayedTotal

        scoreAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SCORE_ANIMATION_DURATION
            interpolator = MOTION_INTERPOLATOR
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val attendances = lerp(startAttendances, score.attendances, fraction)
                val bonus = lerp(startBonus, score.otherCapped, fraction)
                val total = lerp(startTotal, score.total, fraction)
                renderScoreValues(attendances, bonus, total)
                if (fraction >= 1f) renderFinalBonusValue(score)
            }
            start()
        }
    }

    private fun renderScoreValues(attendances: Int, bonus: Int, total: Int) {
        displayedAttendances = attendances
        displayedBonus = bonus
        displayedTotal = total
        binding.attendancePointsTextView.text = attendances.toString()
        binding.bonusPointsTextView.text = getString(R.string.sport_score_bonus_value, bonus)
        binding.progressCircle.progressTextView.text = total.toString()
    }

    private fun renderFinalBonusValue(score: SportScore) {
        binding.bonusPointsTextView.text = if (score.other > score.otherCapped) {
            getString(R.string.sport_score_bonus_over_limit, score.otherCapped, score.other)
        } else {
            getString(R.string.sport_score_bonus_value, score.otherCapped)
        }
    }

    private fun animateStatusChip() {
        binding.scoreStatusCard.animate().cancel()
        binding.scoreStatusCard.alpha = 0f
        binding.scoreStatusCard.scaleX = STATUS_START_SCALE
        binding.scoreStatusCard.scaleY = STATUS_START_SCALE
        binding.scoreStatusCard.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(STATUS_ANIMATION_DELAY)
            .setDuration(STATUS_ANIMATION_DURATION)
            .setInterpolator(MOTION_INTERPOLATOR)
            .start()
    }

    private fun resetStatusChipAnimation() {
        binding.scoreStatusCard.animate().cancel()
        binding.scoreStatusCard.alpha = 1f
        binding.scoreStatusCard.scaleX = 1f
        binding.scoreStatusCard.scaleY = 1f
    }

    private fun lerp(start: Int, end: Int, fraction: Float): Int {
        return (start + (end - start) * fraction).toInt()
    }

    // endregion

    override fun onBookingClick(booking: SportBooking) {
        SportCommonDetailsBottomSheet.newInstance(booking, gson)
            .show(parentFragmentManager, SportCommonDetailsBottomSheet.TAG)
    }

    override fun onUnSign(booking: SportBooking) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("Отменить запись на это занятие?")
            .setNegativeButton("Назад", null)
            .setPositiveButton("Отменить") { _, _ ->
                viewModel.cancelBooking(booking)
            }
            .show()
    }

    override fun onLocationClick(booking: SportBooking) {
        val buildingAddress = booking.extractBuildingAddress() ?: return
        val gmmIntentUri = "geo:0,0?q=${android.net.Uri.encode(buildingAddress)}".toUri()
        val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri)
        try {
            requireContext().startActivity(mapIntent)
        } catch (e: Exception) {
        }
    }

    private companion object {
        val MOTION_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)
        const val SCORE_ANIMATION_DURATION = 700L
        const val STATUS_ANIMATION_DELAY = 380L
        const val STATUS_ANIMATION_DURATION = 260L
        const val STATUS_START_SCALE = 0.84f
    }
}
