package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import dev.alllexey.itmowidgets.R

/** Backend sends an empty name until the owner's identity is published; never show it raw. */
fun Context.userDisplayName(name: String, isu: Int): String =
    name.trim().ifEmpty { getString(R.string.user_name_placeholder, isu) }
