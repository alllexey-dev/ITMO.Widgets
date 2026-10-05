package dev.alllexey.itmowidgets.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.WriteProperties
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * JVM screenshot tests (ADR 0022): Roborazzi, Robolectric and ComposablePreviewScanner on the host tests of a
 * shared module (`androidHostTest`) or of `:app` (`test`), Robolectric offline on a fixed SDK with native
 * graphics, and the `screenshotsRecord` / `screenshotsVerify` tasks that `scripts/verify.sh shots` calls.
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

        // Robolectric offline: Gradle resolves android-all into its own cache, so the test JVM never writes ~/.m2.
        // The jar is read in place: a copy per module would cost about 200 MB in each of 15 modules per worktree.
        val robolectricRuntime = configurations.create("robolectricRuntime") {
            isTransitive = false
        }
        dependencies {
            robolectricRuntime(libs.library("robolectric-android-all"))
        }
        tasks.withType<Test>().configureEach {
            // JVM default locale en-US, as on an English-language device (SP-13a plural forms).
            jvmArgs("-Duser.language=en", "-Duser.country=US")
            jvmArgumentProviders.add(
                objects.newInstance<RobolectricOfflineArguments>().apply {
                    runtimeJars.from(robolectricRuntime)
                },
            )
        }

        configureScreenshots()
        verifyQuickTask()
        Unit
    }

    /**
     * Screenshot tests are the `*ScreenshotTest` classes of the host tests ([SCREENSHOT_HOST_TEST]). Every host-test
     * run excludes them, so `verifyQuick` never renders; `screenshotsRecord` / `screenshotsVerify` run Roborazzi's
     * record / verify task for that one test task with only those classes, goldens in `<module>/screenshots/`.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun Project.configureScreenshots() {
        extensions.configure<RoborazziExtension> {
            outputDir.set(layout.projectDirectory.dir(SCREENSHOTS_DIR))
            compare {
                outputDir.set(layout.buildDirectory.dir("outputs/roborazzi"))
            }
        }

        val robolectricProperties = tasks.register<WriteProperties>("robolectricProperties") {
            description = "Pins the Robolectric SDK of every host test to the one android-all jar Gradle resolves."
            destinationFile.set(layout.buildDirectory.file("generated/robolectric/robolectric.properties"))
            property("sdk", ROBOLECTRIC_SDK)
        }
        val robolectricPropertiesDir = robolectricProperties.map { it.destinationFile.get().asFile.parentFile }
        pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.matching { it.name == "androidHostTest" }.configureEach {
                    resources.srcDir(robolectricPropertiesDir)
                }
            }
        }
        pluginManager.withPlugin("com.android.application") {
            extensions.configure<ApplicationExtension> {
                sourceSets.named("test") {
                    resources.directories.add(robolectricPropertiesDir.get().path)
                }
            }
            tasks.matching { it.name.startsWith("process") && it.name.endsWith("UnitTestJavaRes") }.configureEach {
                dependsOn(robolectricProperties)
            }
        }

        tasks.withType<Test>().configureEach {
            systemProperty("robolectric.graphicsMode", "NATIVE")
            // captureRoboImage("<PreviewName>_<appearance>.png") lands in the module's screenshots/.
            systemProperty("roborazzi.record.filePathStrategy", "relativePathFromRoborazziContextOutputDirectory")
            filter.excludeTestsMatching(SCREENSHOT_TEST_PATTERN)
        }

        val hostTest = if (path == ":app") APP_SCREENSHOT_HOST_TEST else SCREENSHOT_HOST_TEST
        val record = tasks.register("screenshotsRecord") {
            group = "verification"
            description = "Records this module's screenshot goldens (scripts/verify.sh shots <module> --record)."
            dependsOn("recordRoborazzi${hostTest.roborazziSuffix}")
        }
        val verify = tasks.register("screenshotsVerify") {
            group = "verification"
            description = "Compares this module's screenshots with the goldens (scripts/verify.sh shots <module>)."
            dependsOn("verifyRoborazzi${hostTest.roborazziSuffix}")
        }

        // The filter is part of the test task's inputs, so it is set before the task runs and a screenshot run
        // never shares a cache entry or up-to-date state with a plain host-test run.
        val shotsProperties = providers.gradlePropertiesPrefixedBy("shots.")
        gradle.taskGraph.whenReady {
            if (!hasTask(record.get()) && !hasTask(verify.get())) return@whenReady
            tasks.named<Test>(hostTest.taskName).configure {
                filter.setExcludePatterns()
                filter.includeTestsMatching(SCREENSHOT_TEST_PATTERN)
                filter.isFailOnNoMatchingTests = false
                systemProperties(shotsProperties.get())
                // Goldens are compared on every run, never replayed from the cache.
                outputs.upToDateWhen { false }
                outputs.cacheIf { false }
            }
        }
    }

    /** The host-test task that screenshot tests run on and Roborazzi's task-name suffix for it. */
    private class ScreenshotHostTest(val taskName: String, val roborazziSuffix: String)

    private companion object {
        const val SCREENSHOTS_DIR = "screenshots"
        const val SCREENSHOT_TEST_PATTERN = "*ScreenshotTest"

        /** The SDK of `robolectric-android-all` in the catalog (android-all-instrumented 15 = API 35). */
        const val ROBOLECTRIC_SDK = 35

        val SCREENSHOT_HOST_TEST = ScreenshotHostTest("testAndroidHostTest", "AndroidHostTest")
        val APP_SCREENSHOT_HOST_TEST = ScreenshotHostTest("testGithubDebugUnitTest", "GithubDebug")

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

/**
 * Robolectric reads its android-all jar from the directory that holds [runtimeJars] (the Gradle cache keeps the
 * Maven file name Robolectric looks for) and never downloads.
 */
abstract class RobolectricOfflineArguments : CommandLineArgumentProvider {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val runtimeJars: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> = listOf(
        "-Drobolectric.offline=true",
        "-Drobolectric.dependency.dir=${runtimeJars.singleFile.parentFile.absolutePath}",
    )
}
