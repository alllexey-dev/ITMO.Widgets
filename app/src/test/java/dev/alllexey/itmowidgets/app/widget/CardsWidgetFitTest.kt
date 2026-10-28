package dev.alllexey.itmowidgets.app.widget

import dev.alllexey.itmowidgets.feature.schedule.ui.widget.CardsWidgetRenderer
import org.junit.Assert.assertEquals
import org.junit.Test

/** How many lesson cards the cards widget shows before the row that folds the rest. */
class CardsWidgetFitTest {

    @Test
    fun everyCardShowsWhenAllFit() {
        assertEquals(3, CardsWidgetRenderer.fit(listOf(70f, 70f, 70f), more = 40f, available = 210f))
    }

    @Test
    fun theRestFoldsIntoTheLastRow() {
        assertEquals(2, CardsWidgetRenderer.fit(listOf(95f, 70f, 70f, 70f), more = 40f, available = 210f))
    }

    @Test
    fun oneCardShowsEvenWithoutRoomForTheRow() {
        assertEquals(1, CardsWidgetRenderer.fit(listOf(95f, 70f), more = 40f, available = 100f))
        assertEquals(0, CardsWidgetRenderer.fit(emptyList(), more = 40f, available = 100f))
    }
}
