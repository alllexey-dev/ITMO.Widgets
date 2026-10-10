package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.storage.SnapshotFile
import kotlinx.serialization.Serializable
import okio.IOException

/**
 * What the extensions know of the session, `session-v1.json` in the App Group container: they link no Kotlin and
 * open no DataStore, so the demo switch reaches them only here. No token: the notification service reads those from
 * the Keychain. A missing file means signed out.
 */
@Serializable
data class SessionSnapshot(
    /** The signed-in student's ISU number, when the ID token carries one. */
    val isu: Int?,
    /** The demo session: the extensions show fictional data and send nothing. */
    val demo: Boolean,
    /** Whether the user allows alert notifications. */
    val alertsAllowed: Boolean,
    /** The ITMO.Widgets services opt-in (`BackendGate`): the notification service books and settles only with it. */
    val servicesEnabled: Boolean
)

/** The only writer of [FILE]; the session's lifecycle effects call it on every session change. */
class SessionSnapshotWriter(private val writer: AppGroupSnapshotWriter) {

    /** Replaces the snapshot as a whole, then reloads [reloadKinds] (widget kinds that show session state). */
    @Throws(IOException::class)
    fun write(snapshot: SessionSnapshot, reloadKinds: Collection<String> = emptyList()) =
        writer.write(FILE, snapshot, reloadKinds)

    /** The snapshot as this build reads it; null when signed out, unreadable or newer than this build. */
    fun read(): SessionSnapshot? = writer.read(FILE)

    companion object {
        val FILE = SnapshotFile(name = "session", version = 1, serializer = SessionSnapshot.serializer())
    }
}
