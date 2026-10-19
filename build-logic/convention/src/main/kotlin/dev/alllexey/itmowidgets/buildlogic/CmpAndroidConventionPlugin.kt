package dev.alllexey.itmowidgets.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * A shared module whose Compose UI is Android-only (ADR 0035): itmowidgets.kmp.library with the Compose compiler on
 * the Android target only, and the Compose, design system and Compose string dependencies in `androidMain`, so the
 * iOS klib links no Compose. The module generates no CMP `Res`: its catalog files stay in
 * `src/commonMain/composeResources` for itmowidgets.strings, and its screens read every string from
 * `:shared:compose-strings`' `Res` (keys are unique across the catalog).
 */
class CmpAndroidConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("itmowidgets.kmp.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("itmowidgets.strings")

        extensions.configure<ComposeCompilerGradlePluginExtension> {
            targetKotlinPlatforms.set(setOf(KotlinPlatformType.androidJvm))
        }

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.named("androidMain") {
                dependencies {
                    implementation(project(":shared:designsystem"))
                    implementation(project(":shared:compose-strings"))
                    implementation(libs.library("compose-runtime"))
                    implementation(libs.library("compose-foundation"))
                    implementation(libs.library("compose-ui"))
                    implementation(libs.library("compose-ui-tooling-preview"))
                }
            }
        }
    }
}
