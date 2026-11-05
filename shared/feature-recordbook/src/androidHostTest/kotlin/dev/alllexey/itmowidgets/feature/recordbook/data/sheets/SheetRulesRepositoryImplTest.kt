package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.sheets.SheetRule as WireRule
import dev.alllexey.itmowidgets.client.sheets.SheetRuleCell
import dev.alllexey.itmowidgets.client.sheets.SheetRuleIdentity
import dev.alllexey.itmowidgets.client.sheets.SheetRuleNode as WireNode
import dev.alllexey.itmowidgets.client.sheets.SheetRuleResponse
import dev.alllexey.itmowidgets.client.sheets.SheetRuleRoute as WireRoute
import dev.alllexey.itmowidgets.client.sheets.SheetRuleStatus
import dev.alllexey.itmowidgets.client.sheets.SheetRulesApi
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.recordbook.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.data.demo.DemoRecordbook
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.BlankPolicy
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetOp
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRuleNode
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRulePurpose
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRuleResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRuleTab
import java.io.IOException
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SheetRulesRepositoryImplTest {
    @get:Rule val main = MainDispatcherRule()
    private val linkId = "00000000-0000-0000-0000-000000000042"
    private val calls = mutableListOf<String>()
    private var answer: () -> SheetRuleResponse = { response(SheetRuleStatus.PENDING) }

    private val api = object : SheetRulesApi {
        override suspend fun sheetRule(linkId: Uuid): SheetRuleResponse = answer().also { calls += "GET $linkId" }
        override suspend fun requestSheetRule(linkId: Uuid): SheetRuleResponse = answer().also { calls += "POST $linkId" }
    }

    private fun repository(optedIn: Boolean = true, demo: Boolean = false): SheetRulesRepositoryImpl {
        val mode = FakeDemoMode(active = demo)
        return SheetRulesRepositoryImpl(api, FakeBackendGate(optedIn, mode), mode, main.appDispatchers)
    }

    private fun response(status: SheetRuleStatus, rule: JsonObject? = null, reason: String? = null) =
        SheetRuleResponse(status, rule, null, reason)

    private val wire = WireRule(
        schemaVersion = 1,
        decision = "mixed",
        summary = "Сумма работ",
        tabs = emptyList(),
        routes = listOf(
            route("r1", "current_accumulated", "compute", calculation = WireNode(
                "sum",
                args = listOf(
                    WireNode("cell", sheet = "Группа 1", gid = "0", column = "D"),
                    WireNode("round", arg = WireNode("cell", sheet = "Группа 1", gid = "0", column = "AB"), digits = 1),
                    WireNode("number", value = 0.5),
                ),
            )),
            route("r2", "official_total", "direct", source = SheetRuleCell("Группа 1", null, "R"), blank = "preserve"),
            route("r3", "bonus_points", "direct", source = SheetRuleCell("Группа 1", "0", "S")),
            route("r4", "current_accumulated", "compute", calculation = WireNode("power", args = listOf(WireNode("number", value = 2.0)))),
            route("r5", "current_accumulated", "compute", calculation = WireNode("divide", args = listOf(WireNode("number", value = 2.0)))),
        ),
    )

    private fun route(
        id: String,
        purpose: String,
        kind: String,
        source: SheetRuleCell? = null,
        calculation: WireNode? = null,
        blank: String = "ignore_blank_inputs",
    ) = WireRoute(
        id, purpose, kind, "Группа 1", "0", listOf(SheetRuleIdentity("Группа 1", "0", "B", null, 4)), source, calculation,
        blank, "Пояснение $id", reviewRequired = true, warnings = listOf("Предупреждение $id"),
    )

    private fun json(rule: WireRule): JsonObject =
        Json.parseToJsonElement(Json.encodeToString(WireRule.serializer(), rule)).jsonObject

    @Test fun `a ready rule keeps the routes the device can apply`() = runTest(main.dispatcher) {
        answer = { response(SheetRuleStatus.READY, json(wire)) }

        val rule = (repository().request(linkId) as SheetRuleResult.Ready).rule

        assertEquals(listOf("POST $linkId"), calls)
        assertEquals("Сумма работ", rule.summary)
        assertEquals(listOf(SheetRulePurpose.CURRENT, SheetRulePurpose.OFFICIAL), rule.routes.map { it.purpose })
        val tab = SheetRuleTab("Группа 1", 0)
        assertEquals(
            SheetRuleNode.Apply(
                SheetOp.SUM,
                listOf(SheetRuleNode.Cell(tab, 3), SheetRuleNode.Round(SheetRuleNode.Cell(tab, 27), 1), SheetRuleNode.Number(0.5)),
            ),
            rule.routes[0].node,
        )
        assertEquals(BlankPolicy.IGNORE_BLANK_INPUTS, rule.routes[0].blanks)
        val official = rule.routes[1]
        assertTrue(official.direct)
        assertEquals(SheetRuleNode.Cell(SheetRuleTab("Группа 1", null), 17), official.node)
        assertEquals(BlankPolicy.PRESERVE, official.blanks)
        assertEquals(listOf("Предупреждение r2"), official.warnings)
    }

    @Test fun `states map to results and a stale rule stays usable while it is checked`() = runTest(main.dispatcher) {
        val cases = listOf(
            response(SheetRuleStatus.NONE) to SheetRuleResult.Pending,
            response(SheetRuleStatus.PENDING) to SheetRuleResult.Pending,
            response(SheetRuleStatus.RUNNING) to SheetRuleResult.Pending,
            response(SheetRuleStatus.INSUFFICIENT, reason = "insufficient") to SheetRuleResult.Insufficient,
            response(SheetRuleStatus.FAILED, reason = "closed") to SheetRuleResult.Failed,
            response(SheetRuleStatus.UNKNOWN) to SheetRuleResult.Failed,
            response(SheetRuleStatus.READY) to SheetRuleResult.Failed,
            response(SheetRuleStatus.READY, JsonObject(mapOf("broken" to Json.parseToJsonElement("1")))) to SheetRuleResult.Failed,
        )
        for ((wireAnswer, expected) in cases) {
            answer = { wireAnswer }
            assertEquals(wireAnswer.status.name, expected, repository().state(linkId))
        }
        answer = { response(SheetRuleStatus.RUNNING, json(wire)) }
        assertTrue(repository().state(linkId) is SheetRuleResult.Ready)
    }

    @Test fun `a rule with no applicable route is a failure`() = runTest(main.dispatcher) {
        answer = { response(SheetRuleStatus.READY, json(wire.copy(routes = wire.routes.drop(2)))) }

        assertEquals(SheetRuleResult.Failed, repository().request(linkId))
    }

    @Test fun `errors say what happened`() = runTest(main.dispatcher) {
        val cases = listOf(
            BackendException.NotFound("not_found") to SheetRuleResult.Unavailable,
            BackendException.Http(409, "business_rule_violation") to SheetRuleResult.Unavailable,
            BackendException.Http(429, "rate_limited") to SheetRuleResult.RateLimited,
            BackendException.Transport(IOException("offline")) to SheetRuleResult.Offline,
            BackendException.Unauthorized() to SheetRuleResult.Failed,
        )
        for ((error, expected) in cases) {
            answer = { throw error }
            assertEquals(error.message, expected, repository().request(linkId))
        }
    }

    @Test fun `without the services opt-in nothing is offered or called`() = runTest(main.dispatcher) {
        val repository = repository(optedIn = false)

        assertFalse(repository.available())
        assertEquals(SheetRuleResult.Unavailable, repository.request(linkId))
        assertEquals(SheetRuleResult.Unavailable, repository.state(linkId))
        assertTrue(calls.isEmpty())
    }

    @Test fun `the demo answers its fixture rule and never calls Backend`() = runTest(main.dispatcher) {
        val repository = repository(optedIn = true, demo = true)

        assertTrue(repository.available())
        assertEquals(SheetRuleResult.Ready(DemoRecordbook.sheetRule()), repository.request(linkId))
        assertEquals(SheetRuleResult.Ready(DemoRecordbook.sheetRule()), repository.state(linkId))
        assertTrue(calls.isEmpty())
    }

    @Test fun `a link id that is no UUID is not sent`() = runTest(main.dispatcher) {
        assertEquals(SheetRuleResult.Unavailable, repository().request("local-1"))
        assertTrue(calls.isEmpty())
        assertNull(columnIndex("a1"))
        assertEquals(0, columnIndex("A"))
        assertEquals(701, columnIndex("ZZ"))
    }
}
