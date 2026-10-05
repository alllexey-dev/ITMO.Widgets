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

// The cross-feature strings stay Android resources of :app for its Views and the generated key -> R table; KM-09b
// joins the rest of app/src/main/res/values/strings_common.xml to this file.
itmowidgetsStrings {
    androidExport("values/strings_common.xml")
}
