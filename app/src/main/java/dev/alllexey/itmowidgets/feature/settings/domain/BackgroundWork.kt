package dev.alllexey.itmowidgets.feature.settings.domain

/** Whether Android lets the app's background checks run. */
interface BackgroundWorkAccess {
    /** True when Android does not restrict the app in the background (it ignores battery optimizations). */
    fun isUnrestricted(): Boolean
}

/** System pages where the user can lift the background restrictions of the app. */
enum class BackgroundWorkScreen {
    /** MIUI and HyperOS «Контроль активности» of the app; the list of battery optimizations does not help there. */
    MIUI_POWER_KEEPER,
    BATTERY_OPTIMIZATION_LIST,
    APP_DETAILS
}

object BackgroundWorkScreens {
    private val XIAOMI_BRANDS = setOf("xiaomi", "redmi", "poco")

    /** Pages to try in order: the first one the device can open wins. */
    fun forDevice(manufacturer: String, brand: String): List<BackgroundWorkScreen> =
        if (manufacturer.equals("xiaomi", ignoreCase = true) || brand.lowercase() in XIAOMI_BRANDS) {
            listOf(BackgroundWorkScreen.MIUI_POWER_KEEPER, BackgroundWorkScreen.APP_DETAILS)
        } else {
            listOf(BackgroundWorkScreen.BATTERY_OPTIMIZATION_LIST, BackgroundWorkScreen.APP_DETAILS)
        }
}
