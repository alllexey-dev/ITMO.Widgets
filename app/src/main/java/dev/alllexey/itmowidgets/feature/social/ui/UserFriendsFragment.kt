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
import dev.alllexey.itmowidgets.feature.social.ui.userfriends.UserFriendsRoute

/**
 * Another user's friends (`@id/user_friends`), kept by name for the overlay graph. The screen is `UserFriendsRoute`
 * from `:shared:feature-social`; its Koin ViewModel reads the owner from this Fragment's arguments.
 */
@AndroidEntryPoint
class UserFriendsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            UserFriendsRoute(
                onOpenProfile = { openUserProfile(it) },
                onOpenSettings = { openScreen(AppScreen.SETTINGS) },
                onBack = { closeScreen() },
            )
        }
}
