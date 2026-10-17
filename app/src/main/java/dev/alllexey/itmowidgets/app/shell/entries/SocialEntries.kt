package dev.alllexey.itmowidgets.app.shell.entries

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.social.ui.copyPersonIsu
import dev.alllexey.itmowidgets.feature.me.ui.shareOwnProfile
import dev.alllexey.itmowidgets.feature.social.ui.friends.FriendsExits
import dev.alllexey.itmowidgets.feature.social.ui.friends.FriendsRoute
import dev.alllexey.itmowidgets.feature.social.ui.profile.UserProfileExits
import dev.alllexey.itmowidgets.feature.social.ui.profile.UserProfileRoute
import dev.alllexey.itmowidgets.feature.social.ui.reviewArgs
import dev.alllexey.itmowidgets.feature.social.ui.shareSocialInvitation
import dev.alllexey.itmowidgets.feature.social.ui.shareUserProfile
import dev.alllexey.itmowidgets.feature.social.ui.userfriends.UserFriendsRoute
import org.koin.core.parameter.parametersOf

/**
 * The social keys: Друзья (with people search), another user's friends and the person card. Every profile
 * link goes through [openUserProfile], so an ISU outside `1..Int.MAX_VALUE` opens nothing. The schedule's friend
 * picker is the schedule's own ([scheduleEntries]).
 */
internal fun EntryRegistry.Builder.socialEntries() {
    entry<AppRoutes.Friends> { key, navigator -> FriendsEntry(onBack = { navigator.close(key) }, navigator) }
    entry<AppRoutes.UserFriends>(
        args = { bundleOf(UserScreenArgs.ISU to it.isu, UserScreenArgs.NAME to it.name) },
    ) { key, navigator ->
        UserFriendsRoute(
            onOpenProfile = navigator::openUserProfile,
            onConnect = { navigator.open(AppRoutes.Settings(SERVICES_PAGE)) },
            onBack = { navigator.close(key) },
            viewModel = entryViewModel(),
        )
    }
    entry<AppRoutes.UserProfile>(args = { bundleOf(UserScreenArgs.ISU to it.isu) }) { key, navigator ->
        UserProfileEntry(onBack = { navigator.close(key) }, navigator)
    }
}

/** Opens the person profile of [isu] only when [UserScreenArgs.profileIsu] accepts it. */
internal fun Nav3AppNavigator.openUserProfile(isu: Int) {
    UserScreenArgs.profileIsu(isu.toLong())?.let { open(AppRoutes.UserProfile(it)) }
}

/** Друзья: sharing the own profile and the invitation (with this distribution's download link) stay on this activity. */
@Composable
private fun FriendsEntry(onBack: () -> Unit, navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    val exits = remember(navigator, context, onBack) {
        FriendsExits(
            onBack = onBack,
            onOpenProfile = navigator::openUserProfile,
            onConnect = { navigator.open(AppRoutes.Settings(SERVICES_PAGE)) },
            onShareProfile = { name, isu ->
                context.shareOwnProfile(KoinStarter.ensureStarted(context).get<ShareLinkFactory>(), name, isu)
            },
            onInvite = { context.shareSocialInvitation() },
        )
    }
    FriendsRoute(exits, viewModel = entryViewModel())
}

/**
 * The person profile: sharing, the clipboard and the source links stay on this activity; the person's
 * friends, schedule, sport, the review editor and the report open as their own keys, named as the page shows them.
 */
@Composable
private fun UserProfileEntry(onBack: () -> Unit, navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    val view = LocalView.current
    val platform = LocalPlatformActions.current
    val exits = remember(navigator, context, view, platform, onBack) {
        userProfileExits(navigator, context, onBack) { url -> openSource(platform, view, url) }
    }
    UserProfileRoute(exits, viewModel = entryViewModel())
}

private fun userProfileExits(
    navigator: Nav3AppNavigator,
    context: Context,
    onBack: () -> Unit,
    onSource: (String) -> Unit,
) = UserProfileExits(
    onBack = onBack,
    onShare = { page ->
        context.shareUserProfile(KoinStarter.ensureStarted(context).get<ShareLinkFactory>(), page)
    },
    onCopyIsu = { isu -> context.copyPersonIsu(isu) },
    onInvite = { context.shareSocialInvitation() },
    onFriends = { page -> navigator.open(AppRoutes.UserFriends(page.isu, page.displayName.resolve(context))) },
    onSchedule = { page -> navigator.open(AppRoutes.UserSchedule(page.isu, page.displayName.resolve(context))) },
    onSport = { page -> navigator.open(AppRoutes.UserSport(page.isu, page.displayName.resolve(context))) },
    onReviewEditor = { page -> navigator.open(AppRoutes.ReviewEditor(page.reviewArgs())) },
    onReport = { page, reviewId -> navigator.open(AppRoutes.ReportReview(page.reviewArgs(), reviewId)) },
    onOpenProfile = navigator::openUserProfile,
    onSource = onSource,
)

/** A review's source page outside the app; without a handler a snackbar says so. */
private fun openSource(platform: PlatformActions, anchor: View, url: String) {
    if (!platform.openLink(url)) Snackbar.make(anchor, R.string.link_open_failed, Snackbar.LENGTH_SHORT).show()
}

/**
 * Koin's definition of [VM] in this entry's own store, with the entry's `SavedStateHandle` (its arguments, seeded by
 * the shell) as Koin's `koinViewModel()` passes it.
 */
@Composable
private inline fun <reified VM : ViewModel> entryViewModel(): VM {
    val context = LocalContext.current
    return viewModel { KoinStarter.ensureStarted(context).get<VM> { parametersOf(createSavedStateHandle()) } }
}

/** `SettingsPage.SERVICES`: the connection to ITMO.Widgets. */
private const val SERVICES_PAGE = "SERVICES"
