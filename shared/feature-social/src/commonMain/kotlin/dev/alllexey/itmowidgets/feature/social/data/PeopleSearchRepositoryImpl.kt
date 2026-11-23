package dev.alllexey.itmowidgets.feature.social.data

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
 * MyITMO tells `е` from `ё`, so a query that finds nobody is asked again with `ё` in place of an `е`
 * ([yoVariants]); the first variant with hits answers. The choice depends only on the hit count, so every page of
 * one query lands on the same variant.
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
                var page = client.personalities.searchPersonalities(PAGE_SIZE, offset, normalized)
                for (variant in yoVariants(normalized)) {
                    if (page.count > 0) break
                    page = client.personalities.searchPersonalities(PAGE_SIZE, offset, variant)
                }
                page
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

/**
 * The spellings of [query] to try when it finds nobody: a typed `ё` read as `е` first, then `ё` in place of an `е`,
 * fewest first and at most one per word (`ё` is always stressed), left to right. The query itself is left out; at
 * most [MAX_YO_VARIANTS], so a query costs at most that many extra requests.
 */
internal fun yoVariants(query: String): List<String> {
    val base = query.replace('ё', 'е').replace('Ё', 'Е')
    val words = mutableListOf<MutableList<Int>>()
    var start = true
    base.forEachIndexed { index, char ->
        if (char.isWhitespace()) start = true else {
            if (start) words += mutableListOf<Int>()
            start = false
            if (char == 'е' || char == 'Е') words.last() += index
        }
    }
    val spots = words.filter(List<Int>::isNotEmpty)
    val found = linkedSetOf(base)
    val chars = base.toCharArray()
    for (count in 1..spots.size) placeYo(chars, spots, 0, count, found)
    return (found - query).take(MAX_YO_VARIANTS)
}

/** Adds [chars] with `ё` at [left] more spots, each in its own word from [from] on, until [found] is full. */
private fun placeYo(chars: CharArray, spots: List<List<Int>>, from: Int, left: Int, found: MutableSet<String>) {
    if (left == 0) {
        found += chars.concatToString()
        return
    }
    for (word in from until spots.size) for (index in spots[word]) {
        if (found.size > MAX_YO_VARIANTS) return
        val char = chars[index]
        chars[index] = if (char == 'Е') 'Ё' else 'ё'
        placeYo(chars, spots, word + 1, left - 1, found)
        chars[index] = char
    }
}

private const val MAX_YO_VARIANTS = 6
