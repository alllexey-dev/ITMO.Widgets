plugins {
    `kotlin-dsl`
}

// compileOnly: :convention already puts AGP and Kotlin on the build classpath; this module only compiles against
// AGP's variant API (the generated res dir of :app) and the KMP source sets (the key objects).
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("strings") {
            id = "itmowidgets.strings"
            implementationClass = "dev.alllexey.itmowidgets.buildlogic.strings.StringsConventionPlugin"
        }
    }
}
