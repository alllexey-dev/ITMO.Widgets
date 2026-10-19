// The campus map: indoor plans, the router ported from itmo-map-web, room matching (docs/features/map.md).
// Features read core, designsystem and compose-strings, never another feature.
plugins {
    id("itmowidgets.cmp.ui")
    id("itmowidgets.testing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            // The map ViewModel is a KMP ViewModel that :app obtains through Koin and iOS through SKIE.
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
        }
        androidMain.dependencies {
            // The Compose kit and strings are Android-only modules (ADR 0035).
            implementation(project(":shared:designsystem"))
            implementation(project(":shared:compose-strings"))
            // The mirror's engine and the native map view of the Android screen.
            implementation(libs.ktor.client.okhttp)
            implementation(libs.maplibre.android)
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
