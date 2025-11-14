package dev.alllexey.itmowidgets.ui.sport.me

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import api.myitmo.model.sport.ChosenSportSection
import api.myitmo.model.sport.SportScore
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.FragmentSportMeBinding
import dev.alllexey.itmowidgets.ui.misc.CircularProgressBar
import dev.alllexey.itmowidgets.util.SportUtils
import dev.alllexey.itmowidgets.util.withSaturation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class SportMeFragment : Fragment() {

    private var _binding: FragmentSportMeBinding? = null
    private val binding get() = _binding!!

    private lateinit var sportRecordAdapter: SportRecordAdapter

    private val myItmo by lazy { (requireContext().applicationContext as ItmoWidgetsApp).appContainer.myItmo }

    private val colorUtil by lazy { (requireContext().applicationContext as ItmoWidgetsApp).appContainer.colorUtil }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSportMeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeToRefresh()

        val progressCircleView = binding.progressCircle
        val progressBar = progressCircleView.circularProgressBar
        progressBar.animateSectors(listOf(), duration = 0L, startDelay = 0L)

        loadData()
    }

    private fun setupRecyclerView() {
        sportRecordAdapter = SportRecordAdapter()
        binding.sportRecordsRecyclerView.apply {
            adapter = sportRecordAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadData()
        }
    }

    private fun loadData() {
        binding.swipeRefreshLayout.isRefreshing = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val chosenDeferred = async(Dispatchers.IO) {
                    myItmo.api().chosenSportSections.execute().body()!!.result
                }
                val scoreDeferred = async(Dispatchers.IO) {
                    myItmo.api().getSportScore(null).execute().body()!!.result
                }

                val chosen = chosenDeferred.await()
                val score = scoreDeferred.await()

                updateUi(score, chosen)

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
                binding.sportRecordsRecyclerView.visibility = View.GONE
                binding.emptyStateTextView.visibility = View.VISIBLE
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun updateUi(score: SportScore, chosenSections: List<ChosenSportSection>) {
        val attendanceColor = colorUtil.getTertiaryColor(
            colorUtil.getColor(R.color.red_sport_color)
        ).withSaturation(4f)
        val bonusColor = colorUtil.getSecondaryColor(
            colorUtil.getColor(R.color.blue_sport_color)
        ).withSaturation(4f)

        val attendancePoints = score.sum.attendances
        val realBonusPoints = score.sum.other
        val bonusPoints = min(realBonusPoints, 40)
        val totalPoints = attendancePoints + bonusPoints

        binding.attendancePointsTextView.text = attendancePoints.toString()
        binding.bonusPointsTextView.text =
            if (realBonusPoints > bonusPoints) "$bonusPoints ($realBonusPoints)" else "$bonusPoints"
        binding.attendanceIndicator.imageTintList = ColorStateList.valueOf(attendanceColor)
        binding.bonusIndicator.imageTintList = ColorStateList.valueOf(bonusColor)

        val need = max(100 - totalPoints, 0)
        val enough = need == 0L
        binding.needPointsTextView.text = if (enough) "Зачёт" else "$need"
        binding.needLabelTextView.text = if (enough) "" else "до зачёта"

        val progressCircleView = binding.progressCircle
        val progressBar = progressCircleView.circularProgressBar
        val progressTextView = progressCircleView.progressTextView

        progressTextView.text = totalPoints.toString()

        val sectors = mutableListOf<CircularProgressBar.Sector>()
        if (totalPoints > 0) {
            val (attendancePercentage, bonusPercentage) = if (totalPoints > 100) {
                (attendancePoints.toFloat() / totalPoints) * 100 to (bonusPoints.toFloat() / totalPoints) * 100
            } else {
                attendancePoints.toFloat() to bonusPoints.toFloat()
            }

            if (attendancePercentage > 0) sectors.add(
                CircularProgressBar.Sector(
                    attendanceColor,
                    attendancePercentage
                )
            )
            if (bonusPercentage > 0) sectors.add(
                CircularProgressBar.Sector(
                    bonusColor,
                    bonusPercentage
                )
            )
        }

        progressBar.animateSectors(sectors, duration = 800L, startDelay = 300L)

        val records = mapChosenSectionsToSportRecords(chosenSections)
        if (records.isEmpty()) {
            binding.sportRecordsRecyclerView.visibility = View.GONE
            binding.emptyStateTextView.visibility = View.VISIBLE
        } else {
            binding.sportRecordsRecyclerView.visibility = View.VISIBLE
            binding.emptyStateTextView.visibility = View.GONE
            sportRecordAdapter.submitList(records)
        }
    }

    private fun mapChosenSectionsToSportRecords(sections: List<ChosenSportSection>): List<SportRecord> {
        val formatter = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())

        val records = sections.flatMap { it.lessonGroups.map { lg -> lg to it.sectionName } }
            .flatMap { it.first.lessons.map { l -> l to it.second } }
            .sortedBy { it.first.dateEnd }
            .mapNotNull { (lesson, sectionName) ->
                if (lesson.dateEnd < OffsetDateTime.now()) return@mapNotNull null
                val dateTimeString =
                    "${lesson.dateStart.format(formatter)}, ${lesson.timeStart} - ${lesson.timeEnd}"

                SportRecord(
                    title = SportUtils.shortenSectionName(sectionName)!!,
                    dateTime = dateTimeString,
                    location = lesson.roomName ?: "Место не указано",
                    teacher = lesson.teacherFio ?: "Преподаватель не указан"
                )
            }

        return records
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class SportRecord(
    val title: String,
    val dateTime: String,
    val location: String,
    val teacher: String
)