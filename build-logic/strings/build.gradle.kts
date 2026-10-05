plugins {
    `kotlin-dsl`
}

// compileOnly: :convention already puts AGP on the build classpath; this module only compiles against its
// variant API (the generated res dir of :app).
dependencies {
    compileOnly(libs.android.gradlePlugin)
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
