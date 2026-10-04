package dev.alllexey.itmowidgets.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A shared module: the Android-KMP library plugin (`kotlin { android { } }`), iosArm64 and iosSimulatorArm64,
 * JVM 17, host tests with Android resources and default return values (parity with `:app`, whose moved tests log
 * through `android.util.Log`), kotlinx.serialization. `:shared:core` and the features also compile the core test
 * fixtures in `commonTest`.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        extensions.configure<KotlinMultiplatformExtension> {
            extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                namespace = sharedNamespace
                compileSdk = compileSdkVersion
                minSdk = minSdkVersion
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
                withHostTest {
                    isIncludeAndroidResources = true
                    isReturnDefaultValues = true
                }
            }
            iosArm64()
            iosSimulatorArm64()

            compilerOptions {
                // expect/actual classes (CMP locale root, the Hilt bridge) are Beta and warn without this flag.
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }

            sourceSets.named("commonMain") {
                dependencies {
                    implementation(libs.library("kotlinx-serialization-json"))
                }
            }
            sourceSets.named("commonTest") {
                if (usesCoreTestFixtures) kotlin.srcDir(coreTestFixturesDir)
                dependencies {
                    implementation(libs.library("kotlin-test"))
                }
            }
        }

        verifyQuickDependsOn { it == "testAndroidHostTest" || it == "compileCommonMainKotlinMetadata" }
    }
}
