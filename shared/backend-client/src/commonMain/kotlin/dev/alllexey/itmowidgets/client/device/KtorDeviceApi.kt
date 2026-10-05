package dev.alllexey.itmowidgets.client.device

import dev.alllexey.itmowidgets.client.http.BackendBody
import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.http.BackendRoute
import dev.alllexey.itmowidgets.client.http.jsonBody
import io.ktor.http.HttpMethod

internal class KtorDeviceApi(private val http: BackendHttp) : DeviceApi {

    override suspend fun register(request: RegisterDeviceRequest) =
        http.callUnit(route(HttpMethod.Post, "register-device", jsonBody(request)))

    override suspend fun unregisterCurrent(request: UnregisterDeviceRequest) =
        http.callUnit(route(HttpMethod.Delete, "current", jsonBody(request)))

    private fun route(method: HttpMethod, segment: String, body: BackendBody) =
        BackendRoute(method, listOf("api", "device", segment), body = body)
}
