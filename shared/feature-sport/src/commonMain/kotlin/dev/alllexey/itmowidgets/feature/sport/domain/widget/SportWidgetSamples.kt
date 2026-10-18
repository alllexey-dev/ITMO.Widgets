package dev.alllexey.itmowidgets.feature.sport.domain.widget

/** The sections of the sample, from the platform's catalog (`widget_preview_sport_*`). */
data class SportWidgetSampleLabels(val swimming: String, val volleyball: String)

/**
 * The sport widget's states in the gallery and the screenshot tests: [QUEUE] the gallery's sample, [BOOKED] a booking
 * today, [DONE] the credit reached without bookings.
 */
enum class SportWidgetSampleState { QUEUE, BOOKED, DONE }

/**
 * The fixed sample of the sport widget, seen on the design lab's Monday, 7 September (`SchedulePreviewScenario`):
 * never a user's score or bookings. Buildings are MyITMO's addresses, shortened by the drawing side as real ones are.
 */
object SportWidgetSamples {

    fun snapshot(state: SportWidgetSampleState, labels: SportWidgetSampleLabels): SportWidgetSnapshot = when (state) {
        SportWidgetSampleState.QUEUE -> SportWidgetSnapshot(
            kind = SportWidgetKind.CONTENT, visits = 15, bonus = 15, items = listOf(swimmingQueue(labels), volleyball(labels))
        )
        SportWidgetSampleState.BOOKED -> SportWidgetSnapshot(
            kind = SportWidgetKind.CONTENT, visits = 15, bonus = 15, items = listOf(swimmingToday(labels), volleyball(labels))
        )
        SportWidgetSampleState.DONE -> SportWidgetSnapshot(kind = SportWidgetKind.CONTENT, visits = 65, bonus = 39, items = emptyList())
    }

    private fun swimmingQueue(labels: SportWidgetSampleLabels) = SportWidgetItem(
        lessonId = 101, date = "2026-09-08", day = SportWidgetDay.TOMORROW, time = "15:20", section = labels.swimming,
        building = LOMO, status = SportWidgetStatus.QUEUE, position = 2, total = 5,
    )

    private fun swimmingToday(labels: SportWidgetSampleLabels) = SportWidgetItem(
        lessonId = 100, date = "2026-09-07", day = SportWidgetDay.TODAY, time = "17:00", section = labels.swimming,
        building = LOMO, status = SportWidgetStatus.BOOKED,
    )

    private fun volleyball(labels: SportWidgetSampleLabels) = SportWidgetItem(
        lessonId = 102, date = "2026-09-10", day = SportWidgetDay.LATER, time = "15:20", section = labels.volleyball,
        building = KRONVA, status = SportWidgetStatus.BOOKED,
    )

    private const val LOMO = "улица Ломоносова, 9"
    private const val KRONVA = "Кронверкский проспект, 49"
}
