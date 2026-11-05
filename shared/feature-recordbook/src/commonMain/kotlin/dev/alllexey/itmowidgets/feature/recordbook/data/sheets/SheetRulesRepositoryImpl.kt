package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.sheets.SheetRuleResponse
import dev.alllexey.itmowidgets.client.sheets.SheetRuleStatus
import dev.alllexey.itmowidgets.client.sheets.SheetRulesApi
import dev.alllexey.itmowidgets.client.sheets.SheetRule as WireRule
import dev.alllexey.itmowidgets.client.sheets.SheetRuleNode as WireNode
import dev.alllexey.itmowidgets.client.sheets.SheetRuleRoute as WireRoute
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.feature.recordbook.data.demo.DemoRecordbook
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.BlankPolicy
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetOp
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRule
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRuleNode
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRulePurpose
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRuleResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRuleRoute
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRuleTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRulesRepository
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Backend's sheet rules over Core 2.0's [SheetRulesApi]. The demo session answers [DemoRecordbook.sheetRule] and never
 * calls Backend; outside it every call needs [BackendGate.mayCallBackend]. A route the device cannot read (an unknown
 * purpose, kind, policy or node) is dropped, as the analyzer drops one it cannot check.
 */
class SheetRulesRepositoryImpl(
    private val api: SheetRulesApi,
    private val backend: BackendGate,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : SheetRulesRepository {

    override suspend fun available(): Boolean = demo.isActive() || backend.mayCallBackend()

    override suspend fun request(linkId: String): SheetRuleResult = call(linkId) { api.requestSheetRule(it) }

    override suspend fun state(linkId: String): SheetRuleResult = call(linkId) { api.sheetRule(it) }

    private suspend fun call(linkId: String, route: suspend (Uuid) -> SheetRuleResponse): SheetRuleResult {
        if (demo.isActive()) return SheetRuleResult.Ready(DemoRecordbook.sheetRule())
        if (!backend.mayCallBackend()) return SheetRuleResult.Unavailable
        val id = Uuid.parseOrNull(linkId) ?: return SheetRuleResult.Unavailable
        return try {
            withContext(dispatchers.io) { route(id) }.toResult()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            error.toResult()
        }
    }

    private fun SheetRuleResponse.toResult(): SheetRuleResult {
        val rule = try {
            decodedRule()?.toModel()
        } catch (_: Exception) {
            return SheetRuleResult.Failed
        }
        return when (status) {
            // A stale rule stays valid while Backend checks the sheet's structure again.
            SheetRuleStatus.READY, SheetRuleStatus.PENDING, SheetRuleStatus.RUNNING ->
                rule?.let(SheetRuleResult::Ready) ?: if (status == SheetRuleStatus.READY) SheetRuleResult.Failed else SheetRuleResult.Pending
            SheetRuleStatus.NONE -> SheetRuleResult.Pending
            SheetRuleStatus.INSUFFICIENT -> SheetRuleResult.Insufficient
            SheetRuleStatus.FAILED, SheetRuleStatus.UNKNOWN -> SheetRuleResult.Failed
        }
    }

    private fun Exception.toResult(): SheetRuleResult = when {
        isCausedByNetworkFailure() || this is BackendException.Transport -> SheetRuleResult.Offline
        this is BackendException.NotFound -> SheetRuleResult.Unavailable
        this is BackendException.Http && status == CONFLICT -> SheetRuleResult.Unavailable
        this is BackendException.Http && status == TOO_MANY_REQUESTS -> SheetRuleResult.RateLimited
        else -> SheetRuleResult.Failed
    }

    private companion object {
        const val CONFLICT = 409
        const val TOO_MANY_REQUESTS = 429
    }
}

/** The routes the device can apply; null when none is left. */
internal fun WireRule.toModel(): SheetRule? {
    val routes = routes.mapNotNull { it.toModel() }
    return if (routes.isEmpty()) null else SheetRule(summary, routes)
}

private fun WireRoute.toModel(): SheetRuleRoute? {
    val purpose = when (purpose) {
        "current_accumulated" -> SheetRulePurpose.CURRENT
        "official_total" -> SheetRulePurpose.OFFICIAL
        else -> return null
    }
    val blanks = when (blankPolicy) {
        "preserve" -> BlankPolicy.PRESERVE
        "ignore_blank_inputs" -> BlankPolicy.IGNORE_BLANK_INPUTS
        else -> return null
    }
    val node = when (kind) {
        "direct" -> source?.let { SheetRuleNode.Cell(tab(it.sheet, it.gid) ?: return null, columnIndex(it.column) ?: return null) }
        "compute" -> calculation?.toModel()
        else -> null
    } ?: return null
    return SheetRuleRoute(
        purpose = purpose,
        tab = tab(sheet, gid) ?: return null,
        direct = kind == "direct",
        node = node,
        blanks = blanks,
        explanation = explanation,
        warnings = warnings,
    )
}

private fun WireNode.toModel(): SheetRuleNode? = when (op) {
    "cell" -> SheetRuleNode.Cell(tab(sheet ?: return null, gid) ?: return null, columnIndex(column ?: return null) ?: return null)
    "number" -> value?.takeIf { it.isFinite() }?.let(SheetRuleNode::Number)
    "round" -> {
        val inner = arg?.toModel()
        val places = digits?.takeIf { it in 0..MAX_DIGITS }
        if (inner == null || places == null) null else SheetRuleNode.Round(inner, places)
    }
    else -> {
        val operation = SheetOp.entries.firstOrNull { it.name.lowercase() == op }
        val nodes = args.orEmpty().map { it.toModel() ?: return null }
        val binary = operation == SheetOp.SUBTRACT || operation == SheetOp.DIVIDE
        when {
            operation == null || nodes.isEmpty() || (binary && nodes.size != 2) -> null
            else -> SheetRuleNode.Apply(operation, nodes)
        }
    }
}

private const val MAX_DIGITS = 6
private val COLUMN = Regex("[A-Z]{1,3}")

private fun tab(sheet: String, gid: String?): SheetRuleTab? {
    val number = gid?.let { it.toLongOrNull() ?: return null }
    return SheetRuleTab(sheet, number)
}

/** `A` is 0, `Z` 25, `AA` 26. */
internal fun columnIndex(letters: String): Int? {
    if (!COLUMN.matches(letters)) return null
    return letters.fold(0) { index, letter -> index * 26 + (letter - 'A' + 1) } - 1
}
