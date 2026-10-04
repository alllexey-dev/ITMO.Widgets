package dev.alllexey.itmowidgets.feature.social.data

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.feature.social.data.demo.DemoSocial
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import dev.alllexey.itmowidgets.core.network.requireResult
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
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
            val envelope = ERROR_GSON.fromJson(body, JsonObject::class.java)
            envelope?.get("error_code") == JsonPrimitive(100) && envelope.get("result")?.isJsonNull == true
        } catch (_: JsonParseException) {
            false
        }
    }

    private companion object {
        val ERROR_GSON = GsonBuilder().setStrictness(Strictness.STRICT).create()
    }
}
