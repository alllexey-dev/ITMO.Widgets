package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import dev.alllexey.itmowidgets.core.text.shortPersonName as commonShortPersonName
import dev.alllexey.itmowidgets.core.text.userDisplayName as userDisplayNameText

// Android adapters of `core.text.UserNames`; each goes with its last caller.

fun Context.userDisplayName(name: String, isu: Int): String = userDisplayNameText(name, isu).resolve(this)

fun shortPersonName(name: String): String = commonShortPersonName(name)
