import java.util.Properties

// SDK levels, JVM 17, the Compose compiler and test defaults come from itmowidgets.android.app (build-logic).
plugins {
    id("itmowidgets.android.app")
    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Release signing reads the ignored keystore.properties; a debug build needs none.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "dev.alllexey.itmowidgets"

    defaultConfig {
        applicationId = "dev.alllexey.itmowidgets"
        versionCode = 20290
        versionName = "2.3-SNAPSHOT"
        resValue("string", "app_version", versionName!!)

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
    androidResources { localeFilters += "ru" }
    bundle { language { enableSplit = false } }
    buildFeatures {
        buildConfig = true
        resValues = true
        viewBinding = true
    }
}

dependencies {
    // String paths: type-safe project accessors reject the dotted root name ITMO.Widgets.
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
    testImplementation(project(":shared:testing"))
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
    // Konsist 0.17.3 brings a 2.0.21 parser that misreads Kotlin 2.4 syntax.
    testRuntimeOnly(libs.konsist.kotlin.compiler.embeddable)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.monitor)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
}
