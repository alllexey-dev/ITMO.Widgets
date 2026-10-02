package dev.alllexey.itmowidgets.core.testing

import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The demo switch of one test; off unless the test turns it on. */
class FakeDemoMode(active: Boolean = false) : DemoMode {
    val active = MutableStateFlow(active)

    override suspend fun isActive(): Boolean = active.value

    override fun observeActive(): Flow<Boolean> = active
}

/** A session that is not the demo. */
fun noDemo(): DemoMode = FakeDemoMode()

/** An academic clock standing still at [at] in Moscow. */
class FixedAcademicTime(
    private val at: LocalDateTime = LocalDateTime.of(LocalDate.of(2026, 10, 7), LocalTime.NOON)
) : AcademicTimeProvider {
    override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")

    override fun today(): LocalDate = at.toLocalDate()

    override fun now(): OffsetDateTime = at.atZone(zoneId).toOffsetDateTime()
}

/** A client interface every call of which fails the test: the demo session must not reach it. */
inline fun <reified T : Any> unreachable(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
    if (method.declaringClass == Any::class.java) return@newProxyInstance method.name.hashCode()
    throw AssertionError("The demo session called ${T::class.java.simpleName}.${method.name}")
} as T

/** My ITMO whose every request fails the test. */
fun unreachableMyItmo(): MyItmo = myItmoResponses { request -> throw AssertionError("The demo session asked My ITMO for ${request.url.encodedPath}") }
