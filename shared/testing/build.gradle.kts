// Test helpers for the features' commonTest and the screenshot harness; core-free (recipe kmp-module-build-file).
// Every consumer test classpath gets the libraries below through `api`, so a test module needs only
// `project(":shared:testing")`.
plugins {
    id("itmowidgets.cmp.ui")
    id("itmowidgets.testing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlin.test)
            api(project.dependencies.platform(libs.kotlinx.coroutines.bom))
            api(libs.kotlinx.coroutines.test)
            api(libs.turbine)
            api(libs.okio.fakefilesystem)
            api(libs.ktor.client.mock)
            api(libs.compose.ui.test)
        }
        androidMain.dependencies {
            api(libs.junit)
            api(libs.robolectric)
            // The preview screenshot harness (PreviewScreenshotTest) and the :app XML reference captures. The kit
            // depends on this module only from its tests, so there is no task cycle.
            api(project(":shared:designsystem"))
            api(libs.androidx.test.core)
            api(libs.androidx.compose.ui.test.junit4)
            api(libs.roborazzi)
            api(libs.roborazzi.compose)
            api(libs.roborazzi.accessibility.check)
            api(libs.composable.preview.scanner.android)
        }
    }
}
