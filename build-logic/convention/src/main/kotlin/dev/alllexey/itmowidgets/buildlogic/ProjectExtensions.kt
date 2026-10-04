package dev.alllexey.itmowidgets.buildlogic

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType

internal const val BASE_PACKAGE = "dev.alllexey.itmowidgets"

/** The per-module lifecycle task scripts/verify.sh quick aggregates (TC-04b); later checks attach here. */
internal const val VERIFY_QUICK_TASK = "itmoVerifyQuick"

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalStateException("No library '$alias' in gradle/libs.versions.toml") }

internal fun VersionCatalog.intVersion(alias: String): Int =
    findVersion(alias).orElseThrow { IllegalStateException("No version '$alias' in gradle/libs.versions.toml") }
        .requiredVersion.toInt()

internal val Project.compileSdkVersion: Int get() = libs.intVersion("android-compileSdk")
internal val Project.minSdkVersion: Int get() = libs.intVersion("android-minSdk")
internal val Project.targetSdkVersion: Int get() = libs.intVersion("android-targetSdk")

/**
 * `dev.alllexey.itmowidgets.<path>` from the Gradle path with `:` and `-` turned into `.`: a namespace cannot
 * hold `-`, so `:shared:feature-qr` gives `dev.alllexey.itmowidgets.shared.feature.qr`.
 */
internal val Project.sharedNamespace: String
    get() = BASE_PACKAGE + "." + path.removePrefix(":").replace(':', '.').replace('-', '.')

/** Registers [VERIFY_QUICK_TASK] once per module; every convention may call it. */
internal fun Project.verifyQuickTask(): TaskProvider<Task> =
    if (VERIFY_QUICK_TASK in tasks.names) {
        tasks.named(VERIFY_QUICK_TASK)
    } else {
        tasks.register(VERIFY_QUICK_TASK) {
            group = "verification"
            description = "Fast checks of this module that scripts/verify.sh quick runs."
        }
    }

/** Makes [VERIFY_QUICK_TASK] depend on the tasks named [names], whenever they get registered. */
internal fun Project.verifyQuickDependsOn(names: (String) -> Boolean) {
    verifyQuickTask().configure {
        dependsOn(tasks.named { names(it) })
    }
}
