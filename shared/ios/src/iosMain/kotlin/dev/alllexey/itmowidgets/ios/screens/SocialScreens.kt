package dev.alllexey.itmowidgets.ios.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.ProjectLinks
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.me.ui.MeActions
import dev.alllexey.itmowidgets.feature.me.ui.MeProjectLink
import dev.alllexey.itmowidgets.feature.me.ui.MeRoute
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.social.data.SocialShares
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.ui.UserFriendsIosRoute
import dev.alllexey.itmowidgets.feature.social.ui.UserProfileIosRoute
import dev.alllexey.itmowidgets.feature.social.ui.friends.FriendsRoute
import dev.alllexey.itmowidgets.feature.social.ui.profile.UserProfileExits
import dev.alllexey.itmowidgets.feature.social.ui.search.UserSearchRoute
import dev.alllexey.itmowidgets.ios.di.IosKoin
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * The me tab's root (`AppRoutes.TabRoot(ME)`), LA-3's route as Android's shell hosts it (`MeTabRoot`): its rows open
 * their keys through [open], the Swift router (the web sign-in is a shell sheet the demo refuses there), the share
 * button sends the profile link through the share sheet and the project links open in Telegram or Safari. iOS has no
 * developer tools row. The tab has no top bar, so the content keeps clear of the status bar and the tab bar.
 */
fun meViewController(open: (AppRoute) -> Unit): UIViewController {
    val koin = IosKoin.koin()
    val shares = koin.get<SocialShares>()
    val actions = koin.get<PlatformActions>()
    return screenController {
        val scope = rememberCoroutineScope()
        val meActions = remember(scope) {
            MeActions(
                onOpenFriends = { open(AppRoutes.Friends) },
                onFindPeople = { open(AppRoutes.UserSearch) },
                onOpenPrivacy = { open(AppRoutes.Settings(SettingsPage.PRIVACY.name)) },
                onOpenServices = { open(AppRoutes.Settings()) },
                onOpenWebLogin = { open(AppRoutes.WebLogin()) },
                onOpenSettings = { open(AppRoutes.Settings()) },
                onShareProfile = { name, isu -> scope.launch { shares.profile(name, isu) } },
                onOpenProjectLink = { link -> actions.openLink(link.url) },
            )
        }
        Hosted { MeRoute(meActions, showDebugTools = false) }
    }
}

/** The viewer's friends and requests (`AppRoutes.Friends`), as `FriendsFragment` hosts them. */
fun friendsViewController(open: (AppRoute) -> Unit, onBack: () -> Unit): UIViewController = screenController {
    Hosted {
        FriendsRoute(
            onOpenProfile = { isu -> open(AppRoutes.UserProfile(isu)) },
            onOpenSearch = { open(AppRoutes.UserSearch) },
            onOpenSettings = { open(AppRoutes.Settings()) },
            onBack = onBack,
        )
    }
}

/**
 * People search (`AppRoutes.UserSearch`), as `UserSearchFragment` hosts it: the invite action shares the invitation
 * through the share sheet. The field is Compose's own text field on the system keyboard.
 */
fun userSearchViewController(open: (AppRoute) -> Unit, onBack: () -> Unit): UIViewController {
    val shares = IosKoin.koin().get<SocialShares>()
    return screenController {
        val scope = rememberCoroutineScope()
        Hosted {
            UserSearchRoute(
                onOpenProfile = { isu -> open(AppRoutes.UserProfile(isu)) },
                onInvite = { scope.launch { shares.invitation() } },
                onBack = onBack,
            )
        }
    }
}

/**
 * The person profile (`AppRoutes.UserProfile`), as `UserProfileFragment` hosts it: a user's friends, schedule and
 * sport open through [open] with the name the page shows, the share button sends the profile link, [copyIsu] is the
 * Swift host's (the clipboard and its confirmation), a source link opens in Safari. The reviews section stays hidden
 * while iOS does not offer reviews (`PlatformCapabilities.reviews`, IO-09f), so neither the editor nor the report
 * dialog is reachable.
 */
fun userProfileViewController(
    isu: Int,
    open: (AppRoute) -> Unit,
    copyIsu: (Int) -> Unit,
    onBack: () -> Unit,
): UIViewController {
    val koin = IosKoin.koin()
    val shares = koin.get<SocialShares>()
    val actions = koin.get<PlatformActions>()
    val reviewsEnabled = koin.get<PlatformCapabilities>().reviews
    return screenController {
        val scope = rememberCoroutineScope()
        val exits = remember(scope) {
            fun openNamed(page: UserProfileUiState.Content, route: (String) -> AppRoute) {
                scope.launch { open(route(page.displayName.resolve())) }
            }
            UserProfileExits(
                onBack = onBack,
                onShare = { page -> scope.launch { shares.profile(page.displayName.resolve(), page.isu) } },
                onCopyIsu = copyIsu,
                onFriends = { page -> openNamed(page) { name -> AppRoutes.UserFriends(page.isu, name) } },
                onSchedule = { page -> openNamed(page) { name -> AppRoutes.UserSchedule(page.isu, name) } },
                onSport = { page -> openNamed(page) { name -> AppRoutes.UserSport(page.isu, name) } },
                onReviewEditor = {},
                onReport = { _, _ -> },
                onOpenProfile = { author -> open(AppRoutes.UserProfile(author)) },
                onSource = { url -> actions.openLink(url) },
            )
        }
        Hosted { UserProfileIosRoute(isu, exits, reviewsEnabled) }
    }
}

/** Another user's friends (`AppRoutes.UserFriends`), as `UserFriendsFragment` hosts them. */
fun userFriendsViewController(
    isu: Int,
    name: String,
    open: (AppRoute) -> Unit,
    onBack: () -> Unit,
): UIViewController = screenController {
    Hosted {
        UserFriendsIosRoute(
            isu = isu,
            name = name,
            onOpenProfile = { friend -> open(AppRoutes.UserProfile(friend)) },
            onOpenSettings = { open(AppRoutes.Settings()) },
            onBack = onBack,
        )
    }
}

/** The social routes draw their own top bar and no insets (DS-03), so the host pads the safe area on every side. */
@Composable
private fun Hosted(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        content()
    }
}

/** The project pages, as Android's `openProjectLink`: the Telegram channel opens in Telegram when it is installed. */
private val MeProjectLink.url: String
    get() = when (this) {
        MeProjectLink.GITHUB -> ProjectLinks.GITHUB_URL
        MeProjectLink.TELEGRAM -> ProjectLinks.TELEGRAM_URL
    }
