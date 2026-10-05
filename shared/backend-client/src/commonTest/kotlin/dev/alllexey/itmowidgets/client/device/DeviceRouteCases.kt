package dev.alllexey.itmowidgets.client.device

import dev.alllexey.itmowidgets.client.support.RouteCase

/** One [RouteCase] per public function of [DeviceApi], with synthetic arguments. */
object DeviceRouteCases {
    const val TOKEN = "synthetic-fcm-token"
    const val DEVICE_NAME = "Pixel 8 (synthetic)"

    /** What 2.2 sends: the vendored `requests/RegisterDeviceRequest.json`. */
    val released = RegisterDeviceRequest(TOKEN, DEVICE_NAME, platform = null, alertsAllowed = null, appVersion = null)

    val register = RouteCase("register") { device.register(released) }
    val unregisterCurrent = RouteCase("unregisterCurrent") {
        device.unregisterCurrent(UnregisterDeviceRequest(TOKEN))
    }

    val all: List<RouteCase> = listOf(register, unregisterCurrent)
}
