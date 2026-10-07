package dev.alllexey.itmowidgets.feature.social.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.ui.copyToClipboard
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openReviewEditor
import dev.alllexey.itmowidgets.core.ui.navigation.openReviewReport
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import dev.alllexey.itmowidgets.core.ui.openLink
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.shareTextIntent
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.ui.profile.UserProfileExits
import dev.alllexey.itmowidgets.feature.social.ui.profile.UserProfileRoute
import javax.inject.Inject

/**
 * The person profile (`@id/user_profile`), kept by name for the overlay graph and the `/u/` App Link. The screen is
 * `UserProfileRoute` from `:shared:feature-social`, whose Koin ViewModel reads the ISU from this Fragment's arguments;
 * this host maps its exits to sharing, the clipboard, links and the app's navigation.
 */
@AndroidEntryPoint
class UserProfileFragment : Fragment() {

    @Inject lateinit var shareLinks: ShareLinkFactory

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            UserProfileRoute(
                UserProfileExits(
                    onBack = { closeScreen() },
                    onShare = { requireContext().shareUserProfile(shareLinks, it) },
                    onCopyIsu = { requireContext().copyPersonIsu(it) },
                    onFriends = { openUserScreen(AppScreen.USER_FRIENDS, it) },
                    onSchedule = { openUserScreen(AppScreen.USER_SCHEDULE, it) },
                    onSport = { openUserScreen(AppScreen.USER_SPORT, it) },
                    onReviewEditor = { openReviewEditor(it.reviewArgs()) },
                    onReport = { page, reviewId -> openReviewReport(page.reviewArgs(), reviewId) },
                    onOpenProfile = { openUserProfile(it) },
                    onSource = { openLink(it, requireView()) },
                ),
            )
        }

    private fun openUserScreen(screen: AppScreen, page: UserProfileUiState.Content) {
        openScreen(screen, bundleOf(
            UserScreenArgs.ISU to page.isu,
            UserScreenArgs.NAME to page.displayName.resolve(requireContext()),
        ))
    }

}

/** Shares [page]'s profile link under its name; the Compose shell's profile entry calls it too. */
fun Context.shareUserProfile(shareLinks: ShareLinkFactory, page: UserProfileUiState.Content) {
    val name = page.displayName.resolve(this)
    val text = getString(R.string.share_profile_text, name, shareLinks.profile(page.isu))
    startActivity(shareTextIntent(getString(R.string.share_profile_title), text))
}

/** The teacher the review editor and the report dialog of this page are about. */
fun UserProfileUiState.Content.reviewArgs() = TeacherReviewArgs(isu, name)

/**
 * Puts a person's ISU number on the clipboard; Android 13 and newer confirm a copy themselves. The Compose shell's
 * profile entry calls it too.
 */
fun Context.copyPersonIsu(isu: Int) {
    copyToClipboard(getString(R.string.person_isu_label), isu.toString()) {
        Toast.makeText(this, R.string.person_isu_copied, Toast.LENGTH_SHORT).show()
    }
}
