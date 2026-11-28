package dev.alllexey.itmowidgets.client.contract

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject

/**
 * A client field whose name Backend has agreed but not shipped yet; [reason] says why the client leads. The model
 * check skips it, and fails once the snapshot's [schema] has [property], so the entry is removed in the re-sync that
 * brings it.
 */
class PendingBackendField(val schema: String, val property: String, val reason: String) {
    override fun toString(): String = "$schema.$property ($reason)"
}

/**
 * A route the client calls before Backend's snapshot has it: [path] is the OpenAPI template. The route check skips a
 * request that matches no operation but this entry, and fails once the snapshot has the operation.
 */
class PendingBackendRoute(val method: HttpMethod, val path: String, val reason: String) {
    fun matches(method: HttpMethod, encodedPath: String): Boolean =
        Operation(this.method, path, JsonObject(emptyMap())).matches(method, encodedPath)

    override fun toString(): String = "${method.value} $path ($reason)"
}

/**
 * A query parameter the client sends to an existing operation before Backend's snapshot lists it; the route check
 * fails once the operation takes [name].
 */
class PendingBackendQuery(val method: HttpMethod, val path: String, val name: String, val reason: String) {
    override fun toString(): String = "${method.value} $path ?$name ($reason)"
}

private const val STEP_2 = "client platform step 2 (ADR 0040), built in parallel with Backend"

/**
 * Every [PendingBackendField]: client platform step 2's registry `returnUrl` of a client app and whether a connection
 * holds `identity`.
 */
object PendingBackendFields {
    val all: List<PendingBackendField> = listOf(
        PendingBackendField("ClientAppInfo", "returnUrl", STEP_2),
        PendingBackendField("ConnectionInfo", "identity", STEP_2),
    )
}

/** Every [PendingBackendRoute]. */
object PendingBackendRoutes {
    val all: List<PendingBackendRoute> = emptyList()
}

/** Every [PendingBackendQuery]. */
object PendingBackendQueries {
    val all: List<PendingBackendQuery> = emptyList()
}
