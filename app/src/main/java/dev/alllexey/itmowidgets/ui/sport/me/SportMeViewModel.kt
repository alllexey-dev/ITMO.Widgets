package dev.alllexey.itmowidgets.ui.sport.me

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.myitmo.MyItmoApi
import api.myitmo.model.sport.ChosenSportSection
import api.myitmo.model.sport.SportScore
import dev.alllexey.itmowidgets.appContainer
import dev.alllexey.itmowidgets.core.model.BasicSportLessonData
import dev.alllexey.itmowidgets.core.model.QueueEntryStatus
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.util.SportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
    data class Queue(val position: Int, val total: Int, val isPrediction: Boolean, val entryId: Long) : RecordType()
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
    private val myItmo: MyItmoApi,
    context: Context
) : ViewModel() {

    private val appContainer = context.appContainer()
    private val widgetsApi by lazy { appContainer.itmoWidgets.api() }
    private val settings by lazy { appContainer.storage.settings }

    private val _uiState = MutableStateFlow(SportMeUiState())
    val uiState: StateFlow<SportMeUiState> = _uiState.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                supervisorScope {
                    val scoreDeferred = async { myItmo.getSportScore(null).execute().body()!!.result }
                    val signedDeferred = async { myItmo.chosenSportSections.execute().body()!!.result }

                    val customServicesEnabled = settings.getCustomServicesState()
                    val freeSignDeferred = if (customServicesEnabled) async { widgetsApi.mySportFreeSignEntries() } else null
                    val autoSignDeferred = if (customServicesEnabled) async { widgetsApi.mySportAutoSignEntries() } else null

                    val score = scoreDeferred.await()
                    val signedLessons = signedDeferred.await()
                    val freeSignEntries = freeSignDeferred?.await()?.data ?: emptyList()
                    val autoSignEntries = autoSignDeferred?.await()?.data ?: emptyList()

                    val signedModels = mapSignedLessons(signedLessons, freeSignEntries, autoSignEntries)
                    val freeSignModels = mapFreeSignEntries(freeSignEntries, signedModels)
                    val autoSignModels = mapAutoSignEntries(autoSignEntries, signedModels)

                    val allItems = (signedModels + freeSignModels + autoSignModels)
                        .filter { it.dateTime.isAfter(OffsetDateTime.now().minusHours(1)) }
                        .sortedBy { it.dateTime }

                    _uiState.update {
                        it.copy(
                            score = score,
                            listItems = allItems
                        )
                    }
                }
            } catch (e: Exception) {
                appContainer.errorLogRepository.logThrowable(e, javaClass.name)
                e.printStackTrace()
                _uiState.update { it.copy(errorMessage = "Ошибка загрузки данных") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun mapSignedLessons(sections: List<ChosenSportSection>, freeSignEntries: List<SportFreeSignEntry>, autoSignEntries: List<SportAutoSignEntry>): List<SportRecordUiModel> {
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
                        type = RecordType.Signed(thoughAutoSign = freeSignEntries.filter { it.status == QueueEntryStatus.SATISFIED }
                            .any { it.lessonId == lesson.id } || autoSignEntries.filter { it.status == QueueEntryStatus.SATISFIED }
                            .any { it.realLessonId == lesson.id }),
                    )
                }
            }
        }
    }

    private fun mapFreeSignEntries(entries: List<SportFreeSignEntry>, chosenModels: List<SportRecordUiModel>): List<SportRecordUiModel> {
        return entries
            .filter { it.status == QueueEntryStatus.WAITING }
            .filter { !chosenModels.any { c -> c.lessonId == it.lessonId } }
            .map { entry ->
            entry.lessonData.toUiModel(
                idPrefix = "free_${entry.id}",
                type = RecordType.Queue(entry.position, entry.total, isPrediction = false, entry.id),
                twoWeeksForward = false
            )
        }
    }

    private fun mapAutoSignEntries(entries: List<SportAutoSignEntry>, chosenModels: List<SportRecordUiModel>): List<SportRecordUiModel> {
        return entries
            .filter { it.status == QueueEntryStatus.WAITING }
            .filter { !chosenModels.any { c -> c.lessonId == it.realLessonId } }
            .map { entry ->
            entry.prototypeLessonData.toUiModel(
                idPrefix = "auto_${entry.id}",
                type = RecordType.Queue(entry.position, entry.total, isPrediction = true, entry.id),
                twoWeeksForward = true
            )
        }
    }

    private fun BasicSportLessonData.toUiModel(idPrefix: String, type: RecordType, twoWeeksForward: Boolean): SportRecordUiModel {
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