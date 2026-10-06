package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmoapi.core.requireResult
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmoapi.myitmo.personalities.PersonalityMin
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.social.data.demo.DemoSocial
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * MyITMO owns the name search; Backend only says who is registered. Phone and
 * e-mail fields of the directory response are dropped at this boundary.
 */
class PeopleSearchRepositoryImpl(
    private val client: MyItmoClient,
    private val social: SocialRepository,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : PeopleSearchRepository {

    override suspend fun search(query: String, offset: Int): AppResult<PeopleSearchPage> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return AppResult.Success(PeopleSearchPage.EMPTY)
        if (demo.isActive()) {
            val found = DemoSocial.search(normalized)
            return AppResult.Success(PeopleSearchPage(found.drop(offset), found.size, null))
        }

        val page = try {
            withContext(dispatchers.io) {
                client.personalities.searchPersonalities(PAGE_SIZE, offset, normalized).requireResult()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return AppResult.Failure(error.toAppError())
        }

        val people = page.data.mapNotNull(PersonalityMin::toPerson)
        val registered = when (val lookup = social.lookup(people.map { it.isu })) {
            is AppResult.Success -> lookup.value.associateBy { it.isu }
            is AppResult.Failure -> return lookup
        }
        val results = people.map { person -> person.copy(registered = registered[person.isu]) }
        val loaded = offset + page.data.size
        val nextOffset = loaded.takeIf { page.data.isNotEmpty() && it < page.count }
        return AppResult.Success(PeopleSearchPage(results, page.count, nextOffset))
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

private fun PersonalityMin.toPerson(): PersonSearchResult? {
    val isu = id.takeIf { it in 1..Int.MAX_VALUE }?.toInt() ?: return null
    val name = fio.clean() ?: return null
    return PersonSearchResult(
        isu = isu,
        name = name,
        pictureUrl = photoUrl.clean(),
        registered = null
    )
}
