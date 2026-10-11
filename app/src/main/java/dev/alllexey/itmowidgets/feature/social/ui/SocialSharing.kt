package dev.alllexey.itmowidgets.feature.social.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.ui.copyToClipboard
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.shareTextIntent
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState

/** Shares [page]'s profile link under its name; the Compose shell's profile entry calls it. */
fun Context.shareUserProfile(shareLinks: ShareLinkFactory, page: UserProfileUiState.Content) {
    val name = page.displayName.resolve(this)
    val text = getString(R.string.share_profile_text, name, shareLinks.profile(page.isu))
    startActivity(shareTextIntent(getString(R.string.share_profile_title), text))
}

/** The teacher the review editor and the report dialog of this page are about. */
fun UserProfileUiState.Content.reviewArgs() = TeacherReviewArgs(isu, name)

/**
 * Puts a person's ISU number on the clipboard; Android 13 and newer confirm a copy themselves. The Compose shell's
 * profile entry calls it.
 */
fun Context.copyPersonIsu(isu: Int) {
    copyToClipboard(getString(R.string.person_isu_label), isu.toString()) {
        Toast.makeText(this, R.string.person_isu_copied, Toast.LENGTH_SHORT).show()
    }
}

/** Shares the invitation with the download link of this distribution; the Compose shell's search entry calls it. */
fun Context.shareSocialInvitation() {
    val text = getString(R.string.user_search_invite_text, BuildConfig.DOWNLOAD_URL)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(intent, getString(R.string.user_search_invite_chooser)))
}
