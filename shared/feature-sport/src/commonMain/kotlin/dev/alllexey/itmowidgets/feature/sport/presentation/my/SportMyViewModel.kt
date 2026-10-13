package dev.alllexey.itmowidgets.feature.sport.presentation.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.errorOrNull
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonSheetPresenter
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonSheetUi
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.sportDayLabel
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toLessonUi
import dev.alllexey.itmowidgets.feature.sport.presentation.common.waiting
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.toLocalDateTime

/**
 * «Моё» on both platforms over [SportBookingsHolder], which Сегодня and the schedule share: the score, the bookings and
 * queues that are still ahead, and the cancellation a lesson sheet asked for, confirmed first.
 */
class SportMyViewModel(
    sportBookingRepository: SportBookingRepository,
    sportDataRepository: SportDataRepository,
    preferences: SportSignPreferencesRepository,
    private val holder: SportBookingsHolder,
    private val time: AcademicTimeProvider,
) : ViewModel() {

    private val cancel = MutableStateFlow<SportCancelPrompt?>(null)
    private var bookings: List<SportBooking> = emptyList()
    private var lastBody: SportMyBody.Content? = null

    private val body: Flow<SportMyBody> = combine(
        sportDataRepository.observeSportScore(),
        sportBookingRepository.observeSportBookings(),
        holder.operations,
        holder.refreshing,
    ) { score, bookingsState, operations, byUser -> body(score, bookingsState, operations, byUser) }

    val uiState: StateFlow<SportMyUiState> = combine(
        sportDataRepository.observeSportScore(),
        body,
        preferences.observePinnedSections(),
        cancel,
    ) { score, body, pinned, cancel ->
        SportMyUiState(
            score = score.valueOrNull()?.let { SportScoreUi.of(it.summary) },
            body = body,
            sections = sectionsLine(pinned),
            cancel = cancel,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SportMyUiState())

    /** A cancellation that failed: `Не удалось отменить` with the reason. */
    val events: Flow<UiText> = holder.events.map { event ->
        when (event) {
            is SportMyEvent.ShowError -> event.error.toUiText()
        }
    }

    /** Loads once; coming back with content starts no request. */
    fun ensureDataLoaded() = holder.ensureDataLoaded()

    /** A pull or a retry shows the indicator; the first load and background reloads stay silent. */
    fun refresh(mode: RefreshMode) = holder.refresh(mode)

    /** The sheet of the booking [lessonId] as it is now; null once it is gone. */
    fun details(lessonId: Long): SportCommonDetailsArgs? = bookings.firstOrNull { it.lessonId == lessonId }?.toDetailsArgs()

    /** The lesson sheet of [details] now, as the iOS host draws it; [submitted] once it has sent its action. */
    fun sheet(details: SportCommonDetailsArgs, submitted: Boolean): SportLessonSheetUi =
        SportLessonSheetPresenter.present(details, time.now(), time.timeZone, submitted = submitted)

    /**
     * The lesson sheet asked for [action] on [lessonId]: a cancellation is asked first, and only while the booking still
     * offers it now.
     */
    fun onSheetAction(lessonId: Long, action: SportBookingAction) {
        val booking = bookings.firstOrNull { it.lessonId == lessonId } ?: return
        val offered = booking.toDetailsArgs().bookingAction(time.now())
        if (offered != action || offered == SportBookingAction.NONE) return
        val queue = !booking.signed
        cancel.value = SportCancelPrompt(
            lessonId = lessonId,
            message = UiText.Res(if (queue) SportStrings.sport_cancel_queue_question else CoreStrings.sport_cancel_booking_question),
            confirm = UiText.Res(if (queue) SportStrings.sport_cancel_queue_action else CoreStrings.sport_cancel_booking_action),
        )
    }

    /** `Отменить` in the question: cancels while the booking is still there. */
    fun confirmCancel() {
        val prompt = cancel.value ?: return
        cancel.value = null
        bookings.firstOrNull { it.lessonId == prompt.lessonId }?.let(holder::cancel)
    }

    fun dismissCancel() {
        cancel.value = null
    }

    private fun body(
        score: LoadState<SportScore>,
        bookingsState: LoadState<List<SportBooking>>,
        operations: Int,
        byUser: Boolean,
    ): SportMyBody {
        // The score is `Loading` again only after the session was cleared: the last content was another session's.
        if (score == LoadState.Loading) lastBody = null
        val loaded = bookingsState.valueOrNull()
        if (loaded == null || score == LoadState.Loading) {
            if (lastBody == null) bookings = emptyList()
            val error = bookingsState.errorOrNull() ?: score.errorOrNull()
            val previous = lastBody
            return when {
                operations > 0 -> previous?.copy(refreshing = byUser) ?: SportMyBody.Loading
                previous != null -> previous.copy(partialError = error != null, refreshing = false)
                error != null -> SportMyBody.Failed(error)
                else -> SportMyBody.Loading
            }
        }
        val now = time.now()
        val today = time.today()
        bookings = loaded.filter { it.end > now && (it.signed || it.signEntry?.waiting == true) }.sortedBy { it.start }
        val days = bookings
            .groupBy { it.start.toLocalDateTime(time.timeZone).date }
            .map { (date, items) ->
                SportBookingDay(sportDayLabel(date, today), items.map { SportBookingItem(it.lessonId, it.toLessonUi(time)) })
            }
        val errors = listOfNotNull(score.errorOrNull(), bookingsState.errorOrNull())
        return SportMyBody.Content(days, refreshing = byUser, partialError = errors.isNotEmpty()).also { lastBody = it }
    }

    private fun sectionsLine(pinned: Set<SectionName>): UiText =
        if (pinned.isEmpty()) {
            UiText.Res(SportStrings.sport_sections_entry_empty)
        } else {
            UiText.Dynamic(pinned.map { it.shorten() }.sorted().joinToString(", "))
        }
}
