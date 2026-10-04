// The Compose kit: theme, tokens and components with their previews and goldens (recipe kmp-module-build-file).
plugins {
    id("itmowidgets.cmp.ui")
    id("itmowidgets.testing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // AppIconResources (L05 KM-09b) and LocalPlatformActions (L18 IO-03b) live in core.
            implementation(project(":shared:core"))
        }
    }
}
