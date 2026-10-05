package dev.alllexey.itmowidgets.client.sport.model

import dev.alllexey.itmowidgets.client.json.UnknownTolerantEnumSerializer
import kotlinx.serialization.Serializable

/**
 * The state of a free-sign or auto-sign queue entry. Display enum: a value added by a newer server decodes as
 * [UNKNOWN], which consumers map explicitly. Which states notify is Backend's rule; the app keeps its own mapping.
 */
@Serializable(with = QueueEntryStatusSerializer::class)
enum class QueueEntryStatus {
    /** Waiting for a free place. */
    WAITING,

    /** A place appeared and the user was notified. */
    NOTIFIED,

    /** Backend stopped notifying after the maximum number of attempts. */
    GAVE_UP_NOTIFYING,

    /** The entry was marked satisfied: the user got the place. */
    SATISFIED,

    /** The entry's deadline passed without a place. */
    EXPIRED,

    /** Decode-only: a status this client does not know. Never sent by Backend and never encoded. */
    UNKNOWN,
}

internal object QueueEntryStatusSerializer :
    UnknownTolerantEnumSerializer<QueueEntryStatus>(
        "QueueEntryStatus",
        QueueEntryStatus.entries,
        QueueEntryStatus.UNKNOWN,
    )
