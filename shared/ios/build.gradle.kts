// The iOS umbrella: the static framework `Shared` that the Xcode project links. SKIE and further exports come
// with L18 IO-05; linking needs Xcode (T10), compiling the klibs does not. Xcode builds it through the app target's
// Run Script (`embedAndSignAppleFrameworkForXcode`, iosApp/project.yml); see docs/ios.md.
// The Compose plugins copy every module's `composeResources` into the app bundle during that task (L18 IO-20);
// without them CMP finds no string on iOS.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Without it K/N warns that it cannot infer a bundle ID (T10).
            binaryOption("bundleId", "dev.alllexey.itmowidgets.shared")
            // UiText and AppIcon reach Swift without a module prefix (L18 IO-20).
            export(project(":shared:core"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            implementation(project(":shared:designsystem"))
            implementation(project(":shared:backend-client"))
            implementation(project(":shared:feature-qr"))
            implementation(project(":shared:feature-home"))
            implementation(project(":shared:feature-schedule"))
            implementation(project(":shared:feature-sport"))
            implementation(project(":shared:feature-recordbook"))
            implementation(project(":shared:feature-social"))
            implementation(project(":shared:feature-settings"))
            implementation(project(":shared:feature-resources"))
            implementation(project(":shared:feature-reviews"))
            implementation(project(":shared:feature-account"))
            // The composition probe of IosStrings.
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}

// The module has no resources of its own; the plugin only packs those of its dependencies.
compose.resources {
    generateResClass = org.jetbrains.compose.resources.ResourcesExtension.ResourceClassGeneration.Never
}
