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

// MyItmoApi 2.x as a pinned composite build (ADR 0024, L04 TC-05; selection proven by SP-09). Precedence:
//   1. -PmyItmoApiFromCentral=true  release builds: my-itmo-api-kmp from the repositories (Central after gate M2)
//   2. -PmyItmoApiDir=<checkout>    scripts/verify.sh passes the worktree's lane pin
//   3. env MYITMOAPI_DIR=<checkout> CI checks gradle/myitmoapi.ref out and sets it
//   4. none of them                 no include; nothing needs 2.x before KM-10a
// <checkout> is the MyItmoApi root: the KMP build is <checkout>/kmp until ML-11b moves it to the root, so kmp/ is tried
// first. A missing directory fails the build; a Maven-only commit (no Gradle build yet) and a HEAD off the ref warn.
// The dependency keeps its catalog coordinates (libs.my.itmo.api.kmp); with an include the version is ignored.
// Contract for the included build (L20 ML-01): rootProject.name = "my-itmo-api-kmp", group = "dev.alllexey", builds
// standalone on this Gradle and Kotlin, no signing or credentials unless it publishes. Never mavenLocal().
val myItmoApiFromCentral = providers.gradleProperty("myItmoApiFromCentral").map(String::toBoolean).getOrElse(false)
val myItmoApiDir: String? = if (myItmoApiFromCentral) {
    null
} else {
    providers.gradleProperty("myItmoApiDir").orNull?.ifBlank { null }
        ?: providers.environmentVariable("MYITMOAPI_DIR").orNull?.ifBlank { null }
}

if (myItmoApiDir != null) {
    val checkout = file(myItmoApiDir)
    if (!checkout.isDirectory) {
        throw GradleException(
            "MyItmoApi checkout $checkout does not exist; run `~/proj/.wt/bin/lane pin <this worktree>` " +
                "or drop -PmyItmoApiDir / MYITMOAPI_DIR",
        )
    }
    val ref = layout.rootDirectory.file("gradle/myitmoapi.ref").asFile.takeIf { it.isFile }?.readText()?.trim()
    val head = if (checkout.resolve(".git").exists()) {
        providers.exec {
            commandLine("git", "-C", checkout.path, "rev-parse", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
    } else {
        null
    }
    if (ref != null && head != ref) {
        val at = head ?: "an unknown commit"
        logger.warn("MyItmoApi checkout $checkout is at $at, not at gradle/myitmoapi.ref $ref; run `lane pin`")
    }
    val kmpBuild = listOf(checkout.resolve("kmp"), checkout).firstOrNull { it.resolve("settings.gradle.kts").isFile }
    if (kmpBuild == null) {
        logger.warn("MyItmoApi checkout $checkout has no Gradle build (Maven-only commit); not included")
    } else {
        logger.lifecycle("MyItmoApi: including $kmpBuild")
        includeBuild(kmpBuild) {
            dependencySubstitution {
                // The KMP build's root project is the library.
                substitute(module("dev.alllexey:my-itmo-api-kmp")).using(project(":"))
            }
        }
    }
}
