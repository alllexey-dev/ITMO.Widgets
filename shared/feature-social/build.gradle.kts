// A feature module: shared domain, data and ViewModels; Android-only Compose UI in androidMain (ADR 0035).
// Features read core, designsystem and compose-strings, never another feature.
plugins {
    id("itmowidgets.cmp.android")
    id("itmowidgets.testing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            // The five social and picker ViewModels are KMP ViewModels that :app obtains through Koin.
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
        }
        androidMain.dependencies {
            // The Android routes: koinViewModel() and lifecycle-aware collection; iOS draws the ViewModels in SwiftUI.
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(project(":shared:testing"))
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}

// :app still reads these files as Android resources (R.string); drop an export once no app/ code uses its ids.
itmowidgetsStrings {
    androidExport("values/strings_social.xml")
}
