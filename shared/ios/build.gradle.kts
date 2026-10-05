// The iOS umbrella: the static framework `Shared` that the Xcode project links. Exports and SKIE come with
// L18 IO-05; linking needs Xcode (T10), compiling the klibs does not. Xcode builds it through the app target's
// Run Script (`embedAndSignAppleFrameworkForXcode`, iosApp/project.yml); see docs/ios.md.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Without it K/N warns that it cannot infer a bundle ID (T10).
            binaryOption("bundleId", "dev.alllexey.itmowidgets.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
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
        }
    }
}
