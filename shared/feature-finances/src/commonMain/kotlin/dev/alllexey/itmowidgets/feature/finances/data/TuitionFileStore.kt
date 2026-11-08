package dev.alllexey.itmowidgets.feature.finances.data

import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/** 1: the last answer of one account. */
private const val FORMAT = 1

/**
 * The last successful tuition answer: [contracts] the Договоры об обучении (none on a budget place), [fetchedAt] the
 * wall-clock time it came, [owner] the ISU of the account it belongs to.
 */
@Serializable
internal data class StoredTuition(
    val format: Int = FORMAT,
    val owner: Int? = null,
    val fetchedAt: Long,
    val contracts: List<StoredContract> = emptyList(),
)

/** A Договор об обучении: amounts in kopecks, dates as Moscow calendar dates, [academicYear] its first year. */
@Serializable
internal data class StoredContract(
    val id: String,
    val number: String,
    val start: LocalDate,
    val end: LocalDate,
    val academicYear: Int,
    val active: Boolean,
    val balance: Long,
    val payments: List<StoredPayment> = emptyList(),
)

@Serializable
internal data class StoredPayment(val amount: Long, val paid: Long, val dueDate: LocalDate, val period: String)

/** `noBackup/finances/tuition.json`: never backed up, cleared with the session. Caller owns IO dispatch. */
class TuitionFileStore internal constructor(private val directory: Path, private val fileSystem: FileSystem) {
    constructor(directories: AppDirectories) : this(directories.noBackup / "finances", FinancesFileSystem)

    private val file = AtomicTextFile(directory / "tuition.json", fileSystem)

    /** `null` without a file; throws on a corrupt file or another format. */
    internal fun read(): StoredTuition? {
        val text = file.read() ?: return null
        val stored = StoreJson.decodeFromString<StoredTuition>(text)
        check(stored.format == FORMAT) { "Unknown tuition format ${stored.format}" }
        return stored
    }

    internal fun write(stored: StoredTuition) = file.write(StoreJson.encodeToString(stored))

    fun clear() = fileSystem.deleteRecursively(directory)
}

@OptIn(ExperimentalSerializationApi::class)
private val StoreJson = Json {
    explicitNulls = false
    ignoreUnknownKeys = true
    encodeDefaults = true
}
