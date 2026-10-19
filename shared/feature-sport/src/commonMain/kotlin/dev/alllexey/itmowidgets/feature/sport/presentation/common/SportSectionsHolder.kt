package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.sport.domain.model.MySportSection
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSectionSlot
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSectionRepository
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** The sections as `Моё` and the lesson sheet see them: the user's own, the slots of booked lessons, what is busy. */
data class SportSectionsState(
    val sections: LoadState<List<MySportSection>> = LoadState.Loading,
    val slots: Map<Long, SportSectionSlot> = emptyMap(),
    /** Lessons and sections with a change in flight: their switches wait. */
    val busyLessons: Set<Long> = emptySet(),
    val busySections: Set<Long> = emptySet(),
)

/**
 * Sections for the whole app (DECISIONS.md, Спорт, «Секция»): `Моё` and every lesson sheet read and change them through
 * this one holder, so a switch in a sheet shows in `Моё` at once. It outlives every screen and holds no user data:
 * the repository does, and its session cleaner clears it. Backend queues and cancels a section's dates inside the
 * section request, so a change that went through reloads the queue entries right away.
 */
class SportSectionsHolder(
    private val repository: SportSectionRepository,
    private val queues: SportDataRepository,
    private val scope: CoroutineScope,
) {
    private val slots = MutableStateFlow<Map<Long, SportSectionSlot>>(emptyMap())
    private val busyLessons = MutableStateFlow<Set<Long>>(emptySet())
    private val busySections = MutableStateFlow<Set<Long>>(emptySet())
    private val notices = EventQueue<UiText>()

    val state: StateFlow<SportSectionsState> =
        combine(repository.observeSections(), slots, busyLessons, busySections, ::SportSectionsState)
            .stateIn(scope, SharingStarted.Eagerly, SportSectionsState())

    /** `Сделали секцией`, `Секция выключена` or why a change failed. */
    val events: Flow<UiText> = notices.events

    /** The sections' My ITMO names, for the `Мои секции` chip of `Запись`. */
    fun sectionNames(): Flow<Set<SectionName>> = state
        .map { current -> current.sections.valueOrNull().orEmpty().mapTo(mutableSetOf()) { SectionName(it.lesson.sectionName) } }
        .distinctUntilChanged()

    fun refresh() {
        scope.launch { repository.refreshSections() }
    }

    /** Loads the slots of booked [lessonIds] the holder does not know yet. */
    fun loadSlots(lessonIds: Collection<Long>) {
        val missing = lessonIds.filter { it !in slots.value }
        if (missing.isNotEmpty()) scope.launch { fetchSlots(missing) }
    }

    /** The switch of the booked [lessonId]: makes its slot a section, or stops the section it is in. */
    fun toggle(lessonId: Long) {
        val slot = slots.value[lessonId] ?: return
        if (lessonId in busyLessons.value || (slot.subscriptionId == null && slot.full)) return
        scope.launch {
            busyLessons.update { it + lessonId }
            val subscription = slot.subscriptionId
            val result = if (subscription == null) repository.create(lessonId) else repository.stop(subscription)
            notify(result, if (subscription == null) SportStrings.sport_section_made else SportStrings.sport_section_stopped)
            if (result is AppResult.Success) queues.refreshSportQueueEntries()
            fetchSlots(slots.value.keys)
            busyLessons.update { it - lessonId }
        }
    }

    /** The off switch of the section [sectionId]. */
    fun stop(sectionId: Long) = changeSection(sectionId, SportStrings.sport_section_stopped) { repository.stop(sectionId) }

    /** One date of [sectionId]: on queues it as usual, off skips it and cancels its queue; the place stays. */
    fun setDate(sectionId: Long, start: Instant, on: Boolean) =
        changeSection(sectionId, null) { repository.setSkipped(sectionId, start, skipped = !on) }

    private fun changeSection(sectionId: Long, done: String?, change: suspend () -> AppResult<*>) {
        if (sectionId in busySections.value) return
        scope.launch {
            busySections.update { it + sectionId }
            val result = change()
            notify(result, done)
            if (result is AppResult.Success) queues.refreshSportQueueEntries()
            if (done != null) fetchSlots(slots.value.keys)
            busySections.update { it - sectionId }
        }
    }

    private suspend fun notify(result: AppResult<*>, done: String?) {
        when (result) {
            is AppResult.Success -> done?.let { notices.send(UiText.Res(it)) }
            is AppResult.Failure -> notices.send(result.error.toUiText())
        }
    }

    private suspend fun fetchSlots(lessonIds: Collection<Long>) {
        if (lessonIds.isEmpty()) return
        val result = repository.slots(lessonIds.toList())
        if (result is AppResult.Success) slots.update { known -> known + result.value.associateBy { it.lessonId } }
    }
}
