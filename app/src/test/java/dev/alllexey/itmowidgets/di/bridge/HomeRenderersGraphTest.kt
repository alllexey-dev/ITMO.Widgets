package dev.alllexey.itmowidgets.di.bridge

import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The home feed draws each card with the renderer of its producing feature (L09 LH-4): across the release modules,
 * every `HomeCardKind` has exactly one `HomeCardRenderer`.
 */
class HomeRenderersGraphTest {

    @Test
    fun `every home card kind has exactly one renderer in the release modules`() {
        val application = koinApplication {
            allowOverride(false)
            modules(KoinModules.constructed + module { single<AcademicTimeProvider> { MoscowTime } })
        }
        try {
            val claims = application.koin.getAll<HomeCardRenderer>()
                .flatMap { renderer -> renderer.kinds.map { kind -> kind to renderer::class.simpleName } }
                .groupBy({ it.first }, { it.second })

            assertEquals(
                "renderers by kind: $claims",
                HomeCardKind.entries.associateWith { 1 },
                HomeCardKind.entries.associateWith { claims[it].orEmpty().size },
            )
        } finally {
            application.close()
        }
    }

    /** Only the zone is read, when a renderer is built. */
    private object MoscowTime : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")

        override fun today(): LocalDate = error("not read by the renderers")

        override fun now(): Instant = error("not read by the renderers")
    }
}
