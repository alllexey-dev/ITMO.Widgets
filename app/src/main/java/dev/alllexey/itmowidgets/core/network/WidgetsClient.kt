package dev.alllexey.itmowidgets.core.network

import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.ItmoWidgetsImpl

class WidgetsClient(
    myItmo: MyItmo,
    baseUrl: String = STABLE_BASE_URL
) : ItmoWidgetsImpl(myItmo, baseUrl)
