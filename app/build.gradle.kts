import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.hilt.android)
    kotlin("kapt")
}

android {
    namespace = "dev.alllexey.itmowidgets"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.alllexey.itmowidgets"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "2.1-SNAPSHOT"
        resValue("string", "app_version", versionName!!)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // ActivityScenario.launchActivityForResult waits the full lifecycle timeout (45 s) on
        // close; observed transitions on the emulator stay under 2 s.
        testInstrumentationRunnerArguments["activityLifecycleChangeTimeoutMillis"] = "5000"
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "WIDGETS_BASE_URL",
                "\"https://dev.widgets.alllexey.dev\""
            )
        }
        release {
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "WIDGETS_BASE_URL",
                "\"https://widgets.alllexey.dev\""
            )
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    testOptions {
        // Lets JVM tests exercise classes that log through android.util.Log.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation("com.github.bumptech.glide:glide:5.0.5")
    implementation(libs.android.image.cropper)
    implementation(libs.itmo.widgets.core)
    implementation(libs.my.itmo.api)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.qrcodegen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.konsist)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
