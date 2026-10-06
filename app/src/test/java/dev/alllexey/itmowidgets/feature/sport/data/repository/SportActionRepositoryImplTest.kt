package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.network.Core2Harness
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.contractFixture
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.testkit.respondJson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SportActionRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val clients = Core2Harness(Core2Harness.session()) { request ->
        check(request.url.host != "my.itmo.ru") { "My ITMO is not asked here" }
        respondJson(contractFixture(ROUTES.getValue(request.url.encodedPath)))
    }
    private val gate = FakeBackendGate(optedIn = false)
    private val actions = SportActionRepositoryImpl(gate, clients.myItmo, clients.client.sport, noDemo(), dispatchers)

    @Test
    fun `without the opt-in the queues are off and never reach Backend`() = runTest {
        val disabled = AppResult.Failure(AppError.CustomServicesDisabled)

        assertFalse(actions.areCommunityServicesEnabled())
        assertEquals(disabled, actions.createFreeSignEntry(1, forceSign = false))
        assertEquals(disabled, actions.cancelFreeSignEntry(1))
        assertEquals(disabled, actions.createAutoSignEntry(1))
        assertEquals(disabled, actions.cancelAutoSignEntry(1))
        assertTrue(clients.requests.isEmpty())
    }

    @Test
    fun `with the opt-in every queue action goes to Backend`() = runTest {
        gate.optedIn.value = true

        assertTrue(actions.areCommunityServicesEnabled())
        assertEquals(AppResult.Success(Unit), actions.createFreeSignEntry(1, forceSign = false))
        assertEquals(AppResult.Success(Unit), actions.cancelFreeSignEntry(1))
        assertEquals(AppResult.Success(Unit), actions.createAutoSignEntry(1))
        assertEquals(AppResult.Success(Unit), actions.cancelAutoSignEntry(1))
        assertEquals(ROUTES.keys.toList(), clients.backendRequests.map { it.url.encodedPath })
    }

    @Test
    fun `a rejected queue action is a failure, not a success`() = runTest {
        gate.optedIn.value = true
        val rejecting = Core2Harness(Core2Harness.session()) {
            respondJson(Core2Harness.errorEnvelope("invalid_request"), io.ktor.http.HttpStatusCode.BadRequest)
        }
        val actions = SportActionRepositoryImpl(gate, rejecting.myItmo, rejecting.client.sport, noDemo(), dispatchers)

        assertTrue(actions.createFreeSignEntry(1, forceSign = true) is AppResult.Failure)
    }

    private companion object {
        val ROUTES = linkedMapOf(
            "/api/sport/free-sign/entry/create" to "http/sport-free-sign/createSportFreeSignEntry.json",
            "/api/sport/free-sign/entry/1/cancel" to "http/sport-free-sign/cancelSportFreeSignEntry.json",
            "/api/sport/auto-sign/entry/create" to "http/sport-auto-sign/createSportAutoSignEntry.json",
            "/api/sport/auto-sign/entry/1/cancel" to "http/sport-auto-sign/cancelSportAutoSignEntry.json"
        )
    }
}
