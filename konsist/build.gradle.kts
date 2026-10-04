import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Architecture rules over the sources of :app and every shared module; L06 KN-02a writes the body.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Konsist reads sources, not classes: without these inputs the up-to-date check or the build cache would replay a
// stale result after a source-only edit.
val scannedSources = isolated.rootProject.projectDirectory.let { root ->
    root.dir("app/src").asFileTree + root.dir("shared").asFileTree.matching { include("*/src/**") }
}
tasks.test {
    inputs.files(scannedSources)
        .withPropertyName("scannedSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
