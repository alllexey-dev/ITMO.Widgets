package dev.alllexey.itmowidgets.di.bridge

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import dev.alllexey.itmowidgets.feature.auth.di.authDataModule
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleDataModule
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleModule
import dev.alllexey.itmowidgets.feature.settings.di.settingsDataModule
import kotlin.reflect.KClass
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * The graph check every Koin module goes through (ADR 0019), on the plain JVM.
 *
 * - `verify()` fails on a missing binding or a cycle in the [constructed][KoinModules.constructed] modules: it reads
 *   the constructor of each definition's type. Bridged types count as given, since Hilt constructs them;
 *   `KoinStartTest` resolves the bridges on the real application.
 * - Loading every module with `allowOverride(false)` fails on a type defined in two modules.
 * - Koin keeps the last of two definitions of one type inside one module without an error; `DiRulesTest` catches
 *   that in the source.
 */
internal object KoinGraphCheck {

    /** Supplied by the platform, `androidContext()` or the caller (`SavedStateHandle` from `CreationExtras`). */
    private val platformTypes = listOf(
        Context::class,
        Application::class,
        SavedStateHandle::class,
        WorkerParameters::class,
    )

    @OptIn(KoinExperimentalAPI::class)
    fun assertValid(bridges: List<Module>, constructed: List<Module>) {
        module { includes(constructed) }.verify(extraTypes = platformTypes + typesDefinedIn(bridges))
        koinApplication {
            allowOverride(false)
            modules(bridges + constructed)
        }.close()
    }

    @OptIn(KoinInternalApi::class)
    private fun typesDefinedIn(modules: List<Module>): List<KClass<*>> {
        val application = koinApplication { modules(modules) }
        return try {
            application.koin.instanceRegistry.instances.values
                .map { factory -> factory.beanDefinition }
                .flatMap { definition -> listOf(definition.primaryType) + definition.secondaryTypes }
                .distinct()
        } finally {
            application.close()
        }
    }
}

/**
 * The schedule data Koin constructs (`scheduleDataModule`, KM-11a2) with the modules it reads (the demo flag, the
 * opt-in and the schedule preferences, the selectors). A feature that reads the schedule gateways or the change
 * tracking checks its module together with these.
 */
internal val scheduleDataGraph: List<Module>
    get() = listOf(authDataModule, settingsDataModule, scheduleModule, scheduleDataModule)
