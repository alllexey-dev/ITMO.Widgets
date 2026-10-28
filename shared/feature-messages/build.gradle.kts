// ISU messages (docs/features/isu-messages.md): the user's own inbox read on the device through core's IsuWebClient
// (ADR 0037). Features read core, designsystem and compose-strings, never another feature. Compose UI is
// Android-only (ADR 0035).
plugins {
    id("itmowidgets.cmp.android")
    id("itmowidgets.testing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            // The messages ViewModel is a KMP ViewModel that :app obtains through Koin and iOS through SKIE.
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
            // Message bodies are HTML: parsed into plain blocks, never rendered as HTML.
            implementation(libs.ksoup)
        }
        androidMain.dependencies {
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(project(":shared:testing"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}
