package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.social.data.demo.DemoSocial
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * A MyITMO directory profile through MyItmoApi 2.x. The person is missing on the observed HTTP 400 with
 * `error_code=100`, on HTTP 404, on a successful answer without a result and on a result for another ISU;
 * every other failure, another 400 among them, stays an error. The cache is one immutable map that changes only
 * through `update {}`, so the non-suspend [cachedPerson] needs no lock.
 */
class PersonRepositoryImpl(
    private val client: MyItmoClient,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : PersonRepository, SessionDataCleaner {
    private val cache = MutableStateFlow<Map<Int, Person>>(emptyMap())

    override fun cachedPerson(isu: Int): Person? = cache.value[isu]

    override suspend fun person(isu: Int): AppResult<Person> {
        if (demo.isActive()) {
            return DemoSocial.person(isu)?.let { person -> AppResult.Success(person.also { remember(isu, it) }) }
                ?: AppResult.Failure(AppError.NotFound)
        }
        return try {
            withContext(dispatchers.io) {
                val personality = client.personalities.getPersonality(isu.toLong()).result
                    ?.takeIf { it.isu == isu.toLong() }
                    ?: return@withContext AppResult.Failure(AppError.NotFound)
                val person = personality.toPerson(isu)
                remember(isu, person)
                AppResult.Success(person)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (missing: MyItmoException.Api) {
            AppResult.Failure(if (missing.meansMissingPerson()) AppError.NotFound else missing.toAppError())
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override suspend fun clearSessionData() {
        cache.value = emptyMap()
    }

    private fun remember(isu: Int, person: Person) {
        cache.update { it + (isu to person) }
    }

    private fun MyItmoException.Api.meansMissingPerson(): Boolean =
        status == HTTP_NOT_FOUND || (status == HTTP_BAD_REQUEST && errorCode == MISSING_PERSON_CODE)

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
        const val MISSING_PERSON_CODE = 100
    }
}
