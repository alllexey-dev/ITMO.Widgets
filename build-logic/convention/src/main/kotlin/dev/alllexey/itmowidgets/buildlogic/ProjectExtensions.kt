package dev.alllexey.itmowidgets.buildlogic

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import java.io.File

internal const val BASE_PACKAGE = "dev.alllexey.itmowidgets"

/** The per-module lifecycle task the root `verifyQuick` aggregates; module checks attach here. */
internal const val VERIFY_QUICK_TASK = "itmoVerifyQuick"

/** The per-module task the root `verifyIosKlibs` aggregates: the iosSimulatorArm64 klib where the module has one. */
internal const val IOS_KLIB_TASK = "itmoIosKlib"

/**
 * Core fakes shared by the tests of `:shared:core`, `:shared:compose-strings`, the ten `:shared:feature-*` modules
 * and `:app`. One source root in several modules instead of a separate fixtures module; never in `:shared:testing`
 * (core-free) or `:shared:designsystem` (its tests need no core fakes).
 */
internal val Project.coreTestFixturesDir: Directory
    get() = isolated.rootProject.projectDirectory.dir("shared/core/src/testFixtures/kotlin")

/** The one lint configuration of `:app` and every shared module. */
internal val Project.lintConfigFile: File
    get() = isolated.rootProject.projectDirectory.file("app/lint.xml").asFile

/** Whether this module's tests compile [coreTestFixturesDir]. */
internal val Project.usesCoreTestFixtures: Boolean
    get() = path == ":shared:core" || path == ":shared:compose-strings" || path.startsWith(":shared:feature-")

/** The source sets the host test task compiles. */
internal val HOST_TEST_SOURCE_SETS = listOf("commonTest", "androidHostTest")

/** The source sets the iOS simulator test task compiles. */
internal val IOS_TEST_SOURCE_SETS = listOf("commonTest", "nativeTest", "appleTest", "iosTest", "iosSimulatorArm64Test")

/**
 * Whether a module in [projectDir] has sources of its own in one of [testSourceSets], the source sets one test task
 * compiles, besides [coreTestFixturesDir]: `androidHostTest` alone gives the iOS test task nothing to run.
 */
internal fun hasOwnTestSources(projectDir: File, testSourceSets: List<String>): Boolean =
    testSourceSets.any { File(projectDir, "src/$it").isDirectory }

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
            description = "Fast checks of this module that the root verifyQuick runs."
        }
    }

/** Makes [VERIFY_QUICK_TASK] depend on the tasks named [names], whenever they get registered. */
internal fun Project.verifyQuickDependsOn(names: (String) -> Boolean) {
    verifyQuickTask().configure {
        dependsOn(tasks.named { names(it) })
    }
}
