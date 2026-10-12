// A feature module: Compose UI, Res strings, host and screenshot tests.
// Features read core, designsystem and compose-strings, never another feature.
plugins {
    id("itmowidgets.cmp.ui")
    id("itmowidgets.testing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:designsystem"))
            implementation(project(":shared:compose-strings"))
            // The recordbook ViewModels are KMP ViewModels that :app obtains through Koin.
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
            // The screens' routes: koinViewModel() and lifecycle-aware collection; the iOS shell hosts
            // the same routes.
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            // SheetHtmlGrid parses the HTML view of a public sheet.
            implementation(libs.ksoup)
        }
        // The public sheets' engine; MyITMO's and BARS's come from the app.
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

// :app still reads these files as Android resources (R.string); drop an export once no app/ code uses its ids.
itmowidgetsStrings {
    androidExport("values/strings_recordbook.xml")
}
