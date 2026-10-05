package dev.alllexey.itmowidgets.core.social

import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.result.AppResult

/** A person from the MyITMO directory; [registered] is set when they use ITMO.Widgets. */
data class PersonSearchResult(
    val isu: Int,
    val name: String,
    val pictureUrl: String?,
    val registered: UserProfile?
)

data class PeopleSearchPage(
    val results: List<PersonSearchResult>,
    val total: Int,
    /** Offset of the next page, or `null` when this page was the last one. */
    val nextOffset: Int?
) {
    companion object {
        val EMPTY = PeopleSearchPage(emptyList(), 0, null)
    }
}

/**
 * Searches people by name through MyITMO and marks which of them are registered in
 * ITMO.Widgets. Contact details from the directory are never exposed.
 */
interface PeopleSearchRepository {

    suspend fun search(query: String, offset: Int = 0): AppResult<PeopleSearchPage>
}
