package dev.alllexey.itmowidgets.feature.sport.my

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import dev.alllexey.itmowidgets.core.ui.CircularProgressBar
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
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
            Toast.makeText(requireContext(), "Не удалось загрузить некоторые данные", Toast.LENGTH_SHORT).show()
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
        binding.stateDescription.text = state.message
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

        binding.attendancePointsTextView.text = score.attendances.toString()
        binding.bonusPointsTextView.text = if (score.other > score.otherCapped) {
            getString(R.string.sport_score_bonus_over_limit, score.otherCapped, score.other)
        } else {
            getString(R.string.sport_score_bonus_value, score.otherCapped)
        }
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
        binding.progressCircle.progressTextView.text = total.toString()

        val sectors = mutableListOf<CircularProgressBar.Sector>()
        if (total > 0) {
            val (attPct, bonPct) = if (total > 100) {
                (score.attendances.toFloat() / total) * 100 to (score.otherCapped.toFloat() / total) * 100
            } else {
                score.attendances.toFloat() to score.otherCapped.toFloat()
            }

            if (attPct > 0) sectors.add(CircularProgressBar.Sector(attendanceColor, attPct))
            if (bonPct > 0) sectors.add(CircularProgressBar.Sector(bonusColor, bonPct))
        }
        binding.progressCircle.circularProgressBar.animateSectors(sectors, duration = 800L, startDelay = 300L)
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


}
