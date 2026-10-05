// A feature module: Compose UI, Res strings, host and screenshot tests (recipe kmp-module-build-file).
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
        }
        commonTest.dependencies {
            implementation(project(":shared:testing"))
        }
    }
}
