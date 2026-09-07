package dev.alllexey.itmowidgets.core.ui

import com.google.android.material.transition.MaterialSharedAxis

/** Matching forward/backward motion for Profile -> Settings -> category. */
object SettingsLevelMotion {
    fun transition(forward: Boolean) = MaterialSharedAxis(MaterialSharedAxis.X, forward).apply {
        duration = 220
    }
}
