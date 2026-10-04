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
 * A shared module with Compose UI: itmowidgets.kmp.library plus Compose Multiplatform, the Compose compiler and
 * `composeResources` served through Android resources. The `Res` class lives in the module namespace and is public
 * only in the modules other modules read strings and icons from.
 */
class CmpUiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("itmowidgets.kmp.library")
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
                publicResClass = path in PUBLIC_RES_MODULES
                generateResClass = ResourcesExtension.ResourceClassGeneration.Always
            }
        }
    }

    private companion object {
        val PUBLIC_RES_MODULES = setOf(":shared:core", ":shared:designsystem")
    }
}
