package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportTimeSlot
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime

/** The JVM half of `SportSignStateFactoryTest`: only the JVM lets a test change the default locale. */
class SportSignStateFactoryLocaleTest {

    private val timeProvider = FixedAcademicTime(LocalDateTime(2026, 7, 22, 9, 0))
    private val factory = SportSignStateFactory(timeProvider)

    @Test
    fun calendarNamesAreRussianWhateverTheSystemLocale() {
        val systemLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val state = factory.create(
                lessons = emptyList(),
                catalog = SportFilterCatalog(
                    buildings = emptyList(),
                    sections = emptyList(),
                    sportTypes = emptyList(),
                    teachers = emptyList()
                ),
                timeSlots = listOf(SportTimeSlot(id = 1, start = "10:00", end = "11:30")),
                userFilters = SportSignFilters(selectedDate = timeProvider.today()),
                hasPartialError = false
            )

            assertEquals("Июль", state.currentMonthName)
            assertEquals("ср", state.displayedWeek.single { it.date == timeProvider.today() }.dayOfWeek)
        } finally {
            Locale.setDefault(systemLocale)
        }
    }
}
