package dev.alllexey.itmowidgets.feature.messages.data

import com.fleeksoft.ksoup.nodes.Entities
import dev.alllexey.itmowidgets.feature.messages.domain.IsuAttachment
import dev.alllexey.itmowidgets.feature.messages.domain.IsuMessage
import dev.alllexey.itmowidgets.feature.messages.domain.IsuMessagesPage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The answer could not be read as the inbox list: not JSON or no `incoming` array. */
internal class IsuFormatException : Exception("ISU messages answer has an unexpected shape")

/**
 * Reads the answer of ISU's `MAIN_FN` (app 2422): `{"unrd_cntr": n, "incoming": [...], "outgoing": [...]}`, ten items a
 * page. Fields are read by name and tolerantly (a number may come as a string), so an added or reordered field changes
 * nothing; an item without a `msgid` is skipped rather than failing the page. Only `incoming` is read.
 */
internal object IsuMessagesJson {

    const val PAGE_SIZE = 10

    fun parse(text: String): IsuMessagesPage {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw IsuFormatException()
        val incoming = root["incoming"] as? JsonArray ?: throw IsuFormatException()
        val messages = incoming.mapNotNull { (it as? JsonObject)?.let(::message) }
        return IsuMessagesPage(
            messages = messages,
            unread = root.long("unrd_cntr")?.toInt()?.coerceAtLeast(0) ?: messages.count { !it.read },
            last = incoming.size < PAGE_SIZE,
        )
    }

    private fun message(item: JsonObject): IsuMessage? {
        val id = item.long("msgid") ?: return null
        return IsuMessage(
            id = id,
            sender = plain(item.string("sender_name")),
            subject = plain(item.string("subject")),
            date = item.string("date")?.let(::date),
            time = item.string("sender_date")?.let(::time),
            read = item.string("is_read")?.equals("Y", ignoreCase = true) ?: true,
            body = MessageHtml.parse(item.string("text").orEmpty()),
            attachments = (item["files"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::attachment) },
        )
    }

    private fun attachment(file: JsonObject): IsuAttachment? {
        val hash = file.string("hash_id")?.trim()?.takeIf(::isFileHash) ?: return null
        return IsuAttachment(
            id = hash,
            name = plain(file.string("name")).ifEmpty { hash },
            mimeType = file.string("type")?.trim()?.lowercase()?.takeIf { MIME.matches(it) },
        )
    }

    /** ISU's file hash: 32 hex characters; anything else would put foreign text into the download address. */
    fun isFileHash(value: String): Boolean = HASH.matches(value)

    /** `21.10.2026`. */
    private fun date(text: String): LocalDate? = DATE.find(text)?.destructured?.let { (day, month, year) ->
        runCatching { LocalDate(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
    }

    /** The time of `21 окт. 15:51`. */
    private fun time(text: String): LocalTime? = TIME.find(text)?.destructured?.let { (hour, minute) ->
        runCatching { LocalTime(hour.toInt(), minute.toInt()) }.getOrNull()
    }

    /** Names and subjects may carry HTML entities. */
    private fun plain(text: String?): String = text?.let { if ('&' in it) Entities.unescape(it) else it }?.trim().orEmpty()

    private fun JsonObject.primitive(name: String): JsonPrimitive? = this[name] as? JsonPrimitive

    private fun JsonObject.string(name: String): String? = primitive(name)?.contentOrNull

    private fun JsonObject.long(name: String): Long? = string(name)?.trim()?.toLongOrNull()

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val DATE = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""")
    private val TIME = Regex("""(\d{1,2}):(\d{2})""")
    private val HASH = Regex("[0-9a-fA-F]{32}")
    private val MIME = Regex("[a-z0-9.+-]+/[a-z0-9.+-]+")
}
