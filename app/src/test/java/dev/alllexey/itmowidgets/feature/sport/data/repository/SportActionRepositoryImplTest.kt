package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.myItmoStub
import dev.alllexey.itmowidgets.core.testing.noDemo
import java.lang.reflect.Proxy
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

    private val calls = mutableListOf<String>()
    private val backend = Proxy.newProxyInstance(ItmoWidgetsApi::class.java.classLoader, arrayOf(ItmoWidgetsApi::class.java)) { _, method, _ ->
        calls += method.name
        ApiResponse.success("OK")
    } as ItmoWidgetsApi
    private val gate = FakeBackendGate(optedIn = false)
    private val actions = SportActionRepositoryImpl(gate, myItmoStub { error("My ITMO is not asked here") }.api, backend, noDemo(), dispatchers)

    @Test
    fun `without the opt-in the queues are off and never reach Backend`() = runTest {
        val disabled = AppResult.Failure(AppError.CustomServicesDisabled)

        assertFalse(actions.areCommunityServicesEnabled())
        assertEquals(disabled, actions.createFreeSignEntry(1, forceSign = false))
        assertEquals(disabled, actions.cancelFreeSignEntry(1))
        assertEquals(disabled, actions.createAutoSignEntry(1))
        assertEquals(disabled, actions.cancelAutoSignEntry(1))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `with the opt-in every queue action goes to Backend`() = runTest {
        gate.optedIn.value = true

        assertTrue(actions.areCommunityServicesEnabled())
        assertEquals(AppResult.Success(Unit), actions.createFreeSignEntry(1, forceSign = false))
        assertEquals(AppResult.Success(Unit), actions.cancelFreeSignEntry(1))
        assertEquals(AppResult.Success(Unit), actions.createAutoSignEntry(1))
        assertEquals(AppResult.Success(Unit), actions.cancelAutoSignEntry(1))
        assertEquals(
            listOf("createSportFreeSignEntry", "cancelSportFreeSignEntry", "createSportAutoSignEntry", "cancelSportAutoSignEntry"),
            calls
        )
    }
}
