package dev.alllexey.itmowidgets.feature.social.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.social.ui.friends.FriendsRoute

/**
 * The viewer's friends and requests (`@id/friends`), kept by name for the overlay graph. The screen is `FriendsRoute`
 * from `:shared:feature-social`; this host only maps its exits to the app's navigation.
 */
@AndroidEntryPoint
class FriendsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            FriendsRoute(
                onOpenProfile = { openUserProfile(it) },
                onOpenSearch = { openScreen(AppScreen.USER_SEARCH) },
                onOpenSettings = { openScreen(AppScreen.SETTINGS) },
                onBack = { closeScreen() },
            )
        }
}
