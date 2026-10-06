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
            // The auth, onboarding and Me ViewModels are KMP ViewModels that :app obtains through Koin (L16 LA-2a,
            // recipe koin-module).
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
            // The account routes (L16 LA-3 on): koinViewModel() and lifecycle-aware collection; the iOS shell hosts
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
    androidExport("values/strings_auth.xml")
    androidExport("values/strings_onboarding.xml")
    androidExport("values/strings_weblogin.xml")
    androidExport("values/strings_update.xml")
    androidExport("values/strings_web.xml")
}
