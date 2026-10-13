package dev.alllexey.itmowidgets.core.bell

import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** What a bell row is about; it picks the row's leading tile. */
enum class BellKind { MARK, SCHEDULE_CHANGE, SPORT, FRIEND_REQUEST }

/**
 * One event of the bell. [id] changes with the event's content, so a new mark of the same subject is new again.
 * [title] carries the value (`Матанализ: контрольная работа 2`), [text] the details (`9 из 10, итого 74`), [was] and
 * [now] a change's `было` and `стало`. [lead] is a mark's delta (`+9`) or the person's name of a request, with
 * [pictureUrl]. [failed] marks an event that went wrong (a refused booking). [at] is null when the source does not
 * know the moment. [target] is the event's home; a request has the answer buttons instead of a target.
 */
data class BellItem(
    val id: String,
    val kind: BellKind,
    val at: Instant?,
    val title: UiText,
    val text: UiText? = null,
    val was: UiText? = null,
    val now: UiText? = null,
    val lead: String? = null,
    val pictureUrl: String? = null,
    val failed: Boolean = false,
    val target: EntryRoute? = null,
    val request: Int? = null,
)

/**
 * A feature's events in the bell, from local stores (and a refresh where the data is remote). Sources are an open
 * set (`getAll()`), each under its own qualifier.
 */
interface BellSource {
    fun observe(): Flow<List<BellItem>>

    suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)

    /** Opening the bell reads everything: the feature's own unread marks and notifications go away. */
    suspend fun markAllRead() = Unit

    /** Answers the friend request of [isu]; only the source that showed it acts. */
    suspend fun answer(isu: Int, accept: Boolean): AppResult<Unit>? = null
}
