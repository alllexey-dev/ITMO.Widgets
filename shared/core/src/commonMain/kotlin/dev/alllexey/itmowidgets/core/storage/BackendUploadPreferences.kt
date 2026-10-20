package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import okio.ByteString.Companion.encodeUtf8

/** The snapshot uploads to Backend that replace what it stored before; [sendIfChanged] skips repeats. */
enum class BackendUpload(internal val key: Preferences.Key<String>) {
    LESSONS(stringPreferencesKey("backend_upload_lessons")),
    SPORT_BOOKINGS(stringPreferencesKey("backend_upload_sport_bookings")),
}

/**
 * What Backend last accepted of each [BackendUpload]: the payload's SHA-256 and when. Every background widget cycle
 * fetches the same MyITMO data, so an unchanged snapshot goes again only a day later, in case Backend lost it.
 * Every session change and opt-in switch forgets them.
 */
class BackendUploadPreferences(
    dataStore: DataStore<Preferences>,
    private val clock: Clock,
) : DataStorePreferences(dataStore), SessionDataCleaner {

    /** Runs [send] unless Backend accepted the same [payload] (compared by `toString`) for [upload] within a day. */
    suspend fun sendIfChanged(upload: BackendUpload, payload: Any, send: suspend () -> Unit) {
        val fingerprint = payload.toString().encodeUtf8().sha256().hex()
        val now = clock.now()
        val accepted = read()[upload.key]?.split(SEPARATOR)
        val acceptedAt = accepted?.getOrNull(1)?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
        if (accepted?.first() == fingerprint && acceptedAt != null && now - acceptedAt in 0.hours..<RESEND_AFTER) return
        send()
        write(upload.key, "$fingerprint$SEPARATOR${now.toEpochMilliseconds()}")
    }

    override suspend fun clearSessionData() {
        dataStore.edit { preferences -> BackendUpload.entries.forEach { preferences.remove(it.key) } }
    }

    private companion object {
        const val SEPARATOR = "@"
        val RESEND_AFTER = 24.hours
    }
}
