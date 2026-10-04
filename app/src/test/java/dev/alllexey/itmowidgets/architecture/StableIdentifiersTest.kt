package dev.alllexey.itmowidgets.architecture

import androidx.work.ListenableWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Identifiers that outlive an app update: the launcher, SystemUI, WorkManager, placed widgets, pending intents and
 * files on disk hold them. Every expected value is a literal, never an imported constant, so a `git mv` that keeps
 * the package needs no edit here and a package or value change fails with the identifier in the message.
 *
 * This test is the source of truth for `docs/architecture.md` "Stable identifiers". The list only grows; changing an
 * entry needs an ADR (0016, 0030) and a dual read of the old value.
 */
class StableIdentifiersTest {

    private val module = listOf(File("."), File("app"))
        .map { it.absoluteFile.normalize() }
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
    private val repository = module.parentFile
    private val resources = File(module, "src/main/res")
    private val manifest by lazy { document(File(module, "src/main/AndroidManifest.xml")) }
    private val sources by lazy { kotlinFiles(productionRoots()) }

    @Test
    fun `manifest components keep their class names`() {
        val declared = children(child(manifest, "application"))
            .filter { it.tagName in COMPONENT_TAGS }
            .associate { resolve(it.getAttribute("android:name")) to it.tagName }

        MANIFEST_COMPONENTS.forEach { (className, tag) ->
            assertEquals("Stable identifier $className is not declared as <$tag> in the manifest", tag,
                declared[className])
            load(className, "manifest component")
        }
    }

    @Test
    fun `main activity keeps its launcher entry, app links and shortcuts`() {
        val activity = component(MAIN_ACTIVITY)
        val filters = children(activity).filter { it.tagName == "intent-filter" }

        assertTrue(
            "Stable identifier $MAIN_ACTIVITY lost its MAIN/LAUNCHER intent filter",
            filters.any {
                "android.intent.action.MAIN" in actions(it) && "android.intent.category.LAUNCHER" in categories(it)
            }
        )
        val appLinks = filters.filter { it.getAttribute("android:autoVerify") == "true" }
        val data = appLinks.flatMap { filter -> children(filter).filter { it.tagName == "data" } }
        assertContains("App Link scheme", setOf("https"), data.attributes("android:scheme"))
        assertContains("App Link host", APP_LINK_HOSTS, data.attributes("android:host"))
        assertContains("App Link path", APP_LINK_PATHS, data.attributes("android:pathPrefix"))
        assertTrue(
            "Stable identifier $MAIN_ACTIVITY lost its android.app.shortcuts meta-data",
            children(activity).any {
                it.tagName == "meta-data" && it.getAttribute("android:name") == "android.app.shortcuts" &&
                    it.getAttribute("android:resource") == "@xml/shortcuts"
            }
        )
    }

    @Test
    fun `launcher shortcuts keep their ids, actions and target`() {
        val shortcuts = children(document(File(resources, "xml/shortcuts.xml")))
            .filter { it.tagName == "shortcut" }
            .associateBy { it.getAttribute("android:shortcutId") }

        SHORTCUTS.forEach { (id, action) ->
            val shortcut = shortcuts[id] ?: throw AssertionError("Stable identifier: shortcut \"$id\" is gone")
            val intent = child(shortcut, "intent")
            assertEquals("Stable identifier: action of shortcut \"$id\"", action, intent.getAttribute("android:action"))
            assertEquals("Stable identifier: target of shortcut \"$id\"", MAIN_ACTIVITY,
                intent.getAttribute("android:targetClass"))
            assertEquals("Stable identifier: package of shortcut \"$id\"", APPLICATION_ID,
                intent.getAttribute("android:targetPackage"))
        }
    }

    @Test
    fun `file provider keeps its authority and the ics cache path`() {
        val provider = component(FILE_PROVIDER)
        assertEquals("Stable identifier: FileProvider authority", FILE_PROVIDER_AUTHORITY,
            provider.getAttribute("android:authorities"))
        val paths = child(provider, "meta-data").getAttribute("android:resource").removePrefix("@xml/")
        val cachePaths = children(document(File(resources, "xml/$paths.xml")))
            .filter { it.tagName == "cache-path" }
            .associate { it.getAttribute("name") to it.getAttribute("path") }
        assertEquals("Stable identifier: FileProvider cache-path \"ics\"", "ics/", cachePaths["ics"])
    }

    @Test
    fun `workers keep their class names`() {
        WORKERS.forEach { className ->
            val worker = load(className, "worker")
            assertTrue(
                "Stable identifier $className is no longer a ListenableWorker",
                ListenableWorker::class.java.isAssignableFrom(worker)
            )
        }
    }

    @Test
    fun `unique work names stay`() {
        WORK_NAMES.forEach { assertLiteral("unique work name", it, context = "enqueueUnique") }
    }

    @Test
    fun `intent actions held by pending intents stay`() {
        INTENT_ACTIONS.forEach { assertLiteral("intent action", it) }
    }

    @Test
    fun `notification channels stay and the legacy channel is still deleted`() {
        NOTIFICATION_CHANNELS.forEach { assertLiteral("notification channel", it, context = "NotificationChannel(") }
        assertTrue(
            "Stable identifier: the deletion of \"$LEGACY_CHANNEL\" at start is gone",
            sources.any { "deleteNotificationChannel(\"$LEGACY_CHANNEL\")" in it.second }
        )
    }

    @Test
    fun `preferences, tokens and the keystore alias stay`() {
        assertLiteral("DataStore file", "app_preferences", context = "preferencesDataStoreFile")
        assertLiteral("Keystore alias", "itmo_widgets_myitmo_tokens_v1", context = "KeyStore")
        assertLiteral("token cipher format prefix", "v1:", context = "KeyStore")
        NO_BACKUP_FILES.forEach { assertLiteral("noBackupFilesDir file", it, context = "noBackupFilesDir") }
    }

    @Test
    fun `files and cache directories keep their names and inner files`() {
        FILES_DIR_LAYOUT.forEach { (directory, files) ->
            val names = listOf(directory) + files
            assertTrue(
                "Stable identifier filesDir/$directory/{${files.joinToString()}} is gone from one source file",
                sources.any { (_, text) -> "filesDir" in text && names.all { literal(it).containsMatchIn(text) } }
            )
        }
        CACHE_DIRS.forEach { assertLiteral("cacheDir directory", it, context = "cacheDir") }
    }

    @Test
    fun `backup exclusions stay`() {
        val fullBackup = document(File(resources, "xml/backup_rules.xml"))
        assertContains("backup_rules.xml exclusion", BACKUP_EXCLUSIONS, exclusions(fullBackup))
        val extraction = document(File(resources, "xml/data_extraction_rules.xml"))
        listOf("cloud-backup", "device-transfer").forEach { section ->
            assertContains("data_extraction_rules.xml $section exclusion", BACKUP_EXCLUSIONS,
                exclusions(child(extraction, section)))
        }
    }

    @Test
    fun `distribution keeps one application id and two flavors`() {
        val buildConfig = load("$APPLICATION_ID.BuildConfig", "BuildConfig")
        assertEquals("Stable identifier BuildConfig.APPLICATION_ID", APPLICATION_ID,
            buildConfig.getField("APPLICATION_ID").get(null))
        val flavor = buildConfig.getField("FLAVOR").get(null)
        assertTrue("Stable identifier BuildConfig.FLAVOR: \"$flavor\" is not one of $FLAVORS", flavor in FLAVORS)
        val downloadUrl = buildConfig.getField("DOWNLOAD_URL").get(null)
        assertTrue("Stable identifier BuildConfig.DOWNLOAD_URL is blank",
            downloadUrl is String && downloadUrl.isNotBlank())
    }

    @Test
    fun `rule names quoted by the docs stay`() {
        val rules = kotlinFiles(listOf(File(module, "src/test"), File(repository, "konsist")))
        QUOTED_RULES.forEach { name ->
            assertTrue("Stable identifier: rule `$name` is gone", rules.any { "fun `$name`(" in it.second })
        }
    }

    private fun productionRoots(): List<File> {
        val app = PRODUCTION_SOURCE_SETS.flatMap { set ->
            listOf("java", "kotlin").map { File(module, "src/$set/$it") }
        }
        val shared = File(repository, "shared").listFiles().orEmpty().flatMap { shared ->
            File(shared, "src").listFiles { set -> set.name.endsWith("Main") }.orEmpty().map { File(it, "kotlin") }
        }
        return app + shared
    }

    private fun kotlinFiles(roots: List<File>): List<Pair<File, String>> = roots
        .filter { it.isDirectory }
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        .map { it to it.readText() }

    private fun assertLiteral(kind: String, value: String, context: String? = null) {
        val pattern = literal(value)
        assertTrue(
            "Stable identifier $kind \"$value\" is gone from the production sources",
            sources.any { (_, text) -> (context == null || context in text) && pattern.containsMatchIn(text) }
        )
    }

    /** A string literal or one `/`-separated segment of it, so `"qr_custom_spoiler/custom_spoiler.png"` holds both. */
    private fun literal(value: String) = Regex("""(?<=["/])${Regex.escape(value)}(?=["/])""")

    private fun assertContains(kind: String, expected: Set<String>, actual: Collection<String>) {
        val missing = expected - actual.toSet()
        assertTrue("Stable identifier $kind missing: $missing", missing.isEmpty())
    }

    private fun load(className: String, kind: String): Class<*> = try {
        Class.forName(className, false, javaClass.classLoader)
    } catch (_: ClassNotFoundException) {
        throw AssertionError(
            "Stable identifier $className ($kind) no longer resolves; a package change breaks installed apps"
        )
    }

    private fun component(className: String): Element = children(child(manifest, "application"))
        .firstOrNull { it.tagName in COMPONENT_TAGS && resolve(it.getAttribute("android:name")) == className }
        ?: throw AssertionError("Stable identifier $className is not declared in the manifest")

    private fun resolve(name: String) = if (name.startsWith(".")) APPLICATION_ID + name else name

    private fun exclusions(section: Element) = children(section)
        .filter { it.tagName == "exclude" && it.getAttribute("domain") == "file" }
        .map { it.getAttribute("path") }

    private fun actions(filter: Element) = children(filter).filter { it.tagName == "action" }.attributes("android:name")

    private fun categories(filter: Element) =
        children(filter).filter { it.tagName == "category" }.attributes("android:name")

    private fun List<Element>.attributes(name: String) = map { it.getAttribute(name) }.filter { it.isNotEmpty() }

    private fun document(file: File): Element = DocumentBuilderFactory.newInstance()
        .apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        .newDocumentBuilder()
        .parse(file)
        .documentElement

    private fun children(parent: Element): List<Element> {
        val nodes = parent.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun child(parent: Element, tag: String): Element = children(parent).firstOrNull { it.tagName == tag }
        ?: throw AssertionError("<${parent.tagName}> has no <$tag>")

    private companion object {
        const val APPLICATION_ID = "dev.alllexey.itmowidgets"
        const val MAIN_ACTIVITY = "dev.alllexey.itmowidgets.app.MainActivity"
        const val FILE_PROVIDER = "androidx.core.content.FileProvider"
        const val FILE_PROVIDER_AUTHORITY = "\${applicationId}.files"
        const val LEGACY_CHANNEL = "fcm_default_channel"

        val COMPONENT_TAGS = setOf("activity", "service", "receiver", "provider")
        val PRODUCTION_SOURCE_SETS = listOf("main", "debug", "github", "play")
        val FLAVORS = setOf("github", "play")

        val MANIFEST_COMPONENTS = mapOf(
            MAIN_ACTIVITY to "activity",
            "dev.alllexey.itmowidgets.feature.qr.ui.QrTileService" to "service",
            "dev.alllexey.itmowidgets.feature.qr.ui.widget.QrCodeWidgetProvider" to "receiver",
            "dev.alllexey.itmowidgets.feature.schedule.ui.widget.SingleLessonWidgetProvider" to "receiver",
            "dev.alllexey.itmowidgets.feature.schedule.ui.widget.DayScheduleWidgetProvider" to "receiver",
            "dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetRefreshReceiver" to "receiver",
            "dev.alllexey.itmowidgets.app.WidgetBootReceiver" to "receiver",
            "dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetRemoteViewsService" to "service",
            "dev.alllexey.itmowidgets.core.notification.MyFirebaseMessagingService" to "service"
        )

        val APP_LINK_HOSTS = setOf("widgets.alllexey.dev", "dev.widgets.alllexey.dev")
        val APP_LINK_PATHS = setOf("/u/", "/sport/")

        val SHORTCUTS = mapOf(
            "qr_pass" to "dev.alllexey.itmowidgets.action.OPEN_QR_PASS",
            "today" to "dev.alllexey.itmowidgets.action.OPEN_TODAY"
        )

        val WORKERS = listOf(
            "dev.alllexey.itmowidgets.feature.recordbook.work.MarksWorker",
            "dev.alllexey.itmowidgets.feature.recordbook.work.BarsCookieProbeWorker",
            "dev.alllexey.itmowidgets.core.session.IdentitySyncWorker",
            "dev.alllexey.itmowidgets.feature.qr.work.QrWidgetUpdateWorker",
            "dev.alllexey.itmowidgets.core.notification.FcmTokenWorker",
            "dev.alllexey.itmowidgets.core.notification.FcmMessageWorker",
            "dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetUpdateWorker",
            "dev.alllexey.itmowidgets.feature.schedule.work.CalendarSyncWorker",
            "dev.alllexey.itmowidgets.feature.schedule.work.ScheduleChangesWorker"
        )

        val WORK_NAMES = listOf(
            "marks-check",
            "marks-check-now",
            "schedule-changes-check",
            "schedule-changes-now",
            "calendar-sync",
            "calendar-sync-now",
            "bars-cookie-probe",
            "fcm-token-sync",
            "fcm-messages",
            "backend-identity-sync",
            "dev.alllexey.itmowidgets.ScheduleWidgetPeriodicUpdate",
            "dev.alllexey.itmowidgets.ScheduleWidgetUpdate",
            "dev.alllexey.itmowidgets.ScheduleWidgetFollowUpUpdate",
            "dev.alllexey.itmowidgets.QrWidgetUpdate"
        )

        val INTENT_ACTIONS = listOf(
            "dev.alllexey.itmowidgets.action.OPEN_SPORT",
            "dev.alllexey.itmowidgets.action.OPEN_USER_PROFILE",
            "dev.alllexey.itmowidgets.action.OPEN_SCHEDULE",
            "dev.alllexey.itmowidgets.action.OPEN_SCHEDULE_CHANGES",
            "dev.alllexey.itmowidgets.action.OPEN_RECORDBOOK",
            "dev.alllexey.itmowidgets.action.OPEN_RECORDBOOK_SUBJECT",
            "dev.alllexey.itmowidgets.action.OPEN_BARS_LOGIN",
            "dev.alllexey.itmowidgets.action.OPEN_QR_PASS",
            "dev.alllexey.itmowidgets.action.OPEN_TODAY",
            "dev.alllexey.itmowidgets.action.QR_WIDGET_CLICK",
            "dev.alllexey.itmowidgets.action.QR_WIDGET_AUTO_HIDE",
            "dev.alllexey.itmowidgets.action.SCHEDULE_WIDGET_REFRESH",
            "dev.alllexey.itmowidgets.action.WIDGET_PINNED"
        )

        val NOTIFICATION_CHANNELS = listOf("sport", "friends", "schedule_changes", "marks")

        val NO_BACKUP_FILES = listOf(
            "myitmo_tokens.enc",
            "bars_tokens.enc",
            "widgets/schedule_snapshot.json",
            "debug/academic_date_override",
            "debug/sport_score_override",
            "debug/sport_lesson_templates"
        )

        val FILES_DIR_LAYOUT = mapOf(
            "calendar_sync" to listOf("state.json"),
            "diagnostics" to listOf("log.jsonl", "pending_crash.jsonl"),
            "marks" to listOf("state.json"),
            "qr_custom_spoiler" to listOf("custom_spoiler.png"),
            "schedule_changes" to listOf("state.json"),
            "sheet_scores" to listOf("state.json"),
            "subject_links" to listOf("cache.json"),
            "teacher_lessons" to listOf("weeks.json"),
            "teacher_levels" to listOf("levels.json")
        )

        val CACHE_DIRS = listOf("bitmap_cache", "ics", "qr_hex", "schedule_cache")

        val BACKUP_EXCLUSIONS = setOf(
            "calendar_sync/",
            "marks/",
            "schedule_changes/",
            "sheet_scores/",
            "subject_links/",
            "teacher_lessons/",
            "teacher_levels/",
            "datastore/app_preferences.preferences_pb"
        )

        val QUOTED_RULES = listOf(
            "network clients are gated by demo mode",
            "debug code is gated",
            "distribution variants take the download address from BuildConfig"
        )
    }
}
