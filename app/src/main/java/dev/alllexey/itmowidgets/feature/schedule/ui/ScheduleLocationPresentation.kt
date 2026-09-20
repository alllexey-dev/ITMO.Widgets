package dev.alllexey.itmowidgets.feature.schedule.ui

import android.content.Context
import dev.alllexey.itmowidgets.core.ui.buildingShortTitle
import dev.alllexey.itmowidgets.core.ui.roomShortTitle
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room

fun Building.shortTitle(context: Context, maxLength: Int? = null): String = buildingShortTitle(context, raw, maxLength)

fun Room.shortTitle(context: Context): String = roomShortTitle(context, raw)
