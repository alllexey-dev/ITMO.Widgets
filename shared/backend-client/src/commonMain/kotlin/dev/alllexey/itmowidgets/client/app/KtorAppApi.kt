package dev.alllexey.itmowidgets.client.app

import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.http.BackendRoute
import io.ktor.http.HttpMethod

internal class KtorAppApi(private val http: BackendHttp) : AppApi {

    override suspend fun versionInfo(platform: DevicePlatform?): AppVersionInfo =
        http.call(
            BackendRoute(
                HttpMethod.Get,
                listOf("api", "app", "version-info"),
                query = listOf("platform" to platform?.name),
            ),
            AppVersionInfo.serializer(),
        )
}
