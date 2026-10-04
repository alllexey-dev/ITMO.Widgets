package dev.alllexey.itmowidgets.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * `:app`: AGP with built-in Kotlin, the SDK levels from the catalog, JVM 17 and the Compose compiler for the
 * `ComposeView` hosts. Identity, flavors, signing and BuildConfig stay in `app/build.gradle.kts`. JVM tests also
 * compile the core test fixtures.
 */
class AndroidAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        pluginManager.apply("itmowidgets.strings")

        extensions.configure<ApplicationExtension> {
            compileSdk = compileSdkVersion
            defaultConfig {
                minSdk = minSdkVersion
                targetSdk = targetSdkVersion
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            buildFeatures {
                compose = true
            }
            testOptions {
                // Lets JVM tests exercise classes that log through android.util.Log.
                unitTests.isReturnDefaultValues = true
            }
            sourceSets.named("test") {
                kotlin.directories.add(coreTestFixturesDir.asFile.path)
            }
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }

        dependencies {
            // The Compose compiler refuses to run without the runtime on the classpath.
            "implementation"(libs.library("compose-runtime"))
            "implementation"(libs.library("kotlinx-serialization-json"))
        }

        // Konsist reads sources that are not compile inputs: an import-only edit leaves the bytecode
        // unchanged, so without this the up-to-date check or the build cache would replay a stale result.
        // G-03's StableIdentifiersTest also scans shared/*/src/*Main/kotlin; keep both after L06 KN-02a.
        val sharedSources = isolated.rootProject.projectDirectory.dir("shared").asFileTree.matching {
            include("*/src/**")
        }
        tasks.withType<Test>().configureEach {
            inputs.dir("src")
                .withPropertyName("konsistSources")
                .withPathSensitivity(PathSensitivity.RELATIVE)
            inputs.files(sharedSources)
                .withPropertyName("sharedSources")
                .withPathSensitivity(PathSensitivity.RELATIVE)
        }

        verifyQuickDependsOn { it.matches(DEBUG_UNIT_TEST) }
    }

    private companion object {
        val DEBUG_UNIT_TEST = Regex("test[A-Z]\\w*DebugUnitTest")
    }
}
