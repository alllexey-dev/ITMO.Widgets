package dev.alllexey.itmowidgets.feature.dormitory.data

import dev.alllexey.itmowidgets.core.payments.StoredContract
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/** 1: the last answer of one account. */
private const val FORMAT = 1

/**
 * The last successful dormitory answer: [status] null for no application, [fetchedAt] the wall-clock time it came,
 * [owner] the ISU of the account it belongs to. [contracts] are the Договоры найма as of [contractsFetchedAt] (null
 * before they ever loaded): a refresh whose Договоры fail keeps the earlier ones beside the new status.
 */
@Serializable
internal data class StoredDormitory(
    val format: Int = FORMAT,
    val owner: Int? = null,
    val fetchedAt: Long,
    val status: StoredStatus? = null,
    val contracts: List<StoredContract> = emptyList(),
    val contractsFetchedAt: Long? = null,
)

/**
 * The settlement instructions are kept as ITMO's HTML and parsed again on read, like the first time.
 * [placeOfferedSeenAt] is the wall-clock time the app first saw `Место предоставлено`, kept while the stage stays.
 */
@Serializable
internal data class StoredStatus(
    val statusId: Int,
    val queuePlace: Int? = null,
    val dormitory: StoredDormitoryPlace? = null,
    val documents: List<StoredDocument> = emptyList(),
    val placeOfferedSeenAt: Long? = null,
)

@Serializable
internal data class StoredDormitoryPlace(val name: String, val address: String, val instructionsHtml: String)

@Serializable
internal data class StoredDocument(val name: String, val status: String, val confirmed: Boolean)

/**
 * `noBackup/dormitory/state.json`: never backed up, cleared with the session. Caller owns IO dispatch.
 */
class DormitoryFileStore internal constructor(private val directory: Path, private val fileSystem: FileSystem) {
    constructor(directories: AppDirectories) : this(directories.noBackup / "dormitory", DormitoryFileSystem)

    private val file = AtomicTextFile(directory / "state.json", fileSystem)

    /** `null` without a file; throws on a corrupt file or another format. */
    internal fun read(): StoredDormitory? {
        val text = file.read() ?: return null
        val stored = StoreJson.decodeFromString<StoredDormitory>(text)
        check(stored.format == FORMAT) { "Unknown dormitory format ${stored.format}" }
        return stored
    }

    internal fun write(stored: StoredDormitory) = file.write(StoreJson.encodeToString(stored))

    fun clear() = fileSystem.deleteRecursively(directory)
}

@OptIn(ExperimentalSerializationApi::class)
private val StoreJson = Json {
    explicitNulls = false
    ignoreUnknownKeys = true
    encodeDefaults = true
}
