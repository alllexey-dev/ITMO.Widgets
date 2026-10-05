// Loads build-logic once for every module: its dependencies are the only copy of AGP, Kotlin, CMP, KSP, Hilt,
// google-services and Roborazzi on the build classpath, so no plugin here or in a module carries a version.
plugins {
    id("itmowidgets.android.app") apply false
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
