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
            // The recordbook ViewModels are KMP ViewModels that :app obtains through Koin and SwiftUI through the bridge.
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
            // SheetHtmlGrid parses the HTML view of a public sheet.
            implementation(libs.ksoup)
        }
        androidMain.dependencies {
            // The screens' routes: koinViewModel() and lifecycle-aware collection.
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            // The public sheets' engine; MyITMO's and BARS's come from the app.
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

// SheetRawDumpTest reads real sheets from a local folder: -PsheetScores.raw=<dir> (see docs/features/sheet-scores.md).
providers.gradleProperty("sheetScores.raw").orNull?.let { raw ->
    tasks.withType<Test>().configureEach { systemProperty("sheetScores.raw", raw) }
}
