package dev.alllexey.itmowidgets.feature.social.data

import api.myitmo.MyItmoApi
import api.myitmo.model.personality.PersonalityMin
import dev.alllexey.itmowidgets.core.network.requireResult
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.core.social.SocialRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * MyITMO owns the name search; Backend only says who is registered. Phone and
 * e-mail fields of the directory response are dropped at this boundary.
 */
class PeopleSearchRepositoryImpl @Inject constructor(
    private val myItmoApi: MyItmoApi,
    private val social: SocialRepository
) : PeopleSearchRepository {

    override suspend fun search(query: String, offset: Int): AppResult<PeopleSearchPage> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return AppResult.Success(PeopleSearchPage.EMPTY)

        val page = try {
            withContext(Dispatchers.IO) {
                myItmoApi.searchPersonalities(PAGE_SIZE, offset, normalized).execute().body().requireResult()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return AppResult.Failure(error.toAppError())
        }

        val people = page.data.orEmpty().mapNotNull(PersonalityMin::toPerson)
        val registered = when (val lookup = social.lookup(people.map { it.isu })) {
            is AppResult.Success -> lookup.value.associateBy { it.isu }
            is AppResult.Failure -> return lookup
        }
        val results = people.map { person -> person.copy(registered = registered[person.isu]) }
        val loaded = offset + page.data.orEmpty().size
        val nextOffset = loaded.takeIf { page.data.orEmpty().isNotEmpty() && it < page.count }
        return AppResult.Success(PeopleSearchPage(results, page.count, nextOffset))
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

private fun PersonalityMin.toPerson(): PersonSearchResult? {
    val isu = id.takeIf { it in 1..Int.MAX_VALUE }?.toInt() ?: return null
    val name = fio?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return PersonSearchResult(
        isu = isu,
        name = name,
        pictureUrl = photoUrl?.trim()?.takeIf(String::isNotEmpty),
        registered = null
    )
}
