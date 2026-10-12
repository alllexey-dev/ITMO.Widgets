// Domain models, ports, AppResult/LoadState, UiText with its resolvers and the cross-feature strings; a public Res
// that features and :app read.
plugins {
    id("itmowidgets.cmp.ui")
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
            // AppRoute extends NavKey; only the runtime publishes iOS klibs, navigation3-ui stays in :app.
            api(libs.androidx.navigation3.runtime)
            // StringResource and PluralStringResource are in UiText's public signature.
            api(libs.compose.components.resources)
            // MyItmoClientFactory builds the MyItmoApi 2.x client from an engine, a TokenStorage and a Clock, so the
            // client, its storage and Ktor's engine type are in core's public signatures.
            api(libs.my.itmo.api.kmp)
            api(libs.ktor.client.core)
            // BackendClientFactory and BackendException.asAppError() have Core 2.0's client and errors in their
            // signatures; the client never depends on core, the mapping lives here.
            api(project(":shared:backend-client"))
            // kotlinx.atomicfu.locks.SynchronizedObject of AtomicTextFile; library only, no plugin.
            implementation(libs.kotlinx.atomicfu)
        }
        // The Darwin engine of every iOS Ktor client; the client factories take the engine.
        iosMain.dependencies {
            api(libs.ktor.client.darwin)
            // iosCoreModule, the core bindings of the iOS Koin graph; Android's come from :app's Koin modules.
            api(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        // A counting engine for the refresh and demo checks of the iOS graph.
        iosTest.dependencies {
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
