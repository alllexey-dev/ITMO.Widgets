// A feature module: Compose UI, Res strings, host and screenshot tests.
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
            // The sport ViewModels and the bookings holder are KMP classes that :app obtains through Koin.
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
            // The sign page's route: koinViewModel() and lifecycle-aware collection; the iOS shell hosts
            // the same route.
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
    androidExport("values/strings_sport.xml")
}
