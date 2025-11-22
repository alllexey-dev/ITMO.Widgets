package dev.alllexey.itmowidgets.ui.sport.me

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import api.myitmo.model.sport.SportScore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.SportAutoSignRequest
import dev.alllexey.itmowidgets.databinding.FragmentSportMeBinding
import dev.alllexey.itmowidgets.ui.misc.CircularProgressBar
import dev.alllexey.itmowidgets.ui.sport.SportFragment
import dev.alllexey.itmowidgets.util.withSaturation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class SportMeFragment : Fragment(R.layout.fragment_sport_me), SportRecordListener {

    private var _binding: FragmentSportMeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SportMeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val myItmo = (requireActivity().application as ItmoWidgetsApp).appContainer.myItmo
                return SportMeViewModel(myItmo.api(), requireContext()) as T
            }
        }
    }

    private lateinit var sportRecordAdapter: SportRecordAdapter
    private val colorUtil by lazy { (requireContext().applicationContext as ItmoWidgetsApp).appContainer.colorUtil }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSportMeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadData()
        }

        binding.btnGoToSchedule.setOnClickListener {
            (parentFragment as SportFragment).changeView(2)
        }

        binding.progressCircle.circularProgressBar.animateSectors(listOf(), duration = 0L, startDelay = 0L)

        observeViewModel()
    }

    private fun setupRecyclerView() {
        sportRecordAdapter = SportRecordAdapter(this)
        binding.sportRecordsRecyclerView.apply {
            adapter = sportRecordAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun observeViewModel() {
        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { state ->
                binding.swipeRefreshLayout.isRefreshing = state.isLoading

                if (state.errorMessage != null && !state.isLoading) {
                    Toast.makeText(context, state.errorMessage, Toast.LENGTH_SHORT).show()
                }

                if (state.score != null) {
                    updateScoreUi(state.score)
                }

                if (state.listItems.isEmpty() && !state.isLoading) {
                    binding.sportRecordsRecyclerView.visibility = View.GONE
                    binding.emptyStateLayout.visibility = View.VISIBLE
                } else {
                    binding.sportRecordsRecyclerView.visibility = View.VISIBLE
                    binding.emptyStateLayout.visibility = View.GONE
                    sportRecordAdapter.submitList(state.listItems)
                }

            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onUnSignClick(model: SportRecordUiModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("Отменить запись на это занятие?")
            .setNegativeButton("Назад", null)
            .setPositiveButton("Отменить") { _, _ ->
                val appContainer = (requireContext().applicationContext as ItmoWidgetsApp).appContainer
                CoroutineScope(Dispatchers.IO).launch {
                    when (model.type) {
                        is RecordType.Signed -> appContainer.myItmo.api().signOutLessons(listOf(model.lessonId)).execute()
                        is RecordType.Queue -> {
                            if (model.type.isPrediction) {
                                appContainer.itmoWidgets.api().deleteSportAutoSignEntry(model.type.entryId)
                            } else {
                                appContainer.itmoWidgets.api().deleteSportFreeSignEntry(model.type.entryId)
                            }
                        }
                    }

                    delay(200)
                    viewModel.loadData()
                }
            }
            .show()
    }

    private fun updateScoreUi(score: SportScore) {
        val attendanceColor = colorUtil.getTertiaryColor(colorUtil.getColor(R.color.red_sport_color)).withSaturation(4f)
        val bonusColor = colorUtil.getSecondaryColor(colorUtil.getColor(R.color.blue_sport_color)).withSaturation(4f)

        val attendancePoints = score.sum.attendances
        val realBonusPoints = score.sum.other
        val bonusPoints = min(realBonusPoints, 40)
        val totalPoints = attendancePoints + bonusPoints

        binding.attendancePointsTextView.text = attendancePoints.toString()
        binding.bonusPointsTextView.text = if (realBonusPoints > bonusPoints) "$bonusPoints ($realBonusPoints)" else "$bonusPoints"
        binding.attendanceIndicator.imageTintList = ColorStateList.valueOf(attendanceColor)
        binding.bonusIndicator.imageTintList = ColorStateList.valueOf(bonusColor)

        val need = max(100 - totalPoints, 0)
        val enough = need == 0L
        binding.needPointsTextView.text = if (enough) "Зачёт" else "$need"
        binding.needLabelTextView.text = if (enough) "" else "до зачёта"

        binding.progressCircle.progressTextView.text = totalPoints.toString()

        val sectors = mutableListOf<CircularProgressBar.Sector>()
        if (totalPoints > 0) {
            val (attPct, bonPct) = if (totalPoints > 100) {
                (attendancePoints.toFloat() / totalPoints) * 100 to (bonusPoints.toFloat() / totalPoints) * 100
            } else {
                attendancePoints.toFloat() to bonusPoints.toFloat()
            }

            if (attPct > 0) sectors.add(CircularProgressBar.Sector(attendanceColor, attPct))
            if (bonPct > 0) sectors.add(CircularProgressBar.Sector(bonusColor, bonPct))
        }
        binding.progressCircle.circularProgressBar.animateSectors(sectors, duration = 800L, startDelay = 300L)
    }

    override fun onDestroyView() {
        binding.sportRecordsRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}