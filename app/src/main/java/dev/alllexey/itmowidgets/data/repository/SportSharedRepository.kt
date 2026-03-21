package dev.alllexey.itmowidgets.data.repository

import android.content.Context
import api.myitmo.MyItmoApi
import api.myitmo.model.sport.ChosenSportSection
import api.myitmo.model.sport.SportLesson
import api.myitmo.model.sport.SportScore
import dev.alllexey.itmowidgets.appContainer
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.core.model.SportAutoSignQueue
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.core.model.SportFreeSignQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.supervisorScope
import java.time.LocalDate

data class SportSharedData(
    val isLoading: Boolean = true,
    val revision: Long = 0L,
    val score: SportScore? = null,
    val scheduleLessons: List<SportLesson> = emptyList(),
    val chosenSportSections: List<ChosenSportSection> = emptyList(),
    val freeSignEntries: List<SportFreeSignEntry> = emptyList(),
    val freeSignQueues: List<SportFreeSignQueue> = emptyList(),
    val autoSignEntries: List<SportAutoSignEntry> = emptyList(),
    val autoSignQueues: List<SportAutoSignQueue> = emptyList(),
    val autoSignLimits: SportAutoSignLimits? = null
)

class SportSharedRepository(
    context: Context,
    private val myItmo: MyItmoApi
) {
    private val appContainer = context.applicationContext.appContainer()
    private val widgetsApi by lazy { appContainer.itmoWidgets.api }
    private val settings by lazy { appContainer.storage.settings }

    private val mutex = Mutex()
    private var revisionCounter = 0L

    private val _state = MutableStateFlow(SportSharedData())
    val state: StateFlow<SportSharedData> = _state.asStateFlow()

    suspend fun reloadAll(onlyCustom: Boolean = false) {
        mutex.withLock {
            _state.update { it.copy(isLoading = true) }

            try {
                supervisorScope {
                    val customEnabled = settings.getCustomServicesState()

                    val freeEntriesDeferred = if (customEnabled) async(Dispatchers.IO) {
                        widgetsApi.mySportFreeSignEntries().data.orEmpty()
                    } else null

                    val freeQueuesDeferred = if (customEnabled) async(Dispatchers.IO) {
                        widgetsApi.currentSportFreeSignQueues().data.orEmpty()
                    } else null

                    val autoEntriesDeferred = if (customEnabled) async(Dispatchers.IO) {
                        widgetsApi.mySportAutoSignEntries().data.orEmpty()
                    } else null

                    val autoQueuesDeferred = if (customEnabled) async(Dispatchers.IO) {
                        widgetsApi.currentSportAutoSignQueues().data.orEmpty()
                    } else null

                    val autoLimitsDeferred = if (customEnabled) async(Dispatchers.IO) {
                        widgetsApi.sportAutoSignLimits().data
                    } else null

                    val newState = if (onlyCustom) {
                        SportSharedData(
                            isLoading = false,
                            revision = ++revisionCounter,
                            score = state.value.score,
                            chosenSportSections = state.value.chosenSportSections,
                            scheduleLessons = state.value.scheduleLessons,
                            freeSignEntries = freeEntriesDeferred?.await().orEmpty(),
                            freeSignQueues = freeQueuesDeferred?.await().orEmpty(),
                            autoSignEntries = autoEntriesDeferred?.await().orEmpty(),
                            autoSignQueues = autoQueuesDeferred?.await().orEmpty(),
                            autoSignLimits = autoLimitsDeferred?.await()
                        )
                    } else {

                        val scoreDeferred = async(Dispatchers.IO) {
                            myItmo.getSportScore(null).execute().body()!!.result
                        }
                        val chosenDeferred = async(Dispatchers.IO) {
                            myItmo.chosenSportSections.execute().body()!!.result
                        }

                        val scheduleDeferred = async(Dispatchers.IO) {
                            myItmo.getSportSchedule(
                                LocalDate.now(),
                                LocalDate.now().plusDays(21),
                                null, null, null
                            ).execute().body()!!.result
                        }

                        SportSharedData(
                            isLoading = false,
                            revision = ++revisionCounter,
                            score = scoreDeferred.await(),
                            chosenSportSections = chosenDeferred.await(),
                            scheduleLessons = scheduleDeferred.await()
                                .flatMap { it.lessons ?: emptyList() },
                            freeSignEntries = freeEntriesDeferred?.await().orEmpty(),
                            freeSignQueues = freeQueuesDeferred?.await().orEmpty(),
                            autoSignEntries = autoEntriesDeferred?.await().orEmpty(),
                            autoSignQueues = autoQueuesDeferred?.await().orEmpty(),
                            autoSignLimits = autoLimitsDeferred?.await()
                        )
                    }

                    _state.value = newState
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}