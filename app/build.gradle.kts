import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// Release signing reads the ignored keystore.properties; a debug build needs none.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "dev.alllexey.itmowidgets"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.alllexey.itmowidgets"
        minSdk = 26
        targetSdk = 36
        versionCode = 20290
        versionName = "2.3-SNAPSHOT"
        resValue("string", "app_version", versionName!!)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // ActivityScenario.launchActivityForResult waits the full lifecycle timeout (45 s) on
        // close; observed transitions on the emulator stay under 2 s.
        testInstrumentationRunnerArguments["activityLifecycleChangeTimeoutMillis"] = "5000"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile", "app-keystore.jks"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias", "key0")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
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
            signingConfig = signingConfigs.findByName("release")
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
    // One applicationId and one key: an install of either variant updates the other.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField(
                "String",
                "DOWNLOAD_URL",
                "\"https://github.com/alllexey-dev/ITMO.Widgets/releases/latest\""
            )
        }
        create("play") {
            dimension = "distribution"
            buildConfigField(
                "String",
                "DOWNLOAD_URL",
                "\"https://play.google.com/store/apps/details?id=dev.alllexey.itmowidgets\""
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

// Konsist reads sources that are not compile inputs: an import-only edit leaves the bytecode
// unchanged, so without this the up-to-date check or the build cache would replay a stale result.
tasks.withType<Test>().configureEach {
    inputs.dir("src")
        .withPropertyName("konsistSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.glide)
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
    implementation(libs.play.services.code.scanner)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.hilt.android)
    "playImplementation"(libs.play.app.update.ktx)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.konsist)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.monitor)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
}
