// Advisory only (TC-08): the dependency-analysis plugin reaches the classpath, and `buildHealth` exists, only with
// -Pitmo.buildHealth=true, which android-nightly.yml passes. Its AGP 9.3 and Android-KMP support is unproven, so
// no other build loads it and a failure there never reaches verify.sh or android-ci.yml. The buildscript block
// is compiled apart from the script and cannot see the `buildHealth` value below.
buildscript {
    if (providers.gradleProperty("itmo.buildHealth").orNull == "true") {
        repositories { gradlePluginPortal() }
        val plugin = libs.plugins.dependency.analysis.get()
        dependencies { classpath("${plugin.pluginId}:${plugin.pluginId}.gradle.plugin:${plugin.version}") }
    }
}

// Loads build-logic once for every module: its dependencies are the only copy of AGP, Kotlin, CMP, KSP, Hilt,
// google-services and Roborazzi on the build classpath, so no plugin here or in a module carries a version.
plugins {
    id("itmowidgets.android.app") apply false
}

// Applied to every project: each analyses itself, the root aggregates the reports into buildHealth.
val buildHealth = providers.gradleProperty("itmo.buildHealth").orNull == "true"
if (buildHealth) {
    val dependencyAnalysis = libs.plugins.dependency.analysis.get().pluginId
    allprojects { apply(plugin = dependencyAnalysis) }
}

// The aggregates scripts/verify.sh runs (quick, full, klibs). Every convention registers `itmoVerifyQuick`;
// the iOS umbrella and :konsist apply none, so they are left out of that list.
val modulePaths = subprojects.map { it.path }
val sharedPaths = modulePaths.filter { it.startsWith(":shared:") }
val conventionPaths = modulePaths.filter { it == ":app" || (it.startsWith(":shared:") && it != ":shared:ios") }
val buildLogic = gradle.includedBuild("build-logic")

val verifyQuick = tasks.register("verifyQuick") {
    group = "verification"
    description = "Unit and host tests of every module, Konsist, build-logic tests, lintGithubDebug, both debug APKs."
    dependsOn(conventionPaths.map { "$it:itmoVerifyQuick" })
    dependsOn(":konsist:test")
    dependsOn(buildLogic.task(":convention:test"), buildLogic.task(":strings:test"))
    dependsOn(":app:lintGithubDebug", ":app:assembleGithubDebug", ":app:assemblePlayDebug")
}

tasks.register("verifyFull") {
    group = "verification"
    description = "verifyQuick plus lintPlayDebug: the Android part of the AGENTS.md build command."
    dependsOn(verifyQuick, ":app:lintPlayDebug")
}

// Compile-only: linking a framework or running iosSimulatorArm64Test needs Xcode (SP-10, T10).
tasks.register("verifyIosKlibs") {
    group = "verification"
    description = "iosSimulatorArm64 klibs of every shared module (needs no Xcode; runs on Linux x86_64 too)."
    dependsOn(sharedPaths.map { "$it:compileKotlinIosSimulatorArm64" })
}
