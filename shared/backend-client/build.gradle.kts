// Core 2.0, the typed Backend client (L19). itmowidgets.kmp.library brings kotlinx.serialization
// (recipe kmp-module-build-file).
plugins {
    id("itmowidgets.kmp.library")
}

kotlin {
    sourceSets {
        // No Ktor engine artifact: the caller passes the engine (OkHttp on Android, Darwin on iOS, ADR 0026).
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.okio)
        }
    }
}
