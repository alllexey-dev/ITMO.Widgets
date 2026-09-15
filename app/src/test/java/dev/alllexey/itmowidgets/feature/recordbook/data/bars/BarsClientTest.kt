package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import java.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class BarsClientTest {
    private val old = "Bearer synthetic-old-credential"
    private val fresh = "Bearer synthetic-rotated-credential"
    private class Memory : BarsTokenPersistence {
        var value: String? = null
        override fun read() = value
        override fun write(value: String?) { this.value = value }
    }
    private val memory = Memory()
    private val store = BarsTokenStore(memory, object : TokenCipher {
        override fun encrypt(value: String) = Base64.getEncoder().encodeToString(value.toByteArray())
        override fun decrypt(value: String) = String(Base64.getDecoder().decode(value))
    })
    private val api = Api()
    private val owner = object : CurrentUserProvider {
        override suspend fun getCurrentUser() = CurrentUser(123, null, null)
    }
    private var silentCodes = mutableListOf<String?>()
    private val silentLogin = object : BarsSilentLogin {
        var requests = 0
        override suspend fun authorizationCode(): String? { requests++; return silentCodes.removeFirstOrNull() }
    }
    private val client get() = BarsClient(api, store, owner, silentLogin)

    @Test fun `token file is account-bound cleared and guarded against stale rotation`() {
        store.install(123, old)
        assertEquals(old, store.load(123))
        assertNull(store.load(999))
        assertFalse(memory.value!!.contains(old))
        val generation = store.generation
        store.clear()
        assertNull(store.load(123))
        assertThrows(IllegalStateException::class.java) { store.rotate(123, fresh, generation) }
    }
    @Test fun `changed response header is stored as the new bearer`() = runTest {
        store.install(123, old)
        api.rotation = fresh
        val result = client.account { call { api.disciplines(it) } }
        assertTrue(result is AppResult.Success)
        assertEquals(fresh, store.load(123))
    }
    @Test fun `missing session signs in silently before the first request`() = runTest {
        silentCodes += "synthetic-code"
        assertTrue(client.account { call { api.disciplines(it) } } is AppResult.Success)
        assertEquals(old, api.disciplineCredential)
        assertEquals(1, silentLogin.requests)
    }
    @Test fun `ended ITMO ID session never sends requests or uses MyITMO credentials`() = runTest {
        assertEquals(AppResult.Failure(AppError.Unauthorized), client.account { Unit })
        assertEquals(1, silentLogin.requests)
        assertEquals(0, api.userRequests)
        assertNull(store.load(123))
    }
    @Test fun `expired token is renewed once and the request retried`() = runTest {
        store.install(123, "Bearer synthetic-expired-credential")
        api.rejected = "Bearer synthetic-expired-credential"
        silentCodes += "synthetic-code"
        assertTrue(client.account { call { api.disciplines(it) } } is AppResult.Success)
        assertEquals(old, api.disciplineCredential)
        assertEquals(old, store.load(123))
        assertEquals(1, silentLogin.requests)
        api.rejected = old
        assertEquals(AppResult.Failure(AppError.Unauthorized), client.account { call { api.disciplines(it) } })
        assertEquals(2, silentLogin.requests)
    }
    @Test fun `concurrent calls share a single renewal`() = runTest {
        store.install(123, "Bearer synthetic-expired-credential")
        api.rejected = "Bearer synthetic-expired-credential"
        silentCodes += "synthetic-code"
        val result = client.account {
            coroutineScope { List(4) { async { call { api.disciplines(it) } } }.awaitAll() }
        }
        assertTrue(result is AppResult.Success)
        assertEquals(1, silentLogin.requests)
        assertEquals(old, store.load(123))
    }
    @Test fun `changed-account response clears the session`() = runTest {
        store.install(123, old)
        api.user = api.user.copy(login = "999")
        assertEquals(AppResult.Failure(AppError.Unauthorized), client.account { Unit })
        assertNull(store.load(123))
    }
    @Test fun `period uses spring zero, verifies the write and skips it when already selected`() = runTest {
        store.install(123, old)
        val result = client.account { selectPeriod("2025/2026", autumn = false) }
        assertTrue(result is AppResult.Success)
        assertEquals(listOf(BarsSetting("current_year", "2025/2026"), BarsSetting("current_term", "0")), api.settings)
        assertEquals(0, api.user.term)
        val requests = api.userRequests
        assertTrue(client.account { selectPeriod("2025/2026", autumn = false) } is AppResult.Success)
        assertEquals(2, api.settings.size)
        assertEquals(requests + 1, api.userRequests)
    }
    @Test fun `failed period update is not treated as successful selected data`() = runTest {
        store.install(123, old)
        api.ignoreWrites = true
        assertTrue(client.account { selectPeriod("2025/2026", autumn = false) } is AppResult.Failure)
    }
    @Test fun `login checks server identity before installing a token`() = runTest {
        api.user = api.user.copy(login = "999")
        assertEquals(AppResult.Failure(AppError.Forbidden), client.login("synthetic-code"))
        assertNull(store.load(123))
        api.user = api.user.copy(login = "123")
        assertTrue(client.login("synthetic-code") is AppResult.Success)
        assertEquals(old, store.load(123))
    }

    private inner class Api : BarsApi {
        var user = BarsUser("123", "2026/2027", 1)
        var rejected: String? = null
        var rotation: String? = null
        var userRequests = 0
        var ignoreWrites = false
        var disciplineCredential: String? = null
        val settings = mutableListOf<BarsSetting>()
        private fun <T> unauthorized(): Response<T> = Response.error(401, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun login(code: String, redirect: String) = Response.success(Unit, Headers.headersOf("authorization", old))
        override suspend fun currentUser(token: String): Response<BarsUser> {
            userRequests++
            if (token == rejected) return unauthorized()
            return Response.success(user, rotation?.let { Headers.headersOf("authorization", it) } ?: Headers.headersOf())
        }
        override suspend fun selectPeriod(token: String, setting: BarsSetting): Response<BarsSetting> {
            if (token == rejected) return unauthorized()
            settings += setting
            if (!ignoreWrites) user = if (setting.name == "current_year") user.copy(year = setting.value) else user.copy(term = setting.value.toInt())
            return Response.success(setting)
        }
        override suspend fun disciplines(token: String, withPlans: Boolean): Response<List<BarsDiscipline>> {
            if (token == rejected) return unauthorized()
            disciplineCredential = token
            return Response.success(emptyList())
        }
        override suspend fun groupsAndFlows(token: String): Response<List<BarsGroupOrFlow>> = Response.success(emptyList())
        override suspend fun journal(token: String, plan: Long, type: String, identifier: String): Response<BarsJournal> = error("Not used")
    }
}
