pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// The dotted root name rules out type-safe project accessors: modules use string paths (`project(":shared:core")`).
rootProject.name = "ITMO.Widgets"
include(":app")

// The module graph of ADR 0018.
include(
    ":shared:core",
    ":shared:designsystem",
    ":shared:testing",
    ":shared:backend-client",
    ":shared:feature-qr",
    ":shared:feature-home",
    ":shared:feature-schedule",
    ":shared:feature-sport",
    ":shared:feature-recordbook",
    ":shared:feature-social",
    ":shared:feature-settings",
    ":shared:feature-resources",
    ":shared:feature-reviews",
    ":shared:feature-account",
    ":shared:ios",
)
include(":konsist")

// MyItmoApi comes from Maven Central (libs.my.itmo.api.kmp). To develop the library alongside the app, set
// MYITMOAPI_DIR to a my-itmo-api checkout: its KMP build (<checkout>/kmp, or the root) replaces the artifact as a
// composite build, and the catalog version is ignored.
val myItmoApiDir: String? = providers.environmentVariable("MYITMOAPI_DIR").orNull?.ifBlank { null }

if (myItmoApiDir != null) {
    val checkout = file(myItmoApiDir)
    val kmpBuild = listOf(checkout.resolve("kmp"), checkout).firstOrNull { it.resolve("settings.gradle.kts").isFile }
        ?: throw GradleException(
            "MYITMOAPI_DIR=$checkout has no Gradle build; set MYITMOAPI_DIR to a my-itmo-api checkout or unset it",
        )
    logger.lifecycle("MyItmoApi: including $kmpBuild")
    includeBuild(kmpBuild) {
        dependencySubstitution {
            // The KMP build's root project is the library.
            substitute(module("dev.alllexey:my-itmo-api-kmp")).using(project(":"))
        }
    }
}
