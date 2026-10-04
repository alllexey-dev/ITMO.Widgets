// Loads build-logic once for every module: its dependencies are the only copy of AGP, Kotlin, CMP, KSP, Hilt,
// google-services and Roborazzi on the build classpath, so no plugin here or in a module carries a version.
plugins {
    id("itmowidgets.android.app") apply false
}
