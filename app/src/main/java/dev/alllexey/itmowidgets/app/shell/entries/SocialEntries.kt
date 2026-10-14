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
import dev.alllexey.itmowidgets.feature.social.ui.friends.FriendsRoute
import dev.alllexey.itmowidgets.feature.social.ui.profile.UserProfileExits
import dev.alllexey.itmowidgets.feature.social.ui.profile.UserProfileRoute
import dev.alllexey.itmowidgets.feature.social.ui.reviewArgs
import dev.alllexey.itmowidgets.feature.social.ui.search.UserSearchRoute
import dev.alllexey.itmowidgets.feature.social.ui.shareSocialInvitation
import dev.alllexey.itmowidgets.feature.social.ui.shareUserProfile
import dev.alllexey.itmowidgets.feature.social.ui.userfriends.UserFriendsRoute
import org.koin.core.parameter.parametersOf

/**
 * The social keys: the viewer's friends, another user's friends, people search and the person profile. Every profile
 * link goes through [openUserProfile], so an ISU outside `1..Int.MAX_VALUE` opens nothing. The schedule's friend
 * picker is the schedule's own ([scheduleEntries]).
 */
internal fun EntryRegistry.Builder.socialEntries() {
    entry<AppRoutes.Friends> { key, navigator ->
        FriendsRoute(
            onOpenProfile = navigator::openUserProfile,
            onOpenSearch = { navigator.open(AppRoutes.UserSearch) },
            onOpenSettings = { navigator.open(AppRoutes.Settings()) },
            onBack = { navigator.close(key) },
            viewModel = entryViewModel(),
        )
    }
    entry<AppRoutes.UserFriends>(
        args = { bundleOf(UserScreenArgs.ISU to it.isu, UserScreenArgs.NAME to it.name) },
    ) { key, navigator ->
        UserFriendsRoute(
            onOpenProfile = navigator::openUserProfile,
            onOpenSettings = { navigator.open(AppRoutes.Settings()) },
            onBack = { navigator.close(key) },
            viewModel = entryViewModel(),
        )
    }
    entry<AppRoutes.UserSearch> { key, navigator -> UserSearchEntry(onBack = { navigator.close(key) }, navigator) }
    entry<AppRoutes.UserProfile>(args = { bundleOf(UserScreenArgs.ISU to it.isu) }) { key, navigator ->
        UserProfileEntry(onBack = { navigator.close(key) }, navigator)
    }
}

/** Opens the person profile of [isu] only when [UserScreenArgs.profileIsu] accepts it. */
internal fun Nav3AppNavigator.openUserProfile(isu: Int) {
    UserScreenArgs.profileIsu(isu.toLong())?.let { open(AppRoutes.UserProfile(it)) }
}

/** People search: `Пригласить` shares the invitation with this distribution's download link. */
@Composable
private fun UserSearchEntry(onBack: () -> Unit, navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    UserSearchRoute(
        onOpenProfile = navigator::openUserProfile,
        onInvite = { context.shareSocialInvitation() },
        onBack = onBack,
        viewModel = entryViewModel(),
    )
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
