package dev.alllexey.itmowidgets.ui.sport.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.myitmo.model.sport.ChosenSportSection
import api.myitmo.model.sport.SportScore
import dev.alllexey.itmowidgets.core.model.BasicSportLessonData
import dev.alllexey.itmowidgets.core.model.QueueEntry
import dev.alllexey.itmowidgets.core.model.QueueEntryStatus
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.data.repository.SportSharedRepository
import dev.alllexey.itmowidgets.util.SportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SportMeUiState(
    val isLoading: Boolean = true,
    val score: SportScore? = null,
    val listItems: List<SportRecordUiModel> = emptyList(),
    val errorMessage: String? = null
)

sealed class RecordType {
    data class Signed(val thoughAutoSign: Boolean) : RecordType()
    data class Queue(val entry: QueueEntry, val isPrediction: Boolean) : RecordType()
}

data class SportRecordUiModel(
    val id: String,
    val lessonId: Long?,
    val title: String,
    val dateTime: OffsetDateTime,
    val timeString: String,
    val location: String,
    val teacher: String,
    val type: RecordType
)

class SportMeViewModel(
    private val sharedRepository: SportSharedRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SportMeUiState())
    val uiState: StateFlow<SportMeUiState> = _uiState.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        observeSharedData()
        loadData()
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sharedRepository.reloadAll()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Ошибка загрузки данных"
                    )
                }
            }
        }
    }

    private fun observeSharedData() {
        viewModelScope.launch {
            sharedRepository.state.collectLatest { shared ->
                if (shared.isLoading) {
                    _uiState.update { it.copy(isLoading = true) }
                    return@collectLatest
                }

                val signedModels = mapSignedLessons(
                    shared.chosenSportSections,
                    shared.freeSignEntries,
                    shared.autoSignEntries
                )

                val freeSignModels = mapFreeSignEntries(shared.freeSignEntries, signedModels)
                val autoSignModels = mapAutoSignEntries(shared.autoSignEntries, signedModels)

                val allItems = (signedModels + freeSignModels + autoSignModels)
                    .filter { it.dateTime.isAfter(OffsetDateTime.now().minusHours(1)) }
                    .sortedBy { it.dateTime }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        score = shared.score,
                        listItems = allItems,
                        errorMessage = null
                    )
                }
            }
        }
    }

    private fun mapSignedLessons(
        sections: List<ChosenSportSection>,
        freeSignEntries: List<SportFreeSignEntry>,
        autoSignEntries: List<SportAutoSignEntry>
    ): List<SportRecordUiModel> {
        return sections.flatMap { section ->
            section.lessonGroups.flatMap { group ->
                group.lessons.map { lesson ->
                    val localStart = lesson.dateStart.atZoneSameInstant(ZoneId.systemDefault())
                    val localEnd = lesson.dateEnd.atZoneSameInstant(ZoneId.systemDefault())

                    SportRecordUiModel(
                        id = "signed_${lesson.dateStart.toEpochSecond()}_${lesson.roomId}",
                        lessonId = lesson.id,
                        title = SportUtils.shortenSectionName(section.sectionName) ?: section.sectionName,
                        dateTime = lesson.dateStart,
                        timeString = "${localStart.format(timeFormatter)} - ${localEnd.format(timeFormatter)}",
                        location = lesson.roomName ?: "Место не указано",
                        teacher = lesson.teacherFio ?: "",
                        type = RecordType.Signed(
                            thoughAutoSign = freeSignEntries.any {
                                it.status == QueueEntryStatus.SATISFIED && it.lessonId == lesson.id
                            } || autoSignEntries.any {
                                it.status == QueueEntryStatus.SATISFIED && it.realLessonId == lesson.id
                            }
                        ),
                    )
                }
            }
        }
    }

    private fun mapFreeSignEntries(
        entries: List<SportFreeSignEntry>,
        chosenModels: List<SportRecordUiModel>
    ): List<SportRecordUiModel> {
        return entries
            .asSequence()
            .filter { it.status != QueueEntryStatus.EXPIRED && it.status != QueueEntryStatus.SATISFIED }
            .filter { !chosenModels.any { c -> c.lessonId == it.lessonId } }
            .groupBy { it.targetLesson.id }
            .map { it.value.maxBy { entry -> entry.createdAt } }
            .map { entry ->
                entry.targetLesson.toUiModel(
                    idPrefix = "free_${entry.id}",
                    type = RecordType.Queue(entry, isPrediction = false),
                    twoWeeksForward = false
                )
            }
            .toList()
    }

    private fun mapAutoSignEntries(
        entries: List<SportAutoSignEntry>,
        chosenModels: List<SportRecordUiModel>
    ): List<SportRecordUiModel> {
        return entries
            .asSequence()
            .filter { it.status != QueueEntryStatus.EXPIRED && it.status != QueueEntryStatus.SATISFIED }
            .filter { !chosenModels.any { c -> c.lessonId == it.realLessonId }}
            .groupBy { it.targetLesson.id }
            .map { it.value.maxBy { entry -> entry.createdAt } }
            .map { entry ->
                entry.targetLesson.toUiModel(
                    idPrefix = "auto_${entry.id}",
                    type = RecordType.Queue(entry, isPrediction = true),
                    twoWeeksForward = true
                )
            }
            .toList()
    }

    private fun BasicSportLessonData.toUiModel(
        idPrefix: String,
        type: RecordType,
        twoWeeksForward: Boolean
    ): SportRecordUiModel {
        val weeksToAdd = if (twoWeeksForward) 2L else 0L
        val localStart = dateStart.atZoneSameInstant(ZoneId.systemDefault()).plusWeeks(weeksToAdd)
        val localEnd = dateEnd.atZoneSameInstant(ZoneId.systemDefault()).plusWeeks(weeksToAdd)

        return SportRecordUiModel(
            id = idPrefix,
            lessonId = id,
            title = SportUtils.shortenSectionName(sectionName) ?: sectionName,
            dateTime = dateStart.plusWeeks(weeksToAdd),
            timeString = "${localStart.format(timeFormatter)} - ${localEnd.format(timeFormatter)}",
            location = roomName,
            teacher = teacherFio,
            type = type
        )
    }
}