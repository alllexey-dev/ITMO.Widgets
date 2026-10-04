package dev.alllexey.itmowidgets.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * JVM screenshot tests (ADR 0022): Roborazzi, Robolectric and ComposablePreviewScanner on the host tests of a
 * shared module (`androidHostTest`) or of `:app` (`test`), Robolectric offline, and the `screenshotsRecord` /
 * `screenshotsVerify` lifecycle tasks that `scripts/verify.sh shots` calls. L08's DS-02a makes them capture.
 */
class TestingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")

        val hostTestLibraries = HOST_TEST_LIBRARIES.map { libs.library(it) }
        pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.matching { it.name == "androidHostTest" }.configureEach {
                    dependencies {
                        hostTestLibraries.forEach { implementation(it) }
                    }
                }
            }
        }
        pluginManager.withPlugin("com.android.application") {
            extensions.configure<ApplicationExtension> {
                testOptions.unitTests.isIncludeAndroidResources = true
            }
            dependencies {
                hostTestLibraries.forEach { "testImplementation"(it) }
            }
        }

        // Robolectric offline: Gradle resolves android-all into build/, so the test JVM never writes ~/.m2.
        val robolectricRuntime = configurations.create("robolectricRuntime")
        dependencies {
            robolectricRuntime(libs.library("robolectric-android-all"))
        }
        val prepareRobolectricRuntime = tasks.register<Sync>("prepareRobolectricRuntime") {
            from(robolectricRuntime)
            into(layout.buildDirectory.dir("robolectric-deps"))
        }
        tasks.withType<Test>().configureEach {
            // JVM default locale en-US, as on an English-language device (SP-13a plural forms).
            jvmArgs("-Duser.language=en", "-Duser.country=US")
            jvmArgumentProviders.add(
                objects.newInstance<RobolectricOfflineArguments>().apply {
                    dependencyDir.set(layout.dir(prepareRobolectricRuntime.map { it.destinationDir }))
                },
            )
        }

        tasks.register("screenshotsRecord") {
            group = "verification"
            description = "Records this module's screenshot goldens (scripts/verify.sh shots <module> --record)."
        }
        tasks.register("screenshotsVerify") {
            group = "verification"
            description = "Compares this module's screenshots with the goldens (scripts/verify.sh shots <module>)."
        }
        verifyQuickTask()
        Unit
    }

    private companion object {
        val HOST_TEST_LIBRARIES = listOf(
            "junit",
            "kotlin-test",
            "robolectric",
            "androidx-test-core",
            "androidx-compose-ui-test-junit4",
            "androidx-compose-ui-test-manifest",
            "roborazzi",
            "roborazzi-compose",
            "roborazzi-compose-preview-scanner-support",
            "composable-preview-scanner-android",
        )
    }
}

/** Robolectric reads its android-all jars from [dependencyDir] and never downloads. */
abstract class RobolectricOfflineArguments : CommandLineArgumentProvider {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val dependencyDir: DirectoryProperty

    override fun asArguments(): Iterable<String> = listOf(
        "-Drobolectric.offline=true",
        "-Drobolectric.dependency.dir=${dependencyDir.get().asFile.absolutePath}",
    )
}
