package dev.alllexey.itmowidgets.di.bridge

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.error.DefinitionOverrideException
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

class KoinGraphTest {

    @Test
    fun `the release modules pass the graph check`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, KoinModules.constructed)
    }

    @Test
    fun `a constructed definition may depend on a bridged core contract`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(module { singleOf(::NeedsAcademicTime) }))
    }

    @Test
    fun `the graph check fails on a missing binding and names the type`() {
        val error = assertThrows(Throwable::class.java) {
            KoinGraphCheck.assertValid(emptyList(), listOf(module { singleOf(::NeedsDemoMode) }))
        }
        assertTrue(error.toString(), DemoMode::class.qualifiedName!! in error.message.orEmpty())
    }

    @Test
    fun `the graph check fails on a cycle and names the types`() {
        val error = assertThrows(Throwable::class.java) {
            KoinGraphCheck.assertValid(emptyList(), listOf(module { singleOf(::CycleA); singleOf(::CycleB) }))
        }
        assertTrue(error.toString(), CycleA::class.qualifiedName!! in error.message.orEmpty())
    }

    @Test
    fun `the graph check fails on a type defined in two modules`() {
        assertThrows(DefinitionOverrideException::class.java) {
            KoinGraphCheck.assertValid(
                emptyList(),
                listOf(
                    module { singleOf(::FirstPort) bind Port::class },
                    module { singleOf(::SecondPort) bind Port::class },
                ),
            )
        }
    }

    class NeedsDemoMode(@Suppress("unused") val demo: DemoMode)
    class NeedsAcademicTime(@Suppress("unused") val time: AcademicTimeProvider)

    class CycleA(@Suppress("unused") val b: CycleB)
    class CycleB(@Suppress("unused") val a: CycleA)

    interface Port
    class FirstPort : Port
    class SecondPort : Port
}
