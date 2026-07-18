package dev.alllexey.itmowidgets.feature.sport.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.myitmo.MyItmo
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.core.network.WidgetsClient
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.core.util.throwableOrNull
import dev.alllexey.itmowidgets.domain.model.sport.SportAttempts
import dev.alllexey.itmowidgets.domain.model.sport.SportBooking
import dev.alllexey.itmowidgets.domain.model.sport.SportScore
import dev.alllexey.itmowidgets.domain.repository.ScheduleRepository
import dev.alllexey.itmowidgets.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.domain.repository.SportScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SportMyUiState {

    object Loading : SportMyUiState()

    data class Success(
        val attempts: SportAttempts,
        val score: SportScore,
        val bookings: List<SportBooking>,
        val hasPartialError: Boolean = false
    ) : SportMyUiState()

    data class Error(val message: String) : SportMyUiState()
}

@HiltViewModel
class SportMyViewModel @Inject constructor(
    private val myItmo: MyItmo,
    private val widgets: WidgetsClient,
    private val scheduleRepository: ScheduleRepository,
    private val sportScheduleRepository: SportScheduleRepository,
    private val sportBookingRepository: SportBookingRepository,
    private val sportDataRepository: SportDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SportMyUiState>(SportMyUiState.Loading)
    val uiState: StateFlow<SportMyUiState> = _uiState.asStateFlow()

    private val isRefreshing = MutableStateFlow(false)

    var observeJob: Job? = null

    init {
        observeData()
    }

    fun ensureDataLoaded() {
        if (_uiState.value is SportMyUiState.Loading && !isRefreshing.value) {
            refreshAllData()
        }
    }

    fun refreshAllData() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                awaitAll(
                    async { sportDataRepository.refreshSportAttempts() },
                    async { sportDataRepository.refreshSportScore() },
                    async { sportBookingRepository.refreshSportBookings() },
                    async { sportDataRepository.refreshSportAutoSignLimits() },
                    async { sportDataRepository.refreshSportQueueEntries() },
                    async { sportDataRepository.refreshFriendsBookings() }
                )
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun refreshMyItmoData() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                awaitAll(
                    async { sportDataRepository.refreshSportAttempts() },
                    async { sportDataRepository.refreshSportScore() },
                    async { sportBookingRepository.refreshSportBookings() }
                )
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun refreshCustomData() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                awaitAll(
                    async { sportDataRepository.refreshSportAutoSignLimits() },
                    async { sportDataRepository.refreshSportQueueEntries() },
                    async { sportDataRepository.refreshFriendsBookings() }
                )
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun cancelBooking(booking: SportBooking) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (booking.signed) {
                    val date = booking.start.toLocalDate()
                    myItmo.api.signOutLessons(listOf(booking.lessonId)).execute()
                    refreshMyItmoData()
                    scheduleRepository.refreshSchedule(null, date, date)
                    sportScheduleRepository.refreshSportSchedule()
                } else when (val entry = booking.signEntry) {
                    is SportFreeSignEntry -> {
                        widgets.api.cancelSportFreeSignEntry(entry.id)
                        refreshCustomData()
                    }

                    is SportAutoSignEntry -> {
                        widgets.api.cancelSportAutoSignEntry(entry.id)
                        refreshCustomData()
                    }

                    else -> refreshAllData()
                }
            } catch (e: Exception) {
                _uiState.value = SportMyUiState.Error(e.message ?: "Не удалось отменить запись")
            }
        }
    }

    private fun observeData() {
        observeJob?.cancel()

        observeJob = viewModelScope.launch {
            combine(
                sportDataRepository.observeSportAttempts(),
                sportDataRepository.observeSportScore(),
                sportBookingRepository.observeSportBookings(),
                isRefreshing
            ) { attemptsState, scoreState, bookingsState, refreshing ->

                if (refreshing) {
                    return@combine SportMyUiState.Loading
                }

                val attempts = attemptsState.dataOrNull()
                val score = scoreState.dataOrNull()
                val bookings = bookingsState.dataOrNull()

                val errors = listOfNotNull(
                    attemptsState.throwableOrNull(),
                    scoreState.throwableOrNull(),
                    bookingsState.throwableOrNull()
                )

                errors.forEach { it.printStackTrace() }

                when {
                    attempts == null || score == null || bookings == null -> {
                        if (errors.isNotEmpty()) {
                            SportMyUiState.Error("Не удалось загрузить данные")
                        } else {
                            SportMyUiState.Loading
                        }
                    }

                    else -> {
                        SportMyUiState.Success(
                            attempts = attempts,
                            score = score,
                            bookings = bookings,
                            hasPartialError = errors.isNotEmpty()
                        )
                    }
                }
            }
                .collect {
                    _uiState.value = it
                }
        }
    }
}
