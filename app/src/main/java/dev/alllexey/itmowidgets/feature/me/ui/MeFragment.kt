package dev.alllexey.itmowidgets.feature.me.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.ProjectLinks
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openWebLogin
import dev.alllexey.itmowidgets.core.ui.shareText
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import javax.inject.Inject

/**
 * The Me tab (`navigation_me`), kept by name for the main graph. The screen is `MeRoute` from
 * `:shared:feature-account`; its ViewModel is Koin's, in this Fragment's store. This host keeps what only Android
 * does: navigation through `AppNavigator`, sharing with the App Link and opening the project pages.
 */
@AndroidEntryPoint
class MeFragment : Fragment() {

    @Inject lateinit var shareLinks: ShareLinkFactory

    private val actions = MeActions(
        onOpenFriends = { openScreen(AppScreen.FRIENDS) },
        onFindPeople = { openScreen(AppScreen.USER_SEARCH) },
        onOpenPrivacy = { openScreen(AppScreen.SETTINGS, bundleOf(SETTINGS_PAGE_ARGUMENT to SETTINGS_PAGE_PRIVACY)) },
        onOpenServices = { openScreen(AppScreen.SETTINGS) },
        onOpenWebLogin = { openWebLogin() },
        onOpenSettings = { openScreen(AppScreen.SETTINGS) },
        onOpenDebugTools = { openScreen(AppScreen.DEBUG_TOOLS) },
        onShareProfile = { name, isu -> shareOwnProfile(name, isu) },
        onOpenProjectLink = { link ->
            when (link) {
                MeProjectLink.GITHUB -> openLink(ProjectLinks.GITHUB_URL)
                // The native client handles tg:// itself; the web page is only a fallback.
                MeProjectLink.TELEGRAM -> openLink(ProjectLinks.TELEGRAM_DEEPLINK, ProjectLinks.TELEGRAM_URL)
            }
        },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            MeRoute(actions, showDebugTools = BuildConfig.DEBUG && !releaseLook)
        }.apply {
            // The tab stays one stationary surface under an overlay's back gesture.
            isTransitionGroup = true
        }

    /** Shares the name and ISU the profile header shows. */
    private fun shareOwnProfile(name: String, isu: Int) {
        shareText(
            getString(R.string.share_profile_title),
            getString(R.string.share_profile_text, name, shareLinks.profile(isu)),
        )
    }

    /** Opens the first of [urls] some app handles; false when none does. */
    private fun openLink(vararg urls: String): Boolean {
        for (url in urls) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                return true
            } catch (_: ActivityNotFoundException) {
                continue
            }
        }
        return false
    }

    companion object {
        /** Mirrors the settings graph argument; features must not import each other. */
        private const val SETTINGS_PAGE_ARGUMENT = "settings_page"
        private const val SETTINGS_PAGE_PRIVACY = "PRIVACY"

        /**
         * Debug-only hook for the store screenshots: hides the developer tools row so a debug build shows the
         * release screen. Release builds never show the row, whatever this says.
         */
        @VisibleForTesting
        @Volatile
        var releaseLook = false
    }
}
