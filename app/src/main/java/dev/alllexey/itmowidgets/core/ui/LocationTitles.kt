package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import dev.alllexey.itmowidgets.core.text.buildingShortTitle as buildingShortTitleText
import dev.alllexey.itmowidgets.core.text.roomShortTitle as roomShortTitleText

// Android adapters of `core.text.LocationTitles`; each goes with its last caller.

fun buildingShortTitle(context: Context, raw: String, maxLength: Int? = null): String =
    buildingShortTitleText(raw, maxLength).resolve(context)

fun roomShortTitle(context: Context, raw: String): String = roomShortTitleText(raw).resolve(context)
