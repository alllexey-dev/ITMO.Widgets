// Domain models, ports, AppResult/LoadState, UiText and the cross-feature strings, without Compose: the catalog files
// compile as CMP resources in :shared:compose-strings, code here names them by the generated CoreStrings keys.
plugins {
    id("itmowidgets.kmp.library")
    id("itmowidgets.strings")
    id("itmowidgets.testing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Flow, Instant/LocalDate, okio Path and DataStore<Preferences> are in core's public signatures.
            api(project.dependencies.platform(libs.kotlinx.coroutines.bom))
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.okio)
            api(libs.androidx.datastore.preferences.core)
            // JsonElement is the payload type of FcmPayloadHandler.
            api(libs.kotlinx.serialization.json)
            // The MyItmoApi 2.x client, its storage and Ktor's engine type are in core's public signatures.
            api(libs.my.itmo.api.kmp)
            api(libs.ktor.client.core)
            // asBackendTokenSource() and BackendException.asAppError() have Core 2.0's types and errors in their
            // signatures; the client never depends on core, the mapping lives here.
            api(project(":shared:backend-client"))
            // kotlinx.atomicfu.locks.SynchronizedObject of AtomicTextFile; library only, no plugin.
            implementation(libs.kotlinx.atomicfu)
            // The widget palettes without Compose (ThemeSeeds.kt); the same version material-kolor builds on.
            implementation(libs.material.color.utilities)
        }
        // The Darwin engine of every iOS Ktor client; the clients take the engine.
        iosMain.dependencies {
            api(libs.ktor.client.darwin)
            // iosCoreModule, the core bindings of the iOS Koin graph; Android's come from :app's Koin modules.
            api(libs.koin.core)
        }
        // A mock engine for the ISU client's redirects and the refresh and demo checks of the iOS graph.
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

// :app reads both files as Android resources: the platform strings for good (manifest, shortcuts, widget
// descriptors and layouts, RemoteViews, notifications), the cross-feature strings for their remaining R.string
// users; the generated key -> R table covers both.
itmowidgetsStrings {
    androidExport("values/strings_common.xml")
    androidExport("values/strings_platform.xml")
}
