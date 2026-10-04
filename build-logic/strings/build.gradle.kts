plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("strings") {
            id = "itmowidgets.strings"
            implementationClass = "StringsConventionPlugin"
        }
    }
}
