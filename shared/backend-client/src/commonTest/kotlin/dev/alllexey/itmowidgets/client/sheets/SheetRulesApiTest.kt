package dev.alllexey.itmowidgets.client.sheets

import dev.alllexey.itmowidgets.client.contract.VendoredContract
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.assertRequest
import dev.alllexey.itmowidgets.client.support.json
import dev.alllexey.itmowidgets.client.support.ok
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The requests of [SheetRulesApi], the typed rule of the vendored fixture and the error answers. */
class SheetRulesApiTest {

    private val path = "/api/links/00000000-0000-0000-0000-000000000042/sheet-rule"

    @Test
    fun everyPublicFunctionHasOneCase() {
        assertEquals(2, SheetRulesRouteCases.all.size)
    }

    @Test
    fun sheetRuleIsAGet() = runSuspend { SheetRulesRouteCases.sheetRule.assertRequest(HttpMethod.Get, path) }

    @Test
    fun requestSheetRuleIsABodilessPost() = runSuspend {
        SheetRulesRouteCases.requestSheetRule.assertRequest(HttpMethod.Post, path)
    }

    @Test
    fun readyRuleDecodesIntoRoutesAndNodes() = runSuspend {
        val backend = MockBackend { ok(VendoredContract.read("http/sheets/sheetRule.json")) }

        val response = backend.client.sheets.sheetRule(SheetRulesRouteCases.linkId)
        val rule = response.decodedRule()!!

        assertEquals(SheetRuleStatus.READY, response.status)
        assertEquals(1, rule.schemaVersion)
        assertEquals(listOf("current_accumulated", "official_total"), rule.routes.map { it.purpose })
        val sum = rule.routes[0].calculation!!
        assertEquals("sum", sum.op)
        assertEquals(listOf("cell", "round", "number"), sum.args!!.map { it.op })
        assertEquals("E", sum.args!![1].arg!!.column)
        assertEquals(1, sum.args!![1].digits)
        assertEquals(0.5, sum.args!![2].value)
        assertEquals("ignore_blank_inputs", rule.routes[0].blankPolicy)
        assertEquals(SheetRuleCell("Группа 1", "0", "R"), rule.routes[1].source)
        assertNull(rule.tabs[1].gid)
    }

    @Test
    fun failedStateHasNoRule() = runSuspend {
        val backend = MockBackend { ok(VendoredContract.read("http/sheets/requestSheetRule.json")) }

        val response = backend.client.sheets.requestSheetRule(SheetRulesRouteCases.linkId)

        assertEquals(SheetRuleStatus.FAILED, response.status)
        assertEquals("closed", response.reason)
        assertNull(response.decodedRule())
    }

    @Test
    fun aNewerStatusIsUnknown() = runSuspend {
        val backend = MockBackend {
            ok("""{"success":true,"data":{"status":"PAUSED","rule":null,"analyzedAt":null,"reason":null},"error":null}""")
        }

        assertEquals(SheetRuleStatus.UNKNOWN, backend.client.sheets.sheetRule(SheetRulesRouteCases.linkId).status)
    }

    @Test
    fun conflictAndRateLimitKeepTheirStatus() = runSuspend {
        val conflict = MockBackend {
            json(
                HttpStatusCode.Conflict,
                """{"success":false,"data":null,"error":{"code":"business_rule_violation","message":"sheet_rules_disabled"}}""",
            )
        }
        val limited = MockBackend {
            json(HttpStatusCode.TooManyRequests, """{"success":false,"data":null,"error":{"code":"rate_limited","message":""}}""")
        }

        val error = assertFailsWith<BackendException.Http> { conflict.client.sheets.requestSheetRule(SheetRulesRouteCases.linkId) }
        assertEquals(409, error.status)
        assertIs<BackendException.Http>(
            assertFailsWith<BackendException> { limited.client.sheets.requestSheetRule(SheetRulesRouteCases.linkId) },
        )
    }
}
