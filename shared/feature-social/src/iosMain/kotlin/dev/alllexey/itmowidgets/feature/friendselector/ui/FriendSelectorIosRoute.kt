package dev.alllexey.itmowidgets.feature.friendselector.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorViewModel
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

/**
 * The schedule's friend picker as the iOS shell hosts it: [FriendSelectorSheetRoute] with its ViewModel reading
 * [selectedIsu] (`FriendSelectionContract.NO_USER_ISU` for the own schedule) from the arguments the Android sheet
 * gets. `koinViewModel()` would hand the definition the hosting controller's empty handle instead.
 */
@Composable
fun FriendSelectorIosRoute(
    selectedIsu: Int,
    onDeliver: (UserSummary?) -> Unit,
    onOpenProfile: (UserSummary) -> Unit,
    onClose: () -> Unit,
) {
    val koin = getKoin()
    val handle = remember { SavedStateHandle(mapOf(FriendSelectionContract.ARG_SELECTED_ISU to selectedIsu)) }
    val viewModel = viewModel { koin.get<FriendSelectorViewModel> { parametersOf(handle) } }
    FriendSelectorSheetRoute(onDeliver, onOpenProfile, onClose, Modifier.fillMaxSize(), viewModel)
}
