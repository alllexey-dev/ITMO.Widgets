package dev.alllexey.itmowidgets.feature.settings.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundWorkScreensTest {

    @Test
    fun `Xiaomi, Redmi and POCO open the activity control first`() {
        val miui = listOf(BackgroundWorkScreen.MIUI_POWER_KEEPER, BackgroundWorkScreen.APP_DETAILS)

        assertEquals(miui, BackgroundWorkScreens.forDevice("Xiaomi", "Redmi"))
        assertEquals(miui, BackgroundWorkScreens.forDevice("Xiaomi", "POCO"))
        assertEquals(miui, BackgroundWorkScreens.forDevice("xiaomi", "xiaomi"))
    }

    @Test
    fun `other devices open the battery optimization list first`() {
        val other = listOf(BackgroundWorkScreen.BATTERY_OPTIMIZATION_LIST, BackgroundWorkScreen.APP_DETAILS)

        assertEquals(other, BackgroundWorkScreens.forDevice("Google", "google"))
        assertEquals(other, BackgroundWorkScreens.forDevice("samsung", "samsung"))
    }
}
