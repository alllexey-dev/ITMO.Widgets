package dev.alllexey.itmowidgets.feature.social.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.ui.profile.preview.UserProfilePreviewData

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LC-1c recorded the XML references as
 * `UserProfileScreen_<state>`. Each state therefore is a function called `UserProfileScreen` in a holder class of its
 * own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun UserProfilePreview(state: UserProfileUiState) = ItmoPreview {
    UserProfileScreen(state, UserProfileActions())
}

/** Tall enough for the AI summary and every review under the facts, as LC-1c's reference. */
internal class UserProfileScreenTeacherSummaryPreview {
    @Preview(name = "teacher-summary", heightDp = 1800)
    @Composable
    fun UserProfileScreen() = UserProfilePreview(UserProfilePreviewData.teacherPage)
}

internal class UserProfileScreenFriendPreview {
    @Preview(name = "friend")
    @Composable
    fun UserProfileScreen() = UserProfilePreview(UserProfilePreviewData.friendPage)
}

internal class UserProfileScreenSelfPreview {
    @Preview(name = "self")
    @Composable
    fun UserProfileScreen() = UserProfilePreview(UserProfilePreviewData.selfPage)
}

internal class UserProfileScreenNoFactsPreview {
    @Preview(name = "no-facts")
    @Composable
    fun UserProfileScreen() = UserProfilePreview(UserProfilePreviewData.noFactsPage)
}

/** Not a friend and nothing shared: `Добавить в друзья`, locked rows and the privacy hint. */
internal class UserProfileScreenNonePreview {
    @Preview(name = "none")
    @Composable
    fun UserProfileScreen() = UserProfilePreview(
        UserProfilePreviewData.studentPage(RelationshipState.NONE, UserProfilePreviewData.closedSharing),
    )
}

internal class UserProfileScreenOutgoingPreview {
    @Preview(name = "outgoing")
    @Composable
    fun UserProfileScreen() = UserProfilePreview(
        UserProfilePreviewData.studentPage(RelationshipState.OUTGOING, UserProfilePreviewData.closedSharing),
    )
}

internal class UserProfileScreenIncomingPreview {
    @Preview(name = "incoming")
    @Composable
    fun UserProfileScreen() = UserProfilePreview(
        UserProfilePreviewData.studentPage(RelationshipState.INCOMING, UserProfilePreviewData.closedSharing),
    )
}

/** A request in flight: both buttons ignore taps. */
internal class UserProfileScreenIncomingBusyPreview {
    @Preview(name = "incoming-busy")
    @Composable
    fun UserProfileScreen() = UserProfilePreview(
        UserProfilePreviewData.studentPage(RelationshipState.INCOMING, UserProfilePreviewData.closedSharing, busy = true),
    )
}

internal class UserProfileScreenBlockedPreview {
    @Preview(name = "blocked")
    @Composable
    fun UserProfileScreen() = UserProfilePreview(
        UserProfilePreviewData.studentPage(RelationshipState.BLOCKED, UserProfilePreviewData.closedSharing),
    )
}

/** Custom services off: no friendship, no `ITMO.Widgets`, no reviews. */
internal class UserProfileScreenDisabledPreview {
    @Preview(name = "disabled")
    @Composable
    fun UserProfileScreen() = UserProfilePreview(UserProfilePreviewData.disabledPage)
}

internal class UserProfileScreenSkeletonPreview {
    @Preview(name = "skeleton")
    @Composable
    fun UserProfileScreen() = UserProfilePreview(UserProfileUiState.Loading)
}

internal class UserProfileScreenNotFoundPreview {
    @Preview(name = "not-found")
    @Composable
    fun UserProfileScreen() = UserProfilePreview(UserProfileUiState.Error(AppError.NotFound))
}

internal class UserProfileScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun UserProfileScreen() = UserProfilePreview(UserProfileUiState.Error(AppError.Network))
}
