plugins {
    `kotlin-dsl`
}

// Implementation, not compileOnly: these jars are the one copy of each plugin on the build classpath. The root
// build loads build-logic once (apply false), so every module sees the same AGP, Kotlin and CMP classes.
dependencies {
    implementation(project(":strings"))
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.composeCompiler.gradlePlugin)
    implementation(libs.kotlin.serialization.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
    implementation(libs.google.services.gradlePlugin)
    implementation(libs.roborazzi.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApp") {
            id = "itmowidgets.android.app"
            implementationClass = "dev.alllexey.itmowidgets.buildlogic.AndroidAppConventionPlugin"
        }
        register("kmpLibrary") {
            id = "itmowidgets.kmp.library"
            implementationClass = "dev.alllexey.itmowidgets.buildlogic.KmpLibraryConventionPlugin"
        }
        register("cmpUi") {
            id = "itmowidgets.cmp.ui"
            implementationClass = "dev.alllexey.itmowidgets.buildlogic.CmpUiConventionPlugin"
        }
        register("testing") {
            id = "itmowidgets.testing"
            implementationClass = "dev.alllexey.itmowidgets.buildlogic.TestingConventionPlugin"
        }
    }
}
