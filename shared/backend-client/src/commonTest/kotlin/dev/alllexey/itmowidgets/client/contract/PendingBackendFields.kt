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

private const val PROTOCOL_2 = "client platform step 1, protocol 2 (ADR 0040), built in parallel with Backend"

/**
 * Every [PendingBackendField]: the link flow options and the protocol-2 fields of the connections area. The types of
 * the latter ([ClientAppInfo], [GrantRequest], [ClientDeployment] of the connections package) join [WireModels] in
 * the re-sync that brings the schemas.
 */
object PendingBackendFields {
    val all: List<PendingBackendField> = listOf(
        PendingBackendField("SaveSubjectLinkRequest", "flowOptions", "link flow options, Backend feat/link-flow-options"),
        PendingBackendField("PairingPreview", "protocol", PROTOCOL_2),
        PendingBackendField("PairingPreview", "app", PROTOCOL_2),
        PendingBackendField("PairingPreview", "grants", PROTOCOL_2),
        PendingBackendField("ApprovePairingRequest", "granted", PROTOCOL_2),
        PendingBackendField("ConnectionInfo", "clientId", PROTOCOL_2),
        PendingBackendField("ConnectionInfo", "app", PROTOCOL_2),
        PendingBackendField("ConnectionInfo", "itmoGrants", PROTOCOL_2),
    )
}

/** Every [PendingBackendRoute]. */
object PendingBackendRoutes {
    val all: List<PendingBackendRoute> = listOf(
        PendingBackendRoute(HttpMethod.Post, "/api/users/me/connections/{id}/itmo-closed", PROTOCOL_2),
    )
}

/** Every [PendingBackendQuery]. */
object PendingBackendQueries {
    val all: List<PendingBackendQuery> = listOf(
        PendingBackendQuery(HttpMethod.Post, "/api/users/me/pair/{code}/open", "protocol", PROTOCOL_2),
    )
}
