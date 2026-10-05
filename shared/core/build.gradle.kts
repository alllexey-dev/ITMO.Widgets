// Domain models, ports, AppResult/LoadState and UiText: no Compose until L07 KM-07 moves it to
// itmowidgets.cmp.ui with a public Res (recipe kmp-module-build-file).
plugins {
    id("itmowidgets.kmp.library")
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
            // kotlinx.atomicfu.locks.SynchronizedObject of AtomicTextFile (L07 KM-04); library only, no plugin.
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
