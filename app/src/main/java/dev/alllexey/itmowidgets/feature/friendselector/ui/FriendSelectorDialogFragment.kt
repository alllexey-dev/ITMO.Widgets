package dev.alllexey.itmowidgets.feature.friendselector.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import dev.alllexey.itmowidgets.designsystem.host.ItmoBottomSheetFragment
import dev.alllexey.itmowidgets.designsystem.host.SheetHeight
import dev.alllexey.itmowidgets.designsystem.host.SheetSpec

/**
 * The friend picker (`@id/friend_selector`), kept by name, [TAG] and [newInstance] for the nav graph, the schedule
 * and the tests. The body is `FriendSelectorSheetRoute` from `:shared:feature-social` on the kit sheet host, 90 % of
 * the screen, expanded without a collapsed step; its Koin ViewModel reads [FriendSelectionContract.ARG_SELECTED_ISU]
 * from this Fragment's arguments. The Fragment performs the effects: the profile after closing, the choice as a
 * Fragment result before closing.
 */
@AndroidEntryPoint
class FriendSelectorDialogFragment : ItmoBottomSheetFragment() {

    override val spec: SheetSpec get() = SPEC

    @Composable
    override fun SheetContent() {
        FriendSelectorSheetRoute(
            onDeliver = ::deliver,
            onOpenProfile = ::openProfile,
            onClose = ::onCloseRequest,
            modifier = Modifier.fillMaxSize(),
        )
    }

    /** The profile is a contextual screen; the sheet has nothing to add once it opens. */
    private fun openProfile(user: UserSummary) {
        dismiss()
        openUserProfile(user.isu)
    }

    private fun deliver(target: UserSummary?) {
        parentFragmentManager.setFragmentResult(
            FriendSelectionContract.RESULT_KEY,
            bundleOf(
                FriendSelectionContract.RESULT_USE_MY_SCHEDULE to (target == null),
                FriendSelectionContract.RESULT_USER_ISU to (
                    target?.isu ?: FriendSelectionContract.NO_USER_ISU
                ),
                FriendSelectionContract.RESULT_USER_NAME to target?.name.orEmpty(),
                FriendSelectionContract.RESULT_USER_PICTURE_URL to target?.pictureUrl.orEmpty()
            )
        )
        dismiss()
    }

    companion object {
        const val TAG = "FriendSelectorBottomSheet"

        private val SPEC = SheetSpec(height = SheetHeight.Tall)

        fun newInstance(selectedIsu: Int? = null) = FriendSelectorDialogFragment().apply {
            arguments = bundleOf(
                FriendSelectionContract.ARG_SELECTED_ISU to (
                    selectedIsu ?: FriendSelectionContract.NO_USER_ISU
                )
            )
        }

        fun show(manager: FragmentManager, selectedIsu: Int? = null) {
            newInstance(selectedIsu).show(manager, TAG)
        }
    }
}
