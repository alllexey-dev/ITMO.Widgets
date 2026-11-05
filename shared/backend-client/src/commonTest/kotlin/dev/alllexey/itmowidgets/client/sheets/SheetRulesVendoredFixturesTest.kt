package dev.alllexey.itmowidgets.client.sheets

import dev.alllexey.itmowidgets.client.contract.ResponseClaim
import dev.alllexey.itmowidgets.client.contract.assertAreaClaims
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlin.test.Test

/** Backend's vendored sheet rules fixtures (`claims/sheets.txt`): every answer decodes and re-encodes to its `data`. */
class SheetRulesVendoredFixturesTest {

    private val responses = listOf(
        ResponseClaim("http/sheets/sheetRule.json", SheetRuleResponse.serializer()) {
            sheets.sheetRule(SheetRulesRouteCases.linkId)
        },
        ResponseClaim("http/sheets/requestSheetRule.json", SheetRuleResponse.serializer()) {
            sheets.requestSheetRule(SheetRulesRouteCases.linkId)
        },
    )

    @Test
    fun everyClaimDecodesAndRoundTrips() = runSuspend { assertAreaClaims("sheets", responses, emptyList()) }
}
