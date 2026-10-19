// A feature module: shared domain, data and ViewModels; Android-only Compose UI in androidMain (ADR 0035).
// Features read core, designsystem and compose-strings, never another feature.
plugins {
    id("itmowidgets.cmp.android")
    id("itmowidgets.testing")
}

kotlin {
    // The sign-in logo is an Android drawable (R.drawable.auth_logo); the iOS app bundles the same file by path.
    android {
        androidResources {
            enable = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            // The auth, onboarding and Me ViewModels are KMP ViewModels that :app obtains through Koin and SwiftUI
            // through the bridge.
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
        }
        // Pairing: Tink seals to the device's key (CryptoKit on iOS); BiometricPrompt asks for the screen lock.
        androidMain.dependencies {
            implementation(libs.tink.android)
            implementation(libs.androidx.biometric)
            // The account routes: koinViewModel() and lifecycle-aware collection.
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
