package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.UiTextResolver
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings

/**
 * What the social screens and the Me tab share on iOS, through the system share sheet ([PlatformActions]): a profile
 * with its app link, as Android's `shareOwnProfile` and `shareUserProfile` send it, and the invitation with
 * [inviteUrl], the App Store page once the app has one, else the site (Android sends its distribution's page); [texts]
 * resolves the catalog texts.
 * Called on the main thread; false when no sheet could be shown.
 */
class SocialShares(
    private val actions: PlatformActions,
    private val links: ShareLinkFactory,
    private val inviteUrl: String,
    private val texts: UiTextResolver,
) {

    suspend fun profile(name: String, isu: Int): Boolean = actions.shareText(
        text(CoreStrings.share_profile_title),
        text(CoreStrings.share_profile_text, name, links.profile(isu)),
    )

    suspend fun invitation(): Boolean = actions.shareText(
        text(SocialStrings.user_search_invite_chooser),
        text(SocialStrings.user_search_invite_text, inviteUrl),
    )

    private suspend fun text(key: String, vararg arguments: Any): String =
        texts.resolve(UiText.Res(key, arguments.toList()))
}
