package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InFlightLessonsTest {

    private val inFlight = InFlightLessons()

    @Test
    fun `refuses a second request for the same lesson`() {
        assertTrue(inFlight.tryStart(LESSON))
        assertFalse("a double tap must not start a second request", inFlight.tryStart(LESSON))
    }

    @Test
    fun `allows other lessons while one request is running`() {
        inFlight.tryStart(LESSON)

        assertTrue(inFlight.tryStart(OTHER_LESSON))
        assertEquals(setOf(LESSON, OTHER_LESSON), inFlight.ids.value)
    }

    @Test
    fun `releases the lesson after the request finishes`() {
        inFlight.tryStart(LESSON)
        inFlight.finish(LESSON)

        assertEquals(emptySet<Long>(), inFlight.ids.value)
        assertTrue(inFlight.tryStart(LESSON))
    }

    @Test
    fun `finishing an unknown lesson changes nothing`() {
        inFlight.tryStart(LESSON)

        inFlight.finish(OTHER_LESSON)

        assertEquals(setOf(LESSON), inFlight.ids.value)
    }

    private companion object {
        const val LESSON = 100L
        const val OTHER_LESSON = 200L
    }
}
