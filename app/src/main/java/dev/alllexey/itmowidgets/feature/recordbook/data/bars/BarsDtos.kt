package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import com.google.gson.annotations.SerializedName

// Field evidence: docs/bars-api.md and the live follow-up. Unused audit/identity fields are not retained.
data class BarsUser(val login: String, @SerializedName("selected_year") val year: String,
    @SerializedName("selected_term") val term: Int)
data class BarsSetting(val name: String, val value: String)
data class BarsDiscipline(val id: Long, val name: String,
    @SerializedName("checkpoint_plan_ids") val planIds: List<Long>)
data class BarsGroupOrFlow(val type: String, val identifier: String,
    @SerializedName("checkpoint_plan_ids") val planIds: List<Long>)
data class BarsJournal(val students: List<BarsStudent>, val headers: BarsHeaders)
data class BarsHeaders(val plan: BarsPlan, val type: String, val identifier: String)
data class BarsStudent(@SerializedName("student_login") val login: String, val marks: BarsMarks)
// Boxed mandatory scores let the mapper reject missing JSON rather than accept Gson primitive defaults.
data class BarsMarks(val regular: List<BarsMark>, val total: Double?,
    @SerializedName("final") val finalMark: BarsMark? = null,
    val additional: BarsMark? = null,
    @SerializedName("active_approvals") val approvals: List<BarsApproval>)
data class BarsMark(@SerializedName("checkpoint_id") val checkpointId: Long?,
    @SerializedName("checkpoint_plan_id") val planId: Long, val mark: Double?,
    @SerializedName("is_absent") val absent: Boolean)
data class BarsApproval(val attempt: Int,
    @SerializedName("checkpoint_plan_id") val planId: Long,
    @SerializedName("student_login") val login: String,
    @SerializedName("mark_string") val rate: String,
    @SerializedName("is_active") val active: Boolean,
    @SerializedName("is_invalid") val invalid: Boolean,
    @SerializedName("is_absent") val absent: Boolean, val course: Boolean)
data class BarsPlan(val id: Long, val year: String, val discipline: BarsPlanDiscipline,
    @SerializedName("regular_checkpoints") val regular: List<BarsCheckpoint>,
    @SerializedName("final_checkpoint") val finalCheckpoint: BarsCheckpoint?,
    @SerializedName("has_course_project") val hasCourseProject: Boolean) {
    // TODO: programs/components were empty; course_project_checkpoint only null. Do not invent their DTOs.
}
data class BarsPlanDiscipline(val id: Long, val name: String)
data class BarsCheckpoint(val id: Long, val name: String?, val type: String,
    @SerializedName("min_grade") val minimum: Double,
    @SerializedName("max_grade") val maximum: Double, val key: Boolean,
    @SerializedName("sub_checkpoints") val children: List<BarsCheckpoint>) {
    // Recursion is explicitly supported by the official UI; live samples were flat.
    // TODO: test_id/test_name/parent_checkpoint_id/max_sub_checkpoints_fillable were only null.
}
