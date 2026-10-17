package dev.alllexey.itmowidgets.feature.social.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.ui.preview.SocialSamples

/*
 * The person card at the design lab's axes (who, relation, teaches). The harness names a baseline
 * `<function>_<@Preview name>`, so each state is a function called `UserProfileScreen` in a holder class of its own.
 */

@Composable
private fun CardPreview(state: UserProfileUiState) = ItmoPreview { UserProfileScreen(state, UserProfileActions()) }

internal class UserProfileScreenStudentPreview {
    @Preview(name = "student", heightDp = 900)
    @Composable
    fun UserProfileScreen() = CardPreview(SocialSamples.card("app", "none"))
}

internal class UserProfileScreenFriendPreview {
    @Preview(name = "friend", heightDp = 900)
    @Composable
    fun UserProfileScreen() = CardPreview(SocialSamples.card("app", "friend"))
}

internal class UserProfileScreenNoAppPreview {
    @Preview(name = "no-app", heightDp = 900)
    @Composable
    fun UserProfileScreen() = CardPreview(SocialSamples.card("noapp"))
}

internal class UserProfileScreenTeacherPreview {
    @Preview(name = "teacher", heightDp = 1600)
    @Composable
    fun UserProfileScreen() = CardPreview(SocialSamples.card("teacher", teaches = "no"))
}

internal class UserProfileScreenTeachesYouPreview {
    @Preview(name = "teaches-you", heightDp = 1600)
    @Composable
    fun UserProfileScreen() = CardPreview(SocialSamples.card("teacher", teaches = "yes"))
}

internal class UserProfileScreenBothPreview {
    @Preview(name = "both", heightDp = 1400)
    @Composable
    fun UserProfileScreen() = CardPreview(SocialSamples.card("both", "friend", "yes"))
}

internal class UserProfileScreenIncomingPreview {
    @Preview(name = "incoming", heightDp = 900)
    @Composable
    fun UserProfileScreen() = CardPreview(SocialSamples.incomingCard)
}

internal class UserProfileScreenSelfPreview {
    @Preview(name = "self", heightDp = 900)
    @Composable
    fun UserProfileScreen() = CardPreview(SocialSamples.ownCard)
}

internal class UserProfileScreenNotFoundPreview {
    @Preview(name = "not-found")
    @Composable
    fun UserProfileScreen() = CardPreview(SocialSamples.notFound)
}

internal class UserProfileScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun UserProfileScreen() = CardPreview(SocialSamples.failed)
}
