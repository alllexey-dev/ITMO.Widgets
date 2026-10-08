package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.shared.core.share_profile_text
import dev.alllexey.itmowidgets.shared.core.share_profile_title
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.user_search_invite_chooser
import dev.alllexey.itmowidgets.shared.feature.social.user_search_invite_text
import org.jetbrains.compose.resources.getString
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

/**
 * What the social screens and the Me tab share on iOS, through the system share sheet ([PlatformActions]): a profile
 * with its app link, as Android's `shareOwnProfile` and `UserProfileFragment` send it, and the invitation with
 * [inviteUrl], the App Store page once the app has one, else the site (Android sends its distribution's page).
 * Called on the main thread; false when no sheet could be shown.
 */
class SocialShares(
    private val actions: PlatformActions,
    private val links: ShareLinkFactory,
    private val inviteUrl: String,
) {

    suspend fun profile(name: String, isu: Int): Boolean = actions.shareText(
        getString(CoreRes.string.share_profile_title),
        getString(CoreRes.string.share_profile_text, name, links.profile(isu)),
    )

    suspend fun invitation(): Boolean = actions.shareText(
        getString(Res.string.user_search_invite_chooser),
        getString(Res.string.user_search_invite_text, inviteUrl),
    )
}
