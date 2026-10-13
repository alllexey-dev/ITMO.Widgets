// The baseline and startup profile generator and the frame-timing macrobenchmarks, both driving the demo session of
// :app on a device (docs/testing.md "Baseline profiles"). Never part of verifyQuick or a PR check.
plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "dev.alllexey.itmowidgets.baselineprofile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // BaselineProfileRule needs API 28 (rooted) or 33+; Macrobenchmark needs 23.
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    // The app's flavours; only github runs (see :app), its profile is merged into main and serves both.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") { dimension = "distribution" }
        create("play") { dimension = "distribution" }
    }
}

androidComponents {
    beforeVariants { variant ->
        if ("distribution" to "play" in variant.productFlavors) variant.enable = false
    }
}

baselineProfile {
    // The emulator or device that `ANDROID_SERIAL` and adb see; no Gradle managed device.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
