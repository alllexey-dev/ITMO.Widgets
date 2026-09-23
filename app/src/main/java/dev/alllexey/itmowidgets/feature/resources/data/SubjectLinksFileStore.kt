package dev.alllexey.itmowidgets.feature.resources.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.model.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.model.resources.SaveSubjectLinkRequest
import dev.alllexey.itmowidgets.core.model.resources.SubjectLinksResponse
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject

/** A PRIVATE link saved without the opt-in; [request] is what the first refresh with the opt-in sends. */
internal data class LocalLink(val id: String, val request: SaveSubjectLinkRequest, val updatedAt: OffsetDateTime) {
    val scope: ResourceScope get() = ResourceScope(request.subjectId, request.subjectName, request.periodKey)
}

/** A pin of an own link made without the opt-in. */
internal data class LocalPin(val scope: ResourceScope, val linkId: String)

/** The last server answer for a scope. */
internal data class CachedLinks(val scope: ResourceScope, val response: SubjectLinksResponse)

internal data class StoredLinks(
    val format: Int = 1,
    val local: Map<String, LocalLink> = emptyMap(),
    val localPins: Map<String, LocalPin> = emptyMap(),
    val scopes: Map<String, CachedLinks> = emptyMap(),
)

/** Persistent local links and server snapshots, never cacheDir. Caller owns IO dispatch and serialization. */
class SubjectLinksFileStore internal constructor(private val directory: File, private val gson: Gson) {
    @Inject constructor(@ApplicationContext context: Context, gson: Gson) : this(File(context.filesDir, "subject_links"), gson)

    private val file get() = File(directory, "cache.json")

    internal fun read(): StoredLinks {
        if (!file.exists()) return StoredLinks()
        val tree = JsonParser.parseString(file.readText()).asJsonObject
        check(tree.get("format")?.asInt == 1 && tree.get("local")?.isJsonObject == true) { "Invalid subject links store" }
        val state = checkNotNull(gson.fromJson(tree, StoredLinks::class.java))
        // Corruption must not silently discard device-only links by being replaced with an empty store.
        checkNotNull(state.local).forEach { (id, link) ->
            UUID.fromString(id)
            check(link.id == id && link.request.visibility == LinkVisibility.PRIVATE)
            check(link.scope.valid() && link.request.url.isNotBlank() && link.request.category.name.isNotEmpty())
            checkNotNull(link.updatedAt)
        }
        checkNotNull(state.localPins).forEach { (key, pin) ->
            UUID.fromString(pin.linkId)
            check(pin.scope.valid() && pin.scope.key == key)
        }
        checkNotNull(state.scopes).forEach { (key, cached) ->
            check(cached.scope.valid() && cached.scope.key == key)
            checkNotNull(cached.response.mine); checkNotNull(cached.response.shared); checkNotNull(cached.response.previous)
        }
        return state
    }

    internal fun write(state: StoredLinks) {
        check(directory.isDirectory || directory.mkdirs())
        val temporary = File(directory, "cache.json.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(gson.toJson(state).toByteArray(Charsets.UTF_8)); stream.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    internal fun clear() { check(!directory.exists() || directory.deleteRecursively()) }

    private fun ResourceScope.valid() = subjectId > 0 && subjectName.isNotBlank() && periodKey.matches(PERIOD_KEY)

    private companion object {
        val PERIOD_KEY = Regex("[0-9]{4}-[12]")
    }
}
