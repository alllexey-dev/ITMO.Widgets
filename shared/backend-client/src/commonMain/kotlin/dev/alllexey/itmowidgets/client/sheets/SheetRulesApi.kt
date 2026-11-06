package dev.alllexey.itmowidgets.client.sheets

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.http.BackendRoute
import io.ktor.http.HttpMethod
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * Sheet rules (`/api/links/{id}/sheet-rule`, Backend's `sheet-rules.md` contract): how to read or compute the own
 * points from a Google Sheet that a subject link shares with a flow or everybody. Backend decides eligibility: 404
 * `not_found` for a link the caller does not see or that is not FLOW/ALL, 409 `business_rule_violation` for a link
 * that is no Google sheet or while the feature is off, 429 over the per-user limit of analyses.
 */
interface SheetRulesApi {

    /** `GET /api/links/{id}/sheet-rule`: the stored state and rule; never starts an analysis. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun sheetRule(linkId: Uuid): SheetRuleResponse

    /**
     * `POST /api/links/{id}/sheet-rule`: queues an analysis when the sheet has none or a stale one (202 while
     * `PENDING`/`RUNNING`), else answers the stored state.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun requestSheetRule(linkId: Uuid): SheetRuleResponse
}

internal class KtorSheetRulesApi(private val http: BackendHttp) : SheetRulesApi {

    override suspend fun sheetRule(linkId: Uuid): SheetRuleResponse =
        http.call(BackendRoute(HttpMethod.Get, segments(linkId)), SheetRuleResponse.serializer())

    override suspend fun requestSheetRule(linkId: Uuid): SheetRuleResponse =
        http.call(BackendRoute(HttpMethod.Post, segments(linkId)), SheetRuleResponse.serializer())

    private fun segments(linkId: Uuid) = listOf("api", "links", linkId.toHexDashString(), "sheet-rule")
}
