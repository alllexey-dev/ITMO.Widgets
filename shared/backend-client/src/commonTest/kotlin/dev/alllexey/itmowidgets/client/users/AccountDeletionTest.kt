package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.contract.VendoredContract
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.errorEnvelope
import dev.alllexey.itmowidgets.client.support.json
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** `DELETE /api/users/me` ([UsersApi.deleteMe]) against Backend's answers. */
class AccountDeletionTest {

    @Test
    fun deletedAccountReturns() = runSuspend {
        val body = VendoredContract.read("http/users/deleteMyAccount.json")
        val backend = MockBackend { json(HttpStatusCode.OK, body) }

        backend.client.users.deleteMe()
    }

    @Test
    fun staleSignInIsForbiddenWithItsCode() = runSuspend {
        val backend = MockBackend { json(HttpStatusCode.Forbidden, errorEnvelope("recent_sign_in_required")) }

        val error = assertFailsWith<BackendException.Forbidden> { backend.client.users.deleteMe() }

        assertEquals("recent_sign_in_required", error.code)
    }

    @Test
    fun missingCredentialsAreUnauthorized() = runSuspend {
        val body = VendoredContract.read("errors/unauthorized.json")
        val backend = MockBackend { json(HttpStatusCode.Unauthorized, body) }

        assertFailsWith<BackendException.Unauthorized> { backend.client.users.deleteMe() }
    }
}
