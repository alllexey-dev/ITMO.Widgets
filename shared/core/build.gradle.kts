// Domain models, ports, AppResult/LoadState, UiText with its resolvers and the cross-feature strings; a public Res
// that features and :app read (recipe kmp-module-build-file).
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
            // JsonElement is the payload type of FcmPayloadHandler (L07 KM-05d).
            api(libs.kotlinx.serialization.json)
            // StringResource and PluralStringResource are in UiText's public signature (L07 KM-07).
            api(libs.compose.components.resources)
            // MyItmoClientFactory builds the MyItmoApi 2.x client from an engine, a TokenStorage and a Clock, so the
            // client, its storage and Ktor's engine type are in core's public signatures (L07 KM-10a1).
            api(libs.my.itmo.api.kmp)
            api(libs.ktor.client.core)
            // kotlinx.atomicfu.locks.SynchronizedObject of AtomicTextFile (L07 KM-04); library only, no plugin.
            implementation(libs.kotlinx.atomicfu)
        }
        // The Darwin engine of every iOS Ktor client (L18 IO-04a; the client factories take the engine, ADR 0026).
        iosMain.dependencies {
            api(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// :app reads both files as Android resources: the platform strings for good (manifest, shortcuts, widget
// descriptors and layouts, RemoteViews, notifications), the cross-feature strings until their last View or R.string
// user goes; the generated key -> R table covers both (L05 KM-09b).
itmowidgetsStrings {
    androidExport("values/strings_common.xml")
    androidExport("values/strings_platform.xml")
}
