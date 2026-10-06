package dev.alllexey.itmowidgets.feature.settings.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundWorkScreensTest {

    @Test
    fun xiaomiRedmiAndPOCOOpenTheActivityControlFirst() {
        val miui = listOf(
            BackgroundWorkScreen.MIUI_POWER_DETAIL,
            BackgroundWorkScreen.MIUI_POWER_KEEPER,
            BackgroundWorkScreen.APP_DETAILS
        )

        assertEquals(miui, BackgroundWorkScreens.forDevice("Xiaomi", "Redmi"))
        assertEquals(miui, BackgroundWorkScreens.forDevice("Xiaomi", "POCO"))
        assertEquals(miui, BackgroundWorkScreens.forDevice("xiaomi", "xiaomi"))
    }

    @Test
    fun otherDevicesOpenTheBatteryOptimizationListFirst() {
        val other = listOf(BackgroundWorkScreen.BATTERY_OPTIMIZATION_LIST, BackgroundWorkScreen.APP_DETAILS)

        assertEquals(other, BackgroundWorkScreens.forDevice("Google", "google"))
        assertEquals(other, BackgroundWorkScreens.forDevice("samsung", "samsung"))
    }
}
