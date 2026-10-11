import java.util.Properties

// SDK levels, JVM 17, the Compose compiler and test defaults come from itmowidgets.android.app (build-logic).
plugins {
    id("itmowidgets.android.app")
    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    // Robolectric (offline) for host tests that boot the real Application.
    id("itmowidgets.testing")
}

// Release signing reads the ignored keystore.properties; a debug build needs none.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

// Android Test Orchestrator with clearPackageData (TC-14) for the platform list only: `scripts/verify.sh ui @platform`
// and the nightly managed-device job pass -Pitmo.orchestrator=true. The View suites share TestSession and the real
// Hilt graph across tests, so they keep the plain runner until their ports delete them.
val orchestrated = providers.gradleProperty("itmo.orchestrator").map(String::toBoolean).getOrElse(false)

android {
    namespace = "dev.alllexey.itmowidgets"

    defaultConfig {
        applicationId = "dev.alllexey.itmowidgets"
        versionCode = 20292
        versionName = "2.3.0-beta.2"
        resValue("string", "app_version", versionName!!)

        // The real ITMO and Backend hosts fail at once in instrumented tests, never hang them (SH-FIX-DL).
        testInstrumentationRunner = "dev.alllexey.itmowidgets.testing.OfflineHostsTestRunner"
        // ActivityScenario.launchActivityForResult waits the full lifecycle timeout (45 s) on
        // close; observed transitions on the emulator stay under 2 s.
        testInstrumentationRunnerArguments["activityLifecycleChangeTimeoutMillis"] = "5000"
        if (orchestrated) testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    testOptions {
        if (orchestrated) execution = "ANDROIDX_TEST_ORCHESTRATOR"
        // CI only (android-nightly.yml through `verify.sh ui @platform --managed-device`): a local run would create
        // an AVD. The full AOSP image, not ATD: QrTileFlowTest needs SystemUI's quick settings tile host, which the
        // ATD image lacks (the tile is never added there). No listed platform test needs Play services.
        managedDevices {
            localDevices {
                create("ciDevice") {
                    device = "Pixel 2"
                    apiLevel = 36
                    systemImageSource = "aosp"
                }
            }
        }
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
            isMinifyEnabled = true
            isShrinkResources = true
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
        // The release R8 and resource shrinking, debug-signed and pointed at dev, so a smoke run of the shrunk
        // app on an emulator reaches no production data.
        create("minifiedSmoke") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField(
                "String",
                "WIDGETS_BASE_URL",
                "\"https://dev.widgets.alllexey.dev\""
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
    // @Preview of the Compose screens that stay in :app (debug tools); DebugToolsScreenshotTest renders them.
    implementation(libs.compose.ui.tooling.preview)
    // The Navigation 3 shell (ADR 0020, L17 SH-1b1): NavDisplay and scenes, and a ViewModel store per entry.
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    testImplementation(project(":shared:testing"))
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.android.image.cropper)
    implementation(libs.my.itmo.api.kmp)
    // The BARS engine of di/RecordbookModule (KM-10b2): OkHttp with the BARS timeouts, apart from MyITMO's engine.
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.datastore.preferences)
    // Storage foundation (KM-04): okio files, DataStore by okio path, the common lock of AtomicTextFile.
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.okio)
    implementation(libs.kotlinx.atomicfu)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.remoteviews)
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
    implementation(libs.ksoup)
    implementation(libs.kotlinx.datetime)
    // kotlinx JSON of the core stores, auth and the FCM envelope (KM-05d).
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    // Koin beside Hilt (ADR 0019): the graph starts in di/bridge/KoinStarter.
    implementation(libs.koin.android)
    "playImplementation"(libs.play.app.update.ktx)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    // The store tests prove 2.2 (Gson) reads what kotlinx writes; Gson is on no runtime classpath (KM-10i).
    testImplementation(libs.gson)
    testImplementation(libs.koin.test)
    // TestListenableWorkerBuilder: the worker tests build each worker as WorkManager does, on a Koin test graph (KM-12a).
    testImplementation(libs.androidx.work.testing)
    // KoinContext for Robolectric Compose tests: Koin Compose caches the first graph per JVM, and StopKoinRule restarts it.
    testImplementation(libs.koin.compose.viewmodel)
    // Screenshot tests that host @AndroidEntryPoint screens (AppScreenshotRule, XmlReferenceCapture).
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.monitor)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    if (orchestrated) androidTestUtil(libs.androidx.test.orchestrator)
}

// AtomicTextFileTest runs the real android.util.AtomicFile on SDK 29 (`.bak`) as well as the default SDK 35 (`.new`).
itmoTesting.extraRobolectricSdks(libs.robolectric.android.all.sdk29)
