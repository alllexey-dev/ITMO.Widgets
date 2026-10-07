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
            // The recordbook ViewModels are KMP ViewModels that :app obtains through Koin (L12 LR-2a, recipe koin-module).
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
            // The screens' routes (L12 LR-3 on): koinViewModel() and lifecycle-aware collection; the iOS shell hosts
            // the same routes.
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            // SheetHtmlGrid parses the HTML view of a public sheet (L04 TC-11b, moved here by L12 KM-11b2).
            implementation(libs.ksoup)
        }
        // The public sheets' engine; MyITMO's and BARS's come from the app (L12 KM-11b2).
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        commonTest.dependencies {
            implementation(project(":shared:testing"))
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.koin.test)
            // The sheet repository's race tests hold a request on a real OkHttp engine.
            implementation(libs.okhttp.mockwebserver)
        }
    }
}

// :app reads these files as Android resources until --retire (scripts/strings-move.py, L05 KM-09b).
itmowidgetsStrings {
    androidExport("values/strings_recordbook.xml")
}
