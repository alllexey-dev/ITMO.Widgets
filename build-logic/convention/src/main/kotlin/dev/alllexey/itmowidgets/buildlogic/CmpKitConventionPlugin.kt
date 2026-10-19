package dev.alllexey.itmowidgets.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `itmowidgets.cmp.kit`: the Compose kit and the Compose strings, which iOS never links (ADR 0035).
 * itmowidgets.kmp.android (the Android target only) plus Compose Multiplatform, the Compose compiler and
 * `composeResources` served through Android resources. The `Res` class lives in the module namespace and is public, since every feature's Android UI
 * reads its strings and icons. Features use itmowidgets.cmp.android instead.
 */
class CmpKitConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("itmowidgets.kmp.android")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("itmowidgets.strings")

        extensions.configure<KotlinMultiplatformExtension> {
            extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                androidResources {
                    enable = true
                }
            }
            sourceSets.named("commonMain") {
                dependencies {
                    implementation(libs.library("compose-runtime"))
                    implementation(libs.library("compose-foundation"))
                    implementation(libs.library("compose-ui"))
                    implementation(libs.library("compose-components-resources"))
                    implementation(libs.library("compose-ui-tooling-preview"))
                }
            }
        }

        extensions.configure<ComposeExtension> {
            (this as ExtensionAware).extensions.configure<ResourcesExtension> {
                packageOfResClass = sharedNamespace
                publicResClass = true
                generateResClass = ResourcesExtension.ResourceClassGeneration.Always
            }
        }
    }
}
