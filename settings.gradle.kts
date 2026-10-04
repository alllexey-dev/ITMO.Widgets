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

// The 16 v2.3 modules (master plan section 3.1, ADR 0018); there is no 17th module in v2.3.
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
