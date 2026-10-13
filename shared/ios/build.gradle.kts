// The iOS umbrella: the static framework `Shared` that the Xcode project links, with SKIE's Swift layer;
// linking needs Xcode, compiling the klibs does not. Xcode builds it through the app target's
// Run Script (`embedAndSignAppleFrameworkForXcode`, iosApp/project.yml); see docs/ios.md.
// The Compose plugins copy every module's `composeResources` into the app bundle during that task;
// without them CMP finds no string on iOS.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    // Sealed classes as Swift enums (`onEnum(of:)`), suspend as `async`, Flow as `AsyncSequence`.
    alias(libs.plugins.skie)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Without it K/N warns that it cannot infer a bundle ID.
            binaryOption("bundleId", "dev.alllexey.itmowidgets.shared")
            // Exports are what Swift names without a module prefix: core for UiText, AppIcon and the ports Swift
            // implements; a feature whose ViewModel SwiftUI owns is exported too. Every export grows the header and
            // the link.
            export(project(":shared:core"))
            // The sign-in and first-run ViewModels SwiftUI owns.
            export(project(":shared:feature-account"))
            // SettingsViewModel, DiagnosticsViewModel and the settings page model in Swift.
            export(project(":shared:feature-settings"))
            // The BARS sign-in sheet's BarsLoginViewModel and the WebKit ports AppPlatform implements.
            export(project(":shared:feature-recordbook"))
            // The pass sheet's QrPassViewModel and QrCodeGenerator.
            export(project(":shared:feature-qr"))
            // The today header's TodayHeaderViewModel.
            export(project(":shared:feature-home"))
            // The sport tab's, `Мои секции` and `Откуда баллы` ViewModels, SportSamples of the snapshot tests.
            export(project(":shared:feature-sport"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            implementation(project(":shared:designsystem"))
            implementation(project(":shared:compose-strings"))
            implementation(project(":shared:backend-client"))
            api(project(":shared:feature-qr"))
            api(project(":shared:feature-home"))
            implementation(project(":shared:feature-schedule"))
            api(project(":shared:feature-sport"))
            api(project(":shared:feature-recordbook"))
            implementation(project(":shared:feature-social"))
            api(project(":shared:feature-settings"))
            implementation(project(":shared:feature-resources"))
            implementation(project(":shared:feature-reviews"))
            api(project(":shared:feature-account"))
            // The composition probe of IosStrings and the screen hosts (screens/).
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            // The Swift bridge: the ViewModelStore each SwiftUI screen owns (bridge/ScreenViewModelStore.kt).
            implementation(libs.jetbrains.lifecycle.viewmodel)
        }
        // The background runner's tests: kotlin-test, coroutines-test and FakeClock.
        iosTest.dependencies {
            implementation(project(":shared:testing"))
        }
    }
}

skie {
    // The build never reaches Touchlab's analytics endpoint (the upload only timed out).
    analytics {
        disableUpload.set(true)
    }
}

// The module has no resources of its own; the plugin only packs those of its dependencies.
compose.resources {
    generateResClass = org.jetbrains.compose.resources.ResourcesExtension.ResourceClassGeneration.Never
}
