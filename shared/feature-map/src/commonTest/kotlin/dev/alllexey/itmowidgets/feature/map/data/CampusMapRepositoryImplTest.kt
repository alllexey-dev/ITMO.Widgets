package dev.alllexey.itmowidgets.feature.map.data

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.map.MapFixtures
import dev.alllexey.itmowidgets.feature.map.data.mirror.CampusMapApi
import dev.alllexey.itmowidgets.feature.map.data.mirror.CampusMapEndpoint
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class CampusMapRepositoryImplTest {
    private val requests = mutableListOf<String>()
    private var online = true
    private var files = MapFixtures.FILES

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath.removePrefix("/app/v1/")
        requests += path
        when {
            !online -> respondError(HttpStatusCode.ServiceUnavailable)
            path == "manifest.json" -> respond(MapFixtures.MANIFEST)
            path in files -> respond(files.getValue(path))
            else -> respondError(HttpStatusCode.NotFound)
        }
    }
    private val fileSystem = FakeFileSystem()

    private fun repository(dispatchers: AppDispatchers) = CampusMapRepositoryImpl(
        CampusMapApi(HttpClient(engine), CampusMapEndpoint("https://map.example/app/v1"), FakeDemoMode()),
        "/cache/campus_map".toPath(),
        fileSystem,
        dispatchers,
    )

    @Test
    fun aPlanIsDownloadedOnceAndDecoded() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = repository(AppDispatchers(dispatcher, dispatcher, dispatcher))

        val plan = assertIs<AppResult.Success<*>>(repository.plan(13)).value as dev.alllexey.itmowidgets.feature.map.domain.catalog.LoadedPlan
        repository.plan(13)

        assertEquals(469L, plan.plan.entrance?.id)
        assertTrue(plan.tilesPath.startsWith("/cache/campus_map/files/"))
        assertEquals(1, requests.count { it == "manifest.json" })
        assertEquals(1, requests.count { it.endsWith(".mbtiles") })
    }

    @Test
    fun theStoredManifestAndFilesServeOffline() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        repository(AppDispatchers(dispatcher, dispatcher, dispatcher)).plan(13)
        online = false

        val offline = repository(AppDispatchers(dispatcher, dispatcher, dispatcher))

        assertIs<AppResult.Success<*>>(offline.plan(13))
        assertEquals(listOf(13, 273), (offline.catalog() as AppResult.Success).value.buildings.map { it.id })
    }

    @Test
    fun aFileThatDoesNotMatchItsHashIsRejected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        files = MapFixtures.FILES.mapValues { (path, body) -> if (path.endsWith(".mbtiles")) "tampered" else body }

        val result = repository(AppDispatchers(dispatcher, dispatcher, dispatcher)).plan(13)

        assertEquals(AppResult.Failure(AppError.Network), result)
        assertTrue(fileSystem.listRecursively("/".toPath()).none { it.name.endsWith(".mbtiles") })
    }

    @Test
    fun noMirrorAndNoCopyIsANetworkError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        online = false

        assertEquals(AppResult.Failure(AppError.Network), repository(AppDispatchers(dispatcher, dispatcher, dispatcher)).catalog())
    }

    @Test
    fun theGlyphsTemplateKeepsItsTokens() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val catalog = (repository(AppDispatchers(dispatcher, dispatcher, dispatcher)).catalog() as AppResult.Success).value

        assertEquals("https://map.example/app/v1/glyphs/{fontstack}/{range}.pbf", catalog.glyphsUrl)
    }
}
