package dev.alllexey.itmowidgets.feature.social.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.social.ui.search.UserSearchRoute

/**
 * People search (`@id/user_search`), kept by name for the overlay graph. The screen is `UserSearchRoute` from
 * `:shared:feature-social`; this host maps its exits to the app's navigation and shares the invitation with the
 * download link of this distribution.
 */
@AndroidEntryPoint
class UserSearchFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            UserSearchRoute(
                onOpenProfile = { openUserProfile(it) },
                onInvite = { requireContext().shareSocialInvitation() },
                onBack = { closeScreen() },
            )
        }
}

/** Shares the invitation with the download link of this distribution; the Compose shell's search entry calls it too. */
fun Context.shareSocialInvitation() {
    val text = getString(R.string.user_search_invite_text, BuildConfig.DOWNLOAD_URL)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(intent, getString(R.string.user_search_invite_chooser)))
}
