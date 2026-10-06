// A feature module: Compose UI, Res strings, host and screenshot tests (recipe kmp-module-build-file).
// Features read core and designsystem, never another feature.
plugins {
    id("itmowidgets.cmp.ui")
    id("itmowidgets.testing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:designsystem"))
            // The five social and picker ViewModels are KMP ViewModels that :app obtains through Koin (L13 LC-2,
            // recipe koin-module).
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
            // The screens' routes (L13 LC-3a on): koinViewModel() and lifecycle-aware collection; the iOS shell hosts
            // the same routes.
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

// :app reads these files as Android resources until --retire (scripts/strings-move.py, L05 KM-09b).
itmowidgetsStrings {
    androidExport("values/strings_social.xml")
}
