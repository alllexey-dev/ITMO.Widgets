package dev.alllexey.itmowidgets.upgrade

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.platform.app.InstrumentationRegistry
import api.myitmo.MyItmo
import com.google.gson.Gson
import dev.alllexey.itmowidgets.di.NetworkModule
import java.io.Closeable
import java.io.File
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * A fresh copy of the 2.2 data directory (`assets/upgrade-2.2`) in its own temp root, for head stores to read.
 *
 * The app's real `filesDir`, `noBackupFilesDir`, `cacheDir` and DataStore are never touched: the process holds them
 * open. File stores take a directory in their `internal` constructor; `Context`-bound stores get [context], whose
 * directories point into the temp root. Token files are encrypted at copy time with the frozen 2.2 envelope.
 */
class Upgrade22Fixture : Closeable {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.context.assets
    private val root = File(instrumentation.targetContext.cacheDir, "upgrade-from-22/${UUID.randomUUID()}")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val filesDir = File(root, FILES)
    val noBackupFilesDir = File(root, NO_BACKUP)
    val cacheDir = File(root, CACHE)

    /** Answers the temp directories, also through `applicationContext`. */
    val context: Context = object : ContextWrapper(instrumentation.targetContext) {
        override fun getFilesDir() = this@Upgrade22Fixture.filesDir
        override fun getNoBackupFilesDir() = this@Upgrade22Fixture.noBackupFilesDir
        override fun getCacheDir() = this@Upgrade22Fixture.cacheDir
        override fun getApplicationContext(): Context = this
    }

    /** Ten minutes after the capture: nothing captured has expired yet. */
    val clock: Clock = Clock.fixed(Captured22.AT.plusSeconds(600), ZoneOffset.UTC)

    /** The application's Gson, built by the same provider the graph uses. */
    val gson: Gson = NetworkModule.provideGson(NetworkModule.provideWidgetsClient(MyItmo(), "https://localhost/"))

    /** `app_preferences` as `StorageModule` opens it; one instance per fixture, closed with it. */
    val preferences: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(filesDir, "datastore/app_preferences.preferences_pb") }
        )
    }

    init {
        root.deleteRecursively()
        listOf(FILES, NO_BACKUP, CACHE).forEach { copyTree("$ASSET_ROOT/$it", File(root, it)) }
        assets.list("$ASSET_ROOT/$PLAINTEXT").orEmpty().forEach { name ->
            val plaintext = assets.open("$ASSET_ROOT/$PLAINTEXT/$name").use { it.readBytes().toString(Charsets.UTF_8) }
            File(noBackupFilesDir, name).writeText(Legacy22TokenEnvelope.encrypt(plaintext))
        }
    }

    /** Relative paths of every captured asset, with the token plaintexts under `plaintext/`. */
    fun assetPaths(): List<String> = listOf(FILES, NO_BACKUP, CACHE, PLAINTEXT).flatMap { assetFiles(it) }.sorted()

    fun assetBytes(path: String): ByteArray = assets.open("$ASSET_ROOT/$path").use { it.readBytes() }

    override fun close() {
        scope.cancel()
        root.deleteRecursively()
    }

    private fun assetFiles(path: String): List<String> {
        val children = assets.list("$ASSET_ROOT/$path").orEmpty()
        return if (children.isEmpty()) listOf(path) else children.flatMap { assetFiles("$path/$it") }
    }

    private fun copyTree(assetPath: String, target: File) {
        val children = assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            assets.open(assetPath).use { input -> target.outputStream().use(input::copyTo) }
        } else {
            children.forEach { copyTree("$assetPath/$it", File(target, it)) }
        }
    }

    private companion object {
        const val ASSET_ROOT = "upgrade-2.2"
        const val FILES = "files"
        const val NO_BACKUP = "no_backup"
        const val CACHE = "cache"
        const val PLAINTEXT = "plaintext"
    }
}
