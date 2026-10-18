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
            implementation(project(":shared:designsystem"))
            implementation(project(":shared:compose-strings"))
        }
        commonTest.dependencies {
            implementation(project(":shared:testing"))
        }
    }
}
