import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Architecture rules over the sources of :app and every shared module: a plain JVM test module that never puts the
// app or a shared module on its classpath, because Konsist parses source files and needs no compiled classes.
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

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.konsist)
    // Konsist 0.17.3 brings a 2.0.21 parser that misreads Kotlin 2.4 syntax.
    testRuntimeOnly(libs.konsist.kotlin.compiler.embeddable)
}

// Konsist reads sources, not classes: without these inputs the up-to-date check or the build cache would replay a
// stale result after a source-only edit. Exactly what the rules read: every Kotlin file and the values resources of
// :app, every source set and build script of the shared modules.
val scannedSources = isolated.rootProject.projectDirectory.let { root ->
    root.dir("app/src").asFileTree.matching { include("**/*.kt", "**/res/values*/*.xml") } +
        root.dir("shared").asFileTree.matching {
            include("*/src/**", "*/build.gradle.kts")
            exclude("**/build/**")
        }
}
tasks.test {
    inputs.files(scannedSources)
        .withPropertyName("scannedSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
