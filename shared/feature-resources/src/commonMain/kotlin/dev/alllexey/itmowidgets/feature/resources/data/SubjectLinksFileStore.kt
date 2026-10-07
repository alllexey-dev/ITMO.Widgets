package dev.alllexey.itmowidgets.feature.resources.data

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import kotlinx.datetime.format.optional
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path

/**
 * 3: app-owned rows and ISO instants. 2 held Core 1.x's answers as Gson wrote them and links naming one schedule flow;
 * 1 held GROUP/FLOW audiences. Formats 1 and 2 keep their device-only links and pins and drop the cached answers.
 */
private const val FORMAT = 3
private val READABLE = 1..FORMAT

/**
 * The JSON of the links file (recipe `kotlinx-file-store`): absent nullable keys read as null and nulls are not
 * written, as Gson did, and `format` is always written.
 */
@OptIn(ExperimentalSerializationApi::class)
internal val ResourcesStoreJson = Json {
    explicitNulls = false
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** What a device-only link sends on the first refresh with the opt-in; the shape 2.2 wrote as Core's save request. */
@Serializable
data class StoredLinkRequest(
    val subjectId: Long,
    val subjectName: String,
    val periodKey: String,
    val category: LinkCategory,
    val url: String,
    val title: String? = null,
    val visibility: LinkVisibility,
    val flowId: Long? = null,
)

/** A PRIVATE link saved without the opt-in; [request] is what the first refresh with the opt-in sends. */
@Serializable
data class LocalLink(
    val id: String,
    val request: StoredLinkRequest,
    @Serializable(with = StoredInstantSerializer::class) val updatedAt: Instant,
) {
    val scope: ResourceScope get() = ResourceScope(request.subjectId, request.subjectName, request.periodKey)
}

/** A pin of an own link made without the opt-in. */
@Serializable
data class LocalPin(@Serializable(with = StoredScopeSerializer::class) val scope: ResourceScope, val linkId: String)

/** A link of a cached answer, trimmed as the screens show it. */
@Serializable
data class StoredLink(
    val id: String,
    val subjectId: Long,
    val subjectName: String,
    val periodKey: String,
    val category: LinkCategory,
    val url: String,
    val title: String? = null,
    val visibility: LinkVisibility,
    val flowId: Long? = null,
    val audienceLabel: String? = null,
    val status: SubjectLinkStatus,
    val reviewNote: String? = null,
    val score: Int,
    val myVote: Int,
    val isMine: Boolean,
    val reportedByMe: Boolean,
    val author: StoredAuthor? = null,
    @Serializable(with = StoredInstantSerializer::class) val updatedAt: Instant,
)

/** A link's author with the viewer-scoped sharing Backend sent. */
@Serializable
data class StoredAuthor(
    val isu: Int,
    val name: String,
    val pictureUrl: String? = null,
    val groups: List<StoredAuthorGroup> = emptyList(),
    val sharing: StoredAuthorSharing,
)

@Serializable
data class StoredAuthorGroup(val name: String, val course: Int, val facultyShortName: String)

@Serializable
data class StoredAuthorSharing(val sport: Boolean, val schedule: Boolean, val friends: Boolean = false)

@Serializable
data class StoredAudience(val flowId: Long, val label: String, val typeId: Int, val depth: Int)

/** The last server answer for a scope. */
@Serializable
data class StoredLinksAnswer(
    val mine: List<StoredLink> = emptyList(),
    val shared: List<StoredLink> = emptyList(),
    val previous: List<StoredLink> = emptyList(),
    val pinnedId: String? = null,
    val audiences: List<StoredAudience> = emptyList(),
    val premoderation: Boolean,
)

@Serializable
data class CachedLinks(
    @Serializable(with = StoredScopeSerializer::class) val scope: ResourceScope,
    val response: StoredLinksAnswer,
)

@Serializable
data class StoredLinks(
    val format: Int = FORMAT,
    val local: Map<String, LocalLink> = emptyMap(),
    val localPins: Map<String, LocalPin> = emptyMap(),
    val scopes: Map<String, CachedLinks> = emptyMap(),
)

/**
 * Persistent local links and server snapshots in `filesDir` ([AppDirectories.files]), never `cacheDir`. Caller owns
 * IO dispatch and serialization. The stored rows are public for the 2.2 upgrade checker in `:app`.
 */
class SubjectLinksFileStore internal constructor(private val directory: Path, private val fileSystem: FileSystem) {
    constructor(directories: AppDirectories) : this(directories.files / "subject_links", ResourcesFileSystem)

    private val file = AtomicTextFile(directory / "cache.json", fileSystem)

    /** Throws on a corrupt file or an unknown format: device-only links must never become an empty store. */
    fun read(): StoredLinks {
        val text = file.read() ?: return StoredLinks()
        val tree = ResourcesStoreJson.parseToJsonElement(text).jsonObject
        val format = tree["format"]?.jsonPrimitive?.int
        check(format != null && format in READABLE && tree["local"] is JsonObject) { "Invalid subject links store" }
        // Older formats cached Core's answers in Gson's shapes. One that failed to decode would cost the device-only
        // links, so the answers are dropped and refetched on the next open, as the 1 -> 2 migration did.
        val current = if (format == FORMAT) tree else JsonObject(tree - "scopes" + ("format" to JsonPrimitive(FORMAT)))
        val state = ResourcesStoreJson.decodeFromJsonElement(StoredLinks.serializer(), current)
        state.local.forEach { (id, link) ->
            Uuid.parse(id)
            check(link.id == id && link.request.visibility == LinkVisibility.PRIVATE)
            check(link.scope.valid() && link.request.url.isNotBlank())
        }
        state.localPins.forEach { (key, pin) ->
            Uuid.parse(pin.linkId)
            check(pin.scope.valid() && pin.scope.key == key)
        }
        state.scopes.forEach { (key, cached) ->
            check(cached.scope.valid() && cached.scope.key == key)
            with(cached.response) {
                (mine + shared + previous).forEach { Uuid.parse(it.id) }
                pinnedId?.let(Uuid::parse)
            }
        }
        return state
    }

    fun write(state: StoredLinks) = file.write(ResourcesStoreJson.encodeToString(StoredLinks.serializer(), state))

    fun clear() = fileSystem.deleteRecursively(directory)

    private fun ResourceScope.valid() = subjectId > 0 && subjectName.isNotBlank() && periodKey.matches(PERIOD_KEY)

    private companion object {
        val PERIOD_KEY = Regex("[0-9]{4}-[12]")
    }
}

@Serializable
private class StoredScope(val subjectId: Long, val subjectName: String, val periodKey: String)

/** A scope in the shape 2.2 wrote, so that pins of every format decode alike. */
private object StoredScopeSerializer : KSerializer<ResourceScope> {
    override val descriptor: SerialDescriptor = StoredScope.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ResourceScope) = encoder.encodeSerializableValue(
        StoredScope.serializer(), StoredScope(value.subjectId, value.subjectName, value.periodKey)
    )

    override fun deserialize(decoder: Decoder): ResourceScope =
        decoder.decodeSerializableValue(StoredScope.serializer()).let { ResourceScope(it.subjectId, it.subjectName, it.periodKey) }
}

/**
 * Reads ISO instants and the `OffsetDateTime` text Gson wrote up to format 2, which drops `:00` seconds
 * (`2026-09-22T12:00+03:00`) and which no built-in parser accepts; writes `Instant.toString()` (UTC).
 */
private object StoredInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.alllexey.itmowidgets.feature.resources.data.StoredInstant", PrimitiveKind.STRING)

    private val format = DateTimeComponents.Format {
        date(LocalDate.Formats.ISO)
        char('T')
        hour()
        char(':')
        minute()
        optional {
            char(':')
            second()
            optional {
                char('.')
                secondFraction(1, 9)
            }
        }
        offset(UtcOffset.Formats.ISO)
    }

    override fun deserialize(decoder: Decoder): Instant {
        val text = decoder.decodeString()
        return try {
            format.parse(text).toInstantUsingOffset()
        } catch (error: IllegalArgumentException) {
            throw SerializationException("Not a stored date-time: $text", error)
        }
    }

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
}
