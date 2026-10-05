package dev.alllexey.itmowidgets.feature.social.data

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.feature.social.data.demo.DemoSocial
import dev.alllexey.itmowidgets.core.network.requireResult
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import retrofit2.HttpException
import retrofit2.Response
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonRepositoryImpl @Inject constructor(
    private val myItmoApi: MyItmoApi,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : PersonRepository, SessionDataCleaner {
    private val cache = ConcurrentHashMap<Int, Person>()

    override fun cachedPerson(isu: Int): Person? = cache[isu]

    override suspend fun person(isu: Int): AppResult<Person> = if (demo.isActive()) {
        DemoSocial.person(isu)?.let { person -> AppResult.Success(person.also { cache[isu] = it }) }
            ?: AppResult.Failure(AppError.NotFound)
    } else try {
        withContext(dispatchers.io) {
            val response = myItmoApi.getPersonality(isu).execute()
            if (response.isMissingPerson()) return@withContext AppResult.Failure(AppError.NotFound)
            if (!response.isSuccessful) throw HttpException(response)
            val body = response.body()
            if (body?.errorCode == 0 && body.result == null) return@withContext AppResult.Failure(AppError.NotFound)
            val personality = body.requireResult()
            if (personality.isu != isu.toLong()) return@withContext AppResult.Failure(AppError.NotFound)
            val person = personality.toPerson(isu)
            cache[isu] = person
            AppResult.Success(person)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        AppResult.Failure(error.toAppError())
    }

    override suspend fun clearSessionData() {
        cache.clear()
    }

    /** Only this endpoint's observed 400/100/null means missing; other bad requests stay failures. */
    private fun Response<*>.isMissingPerson(): Boolean {
        if (code() != 400) return false
        val body = errorBody()?.use { it.string() } ?: return false
        return try {
            val envelope = Json.parseToJsonElement(body) as? JsonObject ?: return false
            // A number equal to 100 (100.0 too, as 2.2 compared it), never the string "100".
            val errorCode = (envelope["error_code"] as? JsonPrimitive)?.takeUnless { it.isString || it is JsonNull }
            errorCode?.doubleOrNull == MISSING_PERSON_CODE && envelope["result"] is JsonNull
        } catch (_: SerializationException) {
            false
        }
    }

    private companion object {
        const val MISSING_PERSON_CODE = 100.0
    }
}
