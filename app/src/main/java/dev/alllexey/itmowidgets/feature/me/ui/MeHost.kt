package dev.alllexey.itmowidgets.feature.me.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.ProjectLinks
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.ui.shareTextIntent

/** What only Android does around the Me tab's `MeRoute`; the shell's Me tab root reads it. */
object MeHost {
    /**
     * Debug-only hook for the store screenshots: hides the developer tools row so a debug build shows the release
     * screen. Release builds never show the row, whatever this says.
     */
    @VisibleForTesting
    @Volatile
    var releaseLook = false
}

/** Shares the name and ISU the profile header shows, with the App Link of [shareLinks]; the Me tab root calls it. */
fun Context.shareOwnProfile(shareLinks: ShareLinkFactory, name: String, isu: Int) {
    startActivity(
        shareTextIntent(
            getString(R.string.share_profile_title),
            getString(R.string.share_profile_text, name, shareLinks.profile(isu)),
        )
    )
}

/** Opens a project page; false when no app could, which `MeRoute` reports. The Me tab root calls it. */
fun Context.openProjectLink(link: MeProjectLink): Boolean = when (link) {
    MeProjectLink.GITHUB -> openFirstLink(ProjectLinks.GITHUB_URL)
    // The native client handles tg:// itself; the web page is only a fallback.
    MeProjectLink.TELEGRAM -> openFirstLink(ProjectLinks.TELEGRAM_DEEPLINK, ProjectLinks.TELEGRAM_URL)
}

/** Opens the first of [urls] some app handles; false when none does. */
private fun Context.openFirstLink(vararg urls: String): Boolean {
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
