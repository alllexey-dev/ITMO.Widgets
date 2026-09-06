package dev.alllexey.itmowidgets.feature.sport.data.mapper

import api.myitmo.model.sport.SportScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportMappersTest {

    @Test
    fun `maps a missing attendance history to an empty list`() {
        val source = SportScore().apply {
            sum = SportScore.Sum().apply {
                attendances = 12
                other = 4
            }
            attendances = null
        }

        val result = source.toModel()

        assertEquals(12, result.attendances)
        assertEquals(4, result.other)
        assertTrue(result.attendancesData.isEmpty())
    }
}
