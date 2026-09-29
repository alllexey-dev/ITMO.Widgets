package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.primaryGroup
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.feature.social.domain.model.Person

sealed interface ProfilePart<out T> {
    data object Loading : ProfilePart<Nothing>
    data class Ready<T>(val value: T) : ProfilePart<T>
    data object Absent : ProfilePart<Nothing>
    data class Failed(val error: AppError) : ProfilePart<Nothing>
}

data class SocialBlock(val profile: UserProfile, val isSelf: Boolean, val busy: Boolean)

sealed interface UserProfileUiState {
    data object Loading : UserProfileUiState
    data class Error(val error: AppError) : UserProfileUiState
    data class Content(
        val isu: Int,
        val name: String,
        val pictureUrl: String?,
        val headline: ProfileHeadline?,
        /** Ends with the ISU number. */
        val facts: List<ProfileFact>,
        val social: SocialBlock?,
        val reviews: ProfileReviews?
    ) : UserProfileUiState
}

fun userProfileUiState(
    isu: Int,
    person: ProfilePart<Person>,
    social: ProfilePart<SocialBlock>,
    reviews: ProfilePart<TeacherReviews>,
    busyId: String? = null
): UserProfileUiState {
    if (person is ProfilePart.Loading || social is ProfilePart.Loading || reviews is ProfilePart.Loading) {
        return UserProfileUiState.Loading
    }
    val personality = (person as? ProfilePart.Ready)?.value
    val block = (social as? ProfilePart.Ready)?.value
    if (personality != null || block != null) {
        val user = block?.profile?.user
        val group = user?.primaryGroup()
        return UserProfileUiState.Content(
            isu = isu,
            name = personality?.name ?: checkNotNull(user).name,
            pictureUrl = if (personality != null) personality.photoUrl else user?.pictureUrl,
            headline = profileHeadline(personality, if (personality == null) group else null),
            facts = profileFacts(personality, if (personality == null) group else null) + isuFact(isu),
            social = block,
            reviews = profileReviews((reviews as? ProfilePart.Ready)?.value, personality, busyId)
        )
    }
    val error = listOfNotNull((person as? ProfilePart.Failed)?.error, (social as? ProfilePart.Failed)?.error)
        .firstOrNull { it != AppError.NotFound } ?: AppError.NotFound
    return UserProfileUiState.Error(error)
}
