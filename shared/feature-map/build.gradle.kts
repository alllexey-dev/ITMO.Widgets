// The campus map: indoor plans, the router ported from itmo-map-web, room matching (docs/features/map.md).
// Features read core, designsystem and compose-strings, never another feature. Compose UI and the map icons
// (`assets/map_sprites`, which the iOS bundle references too) are Android-only (ADR 0035).
plugins {
    id("itmowidgets.cmp.android")
    id("itmowidgets.testing")
}

kotlin {
    android {
        // The map icons in src/androidMain/assets.
        androidResources {
            enable = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            // The map ViewModel is a KMP ViewModel that :app obtains through Koin and iOS through SKIE.
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
        }
        androidMain.dependencies {
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
