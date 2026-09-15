package dev.alllexey.itmowidgets.feature.social.ui

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen

/** Single entry point to a public profile, so every list opens it the same way. */
object UserProfileNavigation {

    fun open(fragment: Fragment, isu: Int) {
        fragment.openScreen(AppScreen.USER_PROFILE, bundleOf(UserScreenArgs.ISU to isu))
    }
}
