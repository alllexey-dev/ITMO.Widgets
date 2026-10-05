package dev.alllexey.itmowidgets.core.storage

import okio.Path

/** The app's private directories, where every store keeps its files. */
interface AppDirectories {

    /** Backed up; Android's `filesDir`. */
    val files: Path

    /** May be cleared by the system; Android's `cacheDir`. */
    val cache: Path

    /** Never backed up: tokens and debug state; Android's `noBackupFilesDir`. */
    val noBackup: Path
}

/** Where a preferences DataStore named [name] lives, as androidx's `Context.preferencesDataStoreFile` puts it. */
fun AppDirectories.preferencesDataStoreFile(name: String): Path = files / "datastore" / "$name.preferences_pb"
