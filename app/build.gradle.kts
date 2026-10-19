import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

// SDK levels, JVM 17, the Compose compiler and test defaults come from itmowidgets.android.app (build-logic).
plugins {
    id("itmowidgets.android.app")
    id("com.google.gms.google-services")
    // Robolectric (offline) for host tests that boot the real Application.
    id("itmowidgets.testing")
    // Consumes the profiles :baselineprofile generates (docs/testing.md "Baseline profiles").
    id("androidx.baselineprofile")
}

// Release signing reads the ignored keystore.properties; a debug build needs none.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

// Build identity (docs/building.md): `official` only when the release key is the official one (the SHA-256 in
// docs/product/releases.md and the site's assetlinks.json), so a fork with its own key or none builds `custom`.
val officialCertSha256 = "939e9bd52c89fbb7a9b3c6b8d0f0883ee2c9c12c6d8fed922251e2bdfbc1f071"
val buildChannel = if (keystoreProperties.isNotEmpty() && releaseCertSha256() == officialCertSha256) "official" else "custom"
val buildSource = providers.environmentVariable("GITHUB_REPOSITORY").orElse("local").get()
val buildCommit = providers.exec {
    commandLine("git", "rev-parse", "--short=12", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map(String::trim)

fun releaseCertSha256(): String? = runCatching {
    val store = KeyStore.getInstance(
        rootProject.file(keystoreProperties.getProperty("storeFile", "app-keystore.jks")),
        keystoreProperties.getProperty("storePassword").toCharArray(),
    )
    val cert = store.getCertificate(keystoreProperties.getProperty("keyAlias", "key0"))
    MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
}.getOrNull()

// `-Pitmo.selfSigned=true` signs the release outputs with the local debug key when keystore.properties is absent, so
// a custom build installs (docs/building.md). Without it they stay unsigned, as ship-check.sh expects.
val selfSigned = providers.gradleProperty("itmo.selfSigned").map(String::toBoolean).getOrElse(false)

// Android Test Orchestrator with clearPackageData for the platform list only: `scripts/verify.sh ui @platform`
// and the nightly managed-device job pass -Pitmo.orchestrator=true. The other suites share TestSession and the real
// Koin graph across tests, so they keep the plain runner.
val orchestrated = providers.gradleProperty("itmo.orchestrator").map(String::toBoolean).getOrElse(false)

android {
    namespace = "dev.alllexey.itmowidgets"

    defaultConfig {
        applicationId = "dev.alllexey.itmowidgets"
        versionCode = 20292
        versionName = "2.3-SNAPSHOT"
        resValue("string", "app_version", versionName!!)
        buildConfigField("String", "BUILD_CHANNEL", "\"$buildChannel\"")
        buildConfigField("String", "BUILD_SOURCE", "\"$buildSource\"")

        // The real ITMO and Backend hosts fail at once in instrumented tests, never hang them.
        testInstrumentationRunner = "dev.alllexey.itmowidgets.testing.OfflineHostsTestRunner"
        // ActivityScenario.launchActivityForResult waits the full lifecycle timeout (45 s) on
        // close; observed transitions on the emulator stay under 2 s.
        testInstrumentationRunnerArguments["activityLifecycleChangeTimeoutMillis"] = "5000"
        if (orchestrated) testInstrumentationRunnerArguments["clearPackageData"] = "true"
        // The campus-map mirror (srvscripts stacks/itmomap); `-PcampusMapUrl=<url>` for a local one.
        val campusMapUrl = providers.gradleProperty("campusMapUrl").orElse("https://map.alllexey.dev/app/v1/").get()
        buildConfigField("String", "CAMPUS_MAP_URL", "\"$campusMapUrl\"")
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
            // The release key when keystore.properties is present, so a debug build installs over the GitHub release.
            signingConfigs.findByName("release")?.let { signingConfig = it }
            buildConfigField(
                "String",
                "WIDGETS_BASE_URL",
                "\"https://dev.widgets.alllexey.dev\""
            )
        }
        release {
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug").takeIf { selfSigned }
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
        // The release R8 and resource shrinking, pointed at dev, so a smoke run of the shrunk app reaches no
        // production data. Release key when keystore.properties is present, so it installs over a local debug build.
        create("minifiedSmoke") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    implementation(project(":shared:compose-strings"))
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
    implementation(project(":shared:feature-map"))
    implementation(project(":shared:feature-account"))
    // @Preview of the Compose screens that stay in :app (debug tools); DebugToolsScreenshotTest renders them.
    implementation(libs.compose.ui.tooling.preview)
    // The Navigation 3 shell: NavDisplay and scenes, and a ViewModel store per entry.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    testImplementation(project(":shared:testing"))
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.android.image.cropper)
    implementation(libs.my.itmo.api.kmp)
    // The BARS engine of di/RecordbookModule: OkHttp with the BARS timeouts, apart from MyITMO's engine.
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.datastore.preferences)
    // Storage foundation: okio files, DataStore by okio path, the common lock of AtomicTextFile.
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
    // ProcessAppForeground: the app's return to the foreground.
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.ksoup)
    implementation(libs.kotlinx.datetime)
    // kotlinx JSON of the core stores, auth and the FCM envelope.
    implementation(libs.kotlinx.serialization.json)
    // Koin, the app's one graph, starts in di/KoinStarter.
    implementation(libs.koin.android)
    "playImplementation"(libs.play.app.update.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    // The store tests prove 2.2 (Gson) reads what kotlinx writes; Gson is on no runtime classpath.
    testImplementation(libs.gson)
    testImplementation(libs.koin.test)
    // TestListenableWorkerBuilder: the worker tests build each worker as WorkManager does, on a Koin test graph.
    testImplementation(libs.androidx.work.testing)
    // KoinContext for Robolectric Compose tests: Koin Compose caches the first graph per JVM, and StopKoinRule restarts it.
    testImplementation(libs.koin.compose.viewmodel)
    // Installs the baseline profile on sideloaded APKs too; background dexopt then compiles the app against it.
    implementation(libs.androidx.profileinstaller)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.monitor)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    if (orchestrated) androidTestUtil(libs.androidx.test.orchestrator)
}

// The profiles are committed under src/main/generated/baselineProfiles, so release and minifiedSmoke ship them and no
// build needs a device. Regenerate them with `:app:generateBaselineProfile` (docs/testing.md "Baseline profiles").
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
    // The startup profile also orders the dex files so startup classes load from the first one.
    dexLayoutOptimization = true
    // Only github runs the journeys; the profile merged into main serves play too.
    variants {
        create("github") { from(project(":baselineprofile")) }
    }
}

// The plugin's benchmark and nonMinified copies of release run the journeys on devices: like minifiedSmoke they
// talk to dev, never to production.
androidComponents {
    onVariants(selector().withBuildType("benchmarkRelease")) { it.useDevBackend() }
    onVariants(selector().withBuildType("nonMinifiedRelease")) { it.useDevBackend() }
    // The commit only in release-type builds: in debug it would recompile the app and rerun its tests on every commit.
    onVariants { variant ->
        val commit = if (variant.buildType == "debug") provider { "" } else buildCommit
        variant.buildConfigFields?.put(
            "BUILD_COMMIT",
            commit.map { com.android.build.api.variant.BuildConfigField("String", "\"$it\"", null) },
        )
    }
}

fun com.android.build.api.variant.Variant.useDevBackend() {
    buildConfigFields?.put(
        "WIDGETS_BASE_URL",
        com.android.build.api.variant.BuildConfigField("String", "\"https://dev.widgets.alllexey.dev\"", null),
    )
}

// AtomicTextFileTest runs the real android.util.AtomicFile on SDK 29 (`.bak`) as well as the default SDK 35 (`.new`).
itmoTesting.extraRobolectricSdks(libs.robolectric.android.all.sdk29)
