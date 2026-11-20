package dev.alllexey.itmowidgets.feature.recordbook.data.lms

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** A module's own score. A parent and its tasks describe the same work and must not be added together. */
data class LmsModuleScore(
    val id: Long,
    val name: String,
    val earnedPoints: Double?,
    val minGrade: Double?,
    val maxGrade: Double?,
    val percentGrade: Double?,
    val verificationStatus: String?,
)

data class LmsProgram(val id: Long, val name: String)

data class LmsCourse(val id: Long, val name: String)

data class LmsProgramPage(val items: List<LmsProgram>, val total: Int)

data class LmsModulePage(val items: List<LmsModuleScore>, val total: Int)

/** Only fields used by the LMS progress screen; an unexpected shape is an error, never an empty journal. */
internal object LmsJournal {
    private val json = Json { ignoreUnknownKeys = true }

    fun tokenExchangeRequest(code: String, state: String, redirectUri: String): String = buildJsonObject {
        put("state", state)
        put("authorizationCode", code)
        put("redirectUri", redirectUri)
    }.toString()

    fun accessToken(body: String): String =
        requireNotNull(json.parseToJsonElement(body).jsonObject["accessToken"]?.jsonPrimitive?.contentOrNull)
            .also { require(it.isNotBlank()) }

    fun programs(body: String): LmsProgramPage {
        val root = json.parseToJsonElement(body).jsonObject
        val total = requireNotNull(root["elementsTotal"]?.jsonPrimitive?.longOrNull)
        require(total in 0..10_000)
        val items = requireNotNull(root["content"]).jsonArray.map { element ->
            val program = requireNotNull(element.jsonObject["program"]).jsonObject
            val id = requireNotNull(program["id"]?.jsonPrimitive?.longOrNull)
            val name = requireNotNull(program["name"]?.jsonPrimitive?.contentOrNull).trim()
            require(id > 0 && name.isNotEmpty())
            LmsProgram(id, name)
        }
        require(items.size <= total)
        return LmsProgramPage(items, total.toInt())
    }

    fun courses(body: String): List<LmsCourse> = json.parseToJsonElement(body).jsonArray.map { element ->
        val course = element.jsonObject
        val id = requireNotNull(course["id"]?.jsonPrimitive?.longOrNull)
        val name = requireNotNull(course["name"]?.jsonPrimitive?.contentOrNull).trim()
        require(id > 0 && name.isNotEmpty())
        LmsCourse(id, name)
    }

    fun courseGradeId(body: String): Long =
        requireNotNull(json.parseToJsonElement(body).jsonObject["courseGradeId"]?.jsonPrimitive?.longOrNull)
            .also { require(it > 0) }

    fun modules(body: String): LmsModulePage {
        val root = json.parseToJsonElement(body).jsonObject
        val total = requireNotNull(root["elementsTotal"]?.jsonPrimitive?.longOrNull)
        require(total in 0..10_000)
        val items = requireNotNull(root["content"]).jsonArray.map { element ->
            val item = element.jsonObject
            val module = requireNotNull(item["module"]).jsonObject
            val id = requireNotNull(module["id"]?.jsonPrimitive?.longOrNull)
            val name = requireNotNull(module["name"]?.jsonPrimitive?.content).trim()
            require(id > 0 && name.isNotEmpty())
            LmsModuleScore(
                id = id,
                name = name,
                earnedPoints = item.number("earnedPoints"),
                minGrade = item.number("minGrade"),
                maxGrade = item.number("maxGrade"),
                percentGrade = item.number("percentGrade"),
                verificationStatus = (item["verificationInfo"] as? JsonObject)
                    ?.get("effectiveOfflineProctoringStudentStatus")?.jsonPrimitive?.contentOrNull,
            )
        }
        require(items.size <= total)
        return LmsModulePage(items, total.toInt())
    }

    private fun JsonObject.number(key: String): Double? {
        val element = get(key) ?: return null
        if (element is JsonNull) return null
        return requireNotNull(element.jsonPrimitive.doubleOrNull).also { require(it.isFinite()) }
    }
}
