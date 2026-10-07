package dev.alllexey.itmowidgets.feature.social.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileViewModel
import dev.alllexey.itmowidgets.feature.social.ui.profile.UserProfileExits
import dev.alllexey.itmowidgets.feature.social.ui.profile.UserProfileRoute
import dev.alllexey.itmowidgets.feature.social.ui.userfriends.UserFriendsRoute
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

/**
 * The person profile as the iOS shell hosts it: [UserProfileRoute] with its ViewModel reading [isu] from the
 * arguments Android's destination gives it. [reviewsEnabled] is the platform's `PlatformCapabilities.reviews`.
 */
@Composable
fun UserProfileIosRoute(isu: Int, exits: UserProfileExits, reviewsEnabled: Boolean) {
    val viewModel = hostedViewModel<UserProfileViewModel>(UserScreenArgs.ISU to isu)
    UserProfileRoute(exits, reviewsEnabled, viewModel)
}

/** Another user's friends as the iOS shell hosts it: [UserFriendsRoute] for the owner [isu] named [name]. */
@Composable
fun UserFriendsIosRoute(
    isu: Int,
    name: String,
    onOpenProfile: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = hostedViewModel<UserFriendsViewModel>(UserScreenArgs.ISU to isu, UserScreenArgs.NAME to name)
    UserFriendsRoute(onOpenProfile, onOpenSettings, onBack, viewModel)
}

/**
 * Koin's definition of [VM] in the hosting controller's store with [arguments] as its `SavedStateHandle`, as Android's
 * shell seeds an entry's handle from its key. `koinViewModel()` would hand the definition the store's own empty
 * handle instead, since a Compose controller on iOS has no destination arguments.
 */
@Composable
private inline fun <reified VM : ViewModel> hostedViewModel(vararg arguments: Pair<String, Any>): VM {
    val koin = getKoin()
    val handle = remember { SavedStateHandle(mapOf(*arguments)) }
    return viewModel { koin.get<VM> { parametersOf(handle) } }
}
