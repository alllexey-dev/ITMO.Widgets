package dev.alllexey.itmowidgets.client.sheets

import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.json.UnknownTolerantEnumSerializer
import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The analysis state of a sheet. [rule] is the analyzer's rule as JSON, set with [SheetRuleStatus.READY] (and kept
 * while a stale one is checked); [decodedRule] reads it. [reason] is an open set of codes (`closed`, `insufficient`,
 * `budget_exhausted`, ...).
 */
@Serializable
data class SheetRuleResponse(
    val status: SheetRuleStatus,
    val rule: JsonObject?,
    @Serializable(with = WireInstantSerializer::class) val analyzedAt: Instant?,
    val reason: String?,
) {
    /** [rule] as [SheetRule]; throws [kotlinx.serialization.SerializationException] on another shape. */
    fun decodedRule(): SheetRule? = rule?.let { BackendJson.decodeFromJsonElement(SheetRule.serializer(), it) }
}

/** A display enum: a newer value decodes as [UNKNOWN]. */
@Serializable(with = SheetRuleStatusSerializer::class)
enum class SheetRuleStatus { NONE, PENDING, RUNNING, READY, INSUFFICIENT, FAILED, UNKNOWN }

internal object SheetRuleStatusSerializer :
    UnknownTolerantEnumSerializer<SheetRuleStatus>("SheetRuleStatus", SheetRuleStatus.entries, SheetRuleStatus.UNKNOWN)

/** The rule, schema version 1: tabs and routes; no student data. Unknown enum texts stay strings. */
@Serializable
data class SheetRule(
    @SerialName("schema_version") val schemaVersion: Int,
    val decision: String,
    val summary: String,
    val tabs: List<SheetRuleTab>,
    val routes: List<SheetRuleRoute>,
)

/** [gid] is a string of digits or null when the analyzer did not know it. */
@Serializable
data class SheetRuleTab(val sheet: String, val gid: String?, val role: String)

/**
 * A route: [purpose] `current_accumulated` or `official_total`, [kind] `direct` (with [source]) or `compute` (with
 * [calculation]); [blankPolicy] `preserve` or `ignore_blank_inputs`.
 */
@Serializable
data class SheetRuleRoute(
    val id: String,
    val purpose: String,
    val kind: String,
    val sheet: String,
    val gid: String?,
    val identity: List<SheetRuleIdentity>,
    val source: SheetRuleCell?,
    val calculation: SheetRuleNode?,
    @SerialName("blank_policy") val blankPolicy: String,
    val explanation: String,
    @SerialName("review_required") val reviewRequired: Boolean,
    val warnings: List<String>,
)

@Serializable
data class SheetRuleIdentity(
    val sheet: String,
    val gid: String?,
    @SerialName("isu_column") val isuColumn: String?,
    @SerialName("name_column") val nameColumn: String?,
    @SerialName("first_student_row") val firstStudentRow: Int,
)

/** A column of the own row: [column] is 1-3 capital letters. */
@Serializable
data class SheetRuleCell(val sheet: String, val gid: String?, val column: String)

/**
 * A calculation node by [op]: `cell` ([sheet], [gid], [column]), `number` ([value]), `sum`/`multiply`/`min`/`max`
 * (1-200 [args]), `subtract`/`divide` (two [args]), `round` ([arg], [digits] 0-6).
 */
@Serializable
data class SheetRuleNode(
    val op: String,
    val sheet: String? = null,
    val gid: String? = null,
    val column: String? = null,
    val value: Double? = null,
    val args: List<SheetRuleNode>? = null,
    val arg: SheetRuleNode? = null,
    val digits: Int? = null,
)
