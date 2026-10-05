package dev.alllexey.itmowidgets.client.app

import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.client.support.RouteCase

/** One [RouteCase] per public function of [AppApi], with synthetic arguments. */
object AppRouteCases {
    val versionInfo = RouteCase("versionInfo") { app.versionInfo(DevicePlatform.IOS) }

    val all: List<RouteCase> = listOf(versionInfo)
}
