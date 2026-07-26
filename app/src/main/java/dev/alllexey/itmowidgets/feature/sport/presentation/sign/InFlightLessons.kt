package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which lessons currently have a booking request in flight.
 *
 * Booking changes state in MyITMO, so a second tap must not send a second request.
 * The guard is per lesson on purpose: a screen-wide lock would freeze every other
 * row while one request is running.
 */
class InFlightLessons {

    private val mutableIds = MutableStateFlow<Set<Long>>(emptySet())
    val ids: StateFlow<Set<Long>> = mutableIds.asStateFlow()

    /** Returns false when the lesson already has a request in flight. */
    fun tryStart(lessonId: Long): Boolean {
        if (lessonId in mutableIds.value) return false
        mutableIds.value = mutableIds.value + lessonId
        return true
    }

    fun finish(lessonId: Long) {
        mutableIds.value = mutableIds.value - lessonId
    }
}
