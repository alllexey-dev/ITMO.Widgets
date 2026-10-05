package dev.alllexey.itmowidgets.client.app

import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.client.support.assertRequest
import dev.alllexey.itmowidgets.client.support.recordRequest
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

/** The method, path, query and body of every [AppApi] call ([AppRouteCases]). */
class AppRequestTest {

    @Test
    fun everyPublicFunctionHasOneCase() {
        assertEquals(1, AppRouteCases.all.size)
        assertEquals(AppRouteCases.all.size, AppRouteCases.all.map { it.name }.toSet().size)
    }

    @Test
    fun versionInfo() = runSuspend {
        AppRouteCases.versionInfo.assertRequest(HttpMethod.Get, "/api/app/version-info", listOf("platform" to "IOS"))
    }

    @Test
    fun versionInfoSendsEachPlatformByItsWireName() = runSuspend {
        for (platform in DevicePlatform.entries) {
            val request = recordRequest("versionInfo") { client.app.versionInfo(platform) }

            assertEquals("/api/app/version-info", request.path)
            assertEquals(listOf("platform" to platform.name), request.query)
        }
    }

    @Test
    fun versionInfoWithoutPlatformSendsNoQuery() = runSuspend {
        val request = recordRequest("versionInfo") { client.app.versionInfo(null) }

        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/app/version-info", request.path)
        assertEquals(emptyList(), request.query)
        assertEquals(null, request.body)
    }
}
