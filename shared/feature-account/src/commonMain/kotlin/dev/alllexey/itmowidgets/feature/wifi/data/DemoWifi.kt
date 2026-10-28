package dev.alllexey.itmowidgets.feature.wifi.data

import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiCredentials

/** The demo session's fictional account: the demo user's ISU and a password no network accepts. */
internal object DemoWifi {
    val CREDENTIALS = WifiCredentials(login = DemoPeople.ME_ISU.toString(), password = "00000000")
}
