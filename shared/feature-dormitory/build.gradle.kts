// The dormitory (docs/features/dormitory.md): the Этап общежития, the assigned dormitory and the Документы для
// заселения read from my.itmo through MyItmoApi, the last answer kept on the device. Features read core, designsystem
// and compose-strings, never another feature. Compose UI is Android-only (ADR 0035).
plugins {
    id("itmowidgets.cmp.android")
    id("itmowidgets.testing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            // The dormitory ViewModel is a KMP ViewModel that :app obtains through Koin and iOS through SKIE.
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
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
