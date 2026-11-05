package dev.alllexey.itmowidgets.client.sheets

import dev.alllexey.itmowidgets.client.support.RouteCase
import kotlin.uuid.Uuid

/** One [RouteCase] per public function of [SheetRulesApi], with synthetic arguments. */
object SheetRulesRouteCases {
    val linkId: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000042")

    val sheetRule = RouteCase("sheetRule") { sheets.sheetRule(linkId) }
    val requestSheetRule = RouteCase("requestSheetRule") { sheets.requestSheetRule(linkId) }

    val all: List<RouteCase> = listOf(sheetRule, requestSheetRule)
}
