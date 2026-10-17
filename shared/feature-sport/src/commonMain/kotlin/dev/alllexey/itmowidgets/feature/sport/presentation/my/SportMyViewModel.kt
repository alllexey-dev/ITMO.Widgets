package dev.alllexey.itmowidgets.feature.sport.presentation.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.errorOrNull
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonSheetPresenter
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonSheetUi
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSectionsHolder
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSectionsState
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.toLocalDateTime

/**
 * `Моё` on both platforms over [SportBookingsHolder], which Сегодня and the schedule share: the score, the bookings and
 * queues that are still ahead, and the cancellation a lesson sheet asked for, confirmed first; and over
 * [SportSectionsHolder], which every lesson sheet shares: `Сделать секцией` on booked lessons and the user's sections.
 */
class SportMyViewModel(
    sportBookingRepository: SportBookingRepository,
    sportDataRepository: SportDataRepository,
    private val holder: SportBookingsHolder,
    private val sections: SportSectionsHolder,
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
        sections.state,
        cancel,
    ) { score, body, sections, cancel ->
        SportMyUiState(
            score = score.valueOrNull()?.let { SportScoreUi.of(it.summary) },
            body = body.withSections(sections),
            sections = SportSectionsPresenter.cards(sections, time.today(), time.timeZone),
            cancel = cancel,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SportMyUiState())

    /** A cancellation that failed (`Не удалось отменить` with the reason), or a section changed or failed to. */
    val events: Flow<UiText> = merge(
        holder.events.map { event ->
            when (event) {
                is SportMyEvent.ShowError -> event.error.toUiText()
            }
        },
        sections.events,
    )

    /** Loads once; coming back with content starts no request. */
    fun ensureDataLoaded() {
        holder.ensureDataLoaded()
        if (sections.state.value.sections == LoadState.Loading) sections.refresh()
    }

    /** A pull or a retry shows the indicator; the first load and background reloads stay silent. */
    fun refresh(mode: RefreshMode) {
        holder.refresh(mode)
        sections.refresh()
    }

    /** `Сделать секцией` of the booked [lessonId], on its card or in its sheet; off again when it is one. */
    fun toggleSection(lessonId: Long) = sections.toggle(lessonId)

    /** The switch of [lessonId]'s sheet, as the iOS host draws it; null for a lesson that is not booked. */
    fun sectionToggle(lessonId: Long): SportSectionToggleUi? =
        if (bookings.any { it.lessonId == lessonId && it.signed }) SportSectionsPresenter.toggle(sections.state.value, lessonId) else null

    /** The off switch of the section [sectionId]. */
    fun stopSection(sectionId: Long) = sections.stop(sectionId)

    /** The date at [index] of the section [sectionId] as shown: off skips it, on queues it again. */
    fun setSectionDate(sectionId: Long, index: Int, on: Boolean) {
        val date = uiState.value.sections.firstOrNull { it.id == sectionId }?.dates?.getOrNull(index) ?: return
        sections.setDate(sectionId, date.start, on)
    }

    /** The sheet of the booking [lessonId] as it is now; null once it is gone. */
    fun details(lessonId: Long): SportCommonDetailsArgs? = bookings.firstOrNull { it.lessonId == lessonId }?.toDetailsArgs()

    /**
     * The lesson sheet of [details] now, as the iOS host draws it; [submitted] once it has sent its action; the tone of
     * [teacherLevel] from [dev.alllexey.itmowidgets.feature.sport.presentation.common.SportTeacherLevels].
     */
    fun sheet(details: SportCommonDetailsArgs, submitted: Boolean, teacherLevel: TeacherLevel?): SportLessonSheetUi =
        SportLessonSheetPresenter.present(details, time.now(), time.timeZone, submitted = submitted, teacherLevel = teacherLevel)

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
        sections.loadSlots(bookings.filter { it.signed }.map { it.lessonId })
        val days = bookings
            .groupBy { it.start.toLocalDateTime(time.timeZone).date }
            .map { (date, items) ->
                SportBookingDay(sportDayLabel(date, today), items.map { booking ->
                    SportBookingItem(
                        lessonId = booking.lessonId,
                        lesson = booking.toLessonUi(time),
                        cancelUntil = SportLessonSheetPresenter.cancelUntil(booking.toDetailsArgs(), now, time.timeZone),
                    )
                })
            }
        val errors = listOfNotNull(score.errorOrNull(), bookingsState.errorOrNull())
        return SportMyBody.Content(days, refreshing = byUser, partialError = errors.isNotEmpty()).also { lastBody = it }
    }

    /** The booked lessons' `Сделать секцией`, from the shared slots. */
    private fun SportMyBody.withSections(state: SportSectionsState): SportMyBody {
        if (this !is SportMyBody.Content) return this
        val booked = bookings.filter { it.signed }.mapTo(mutableSetOf()) { it.lessonId }
        return copy(
            days = days.map { day ->
                day.copy(
                    bookings = day.bookings.map { item ->
                        item.copy(section = if (item.lessonId in booked) SportSectionsPresenter.toggle(state, item.lessonId) else null)
                    },
                )
            },
        )
    }
}
