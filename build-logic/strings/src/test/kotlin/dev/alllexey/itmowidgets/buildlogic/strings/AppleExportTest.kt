package dev.alllexey.itmowidgets.buildlogic.strings

import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppleExportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val fixtures = File(requireNotNull(javaClass.getResource("/apple")).toURI())
    private val repo = File(fixtures, "repo")
    private val golden = File(fixtures, "golden")

    @Test
    fun `the fixture catalog exports the golden tables and AppSymbol`() {
        assertEquals(tree(golden), export())
    }

    @Test
    fun `strings without and with several arguments convert their placeholders`() {
        val settings = table("strings_settings")

        assertEquals("\"Настройки\"", settings.value("settings_title"))
        assertEquals("\"%1\$@: синхронизировано %2\$lld из %3\$lld\"", settings.value("settings_sync_status"))
    }

    @Test
    fun `escapes, quotes and the no-break space survive`() {
        val settings = table("strings_settings")

        assertEquals(
            "\"Строка\\nвторая, лимит 100%% \\\"в кавычках\\\" и'апостроф\"",
            settings.value("settings_escapes"),
        )
        assertEquals("\"%1\$@ и ещё\u00A0%2\$lld\"", settings.value("settings_more"))
        assertEquals("\"  два  пробела   и хвост\"", settings.value("settings_spaces"))
    }

    @Test
    fun `a plural with its count becomes variations plural`() {
        val plural = table("strings_settings").block("settings_lessons")

        assertEquals(true, plural.contains("\"variations\" : {\n            \"plural\" : {"))
        assertEquals(true, plural.contains("\"value\" : \"%1\$lld пар\""))
    }

    @Test
    fun `a plural without a placeholder selects by a count substitution`() {
        val plural = table("strings_settings").block("settings_free_places")

        assertEquals(true, plural.contains("\"value\" : \"%#@count@\""))
        assertEquals(true, plural.contains("\"argNum\" : 1,\n              \"formatSpecifier\" : \"lld\","))
        assertEquals(true, plural.contains("\"value\" : \"Свободных мест\""))
    }

    @Test
    fun `same-named files feed one table and platform and iOS files feed Localizable`() {
        val outputs = AppleExport.render(catalog(), tableRows(), iconRows())

        assertEquals(
            listOf(
                "Strings/InfoPlist.xcstrings",
                "Strings/Localizable.xcstrings",
                "Strings/strings_common.xcstrings",
                "Strings/strings_settings.xcstrings",
                AppleExport.SYMBOLS_FILE,
            ),
            outputs.keys.sorted(),
        )
        assertEquals(listOf("common_close", "common_retry"), keys(outputs.getValue("Strings/strings_common.xcstrings")))
        assertEquals(
            listOf("app_name", "ios_camera_usage", "notification_sport_success"),
            keys(outputs.getValue("Strings/Localizable.xcstrings")),
        )
    }

    @Test
    fun `a moved file changes no table`() {
        val moved = catalog().map { file ->
            if (file.path.startsWith("app/") && file.fileName == "strings_settings.xml") {
                CatalogFile("shared/settings/src/commonMain/composeResources/values/strings_settings.xml", file.entries)
            } else {
                file
            }
        }

        assertEquals(export(), AppleExport.render(moved, tableRows(), iconRows()))
    }

    @Test
    fun `a hand edit, a missing table and a stale table fail the freshness check`() {
        val shared = temp.newFolder("Shared")
        val outputs = AppleExport.render(catalog(), tableRows(), iconRows())
        AppleExport.write(outputs, shared)
        assertEquals(emptyList<String>(), AppleExport.staleOutputs(outputs, shared, "iosApp/Shared"))

        val settings = File(shared, "Strings/strings_settings.xcstrings")
        settings.writeText(settings.readText().replace("Настройки", "Параметры"))
        File(shared, "Strings/strings_common.xcstrings").delete()
        File(shared, "Strings/strings_old.xcstrings").writeText("{}")

        assertEquals(
            listOf(
                "iosApp/Shared/Strings/strings_common.xcstrings is missing",
                "iosApp/Shared/Strings/strings_settings.xcstrings differs from a fresh export",
                "iosApp/Shared/Strings/strings_old.xcstrings is not produced by the export",
            ),
            AppleExport.staleOutputs(outputs, shared, "iosApp/Shared"),
        )
    }

    @Test
    fun `write deletes a table the export no longer produces and keeps other files`() {
        val shared = temp.newFolder("Shared")
        File(shared, "Strings").mkdirs()
        File(shared, "Strings/strings_old.xcstrings").writeText("{}")
        File(shared, "Symbols/Custom.xcassets").mkdirs()

        AppleExport.write(AppleExport.render(catalog(), tableRows(), iconRows()), shared)

        assertEquals(false, File(shared, "Strings/strings_old.xcstrings").exists())
        assertEquals(true, File(shared, "Symbols/Custom.xcassets").isDirectory)
    }

    @Test
    fun `a plural with arguments but no count in argument 1 fails`() {
        val file = CatalogFile(
            "app/src/main/res/values/strings_x.xml",
            listOf(
                CatalogEntry(
                    key = "x_left",
                    kind = "plurals",
                    texts = emptyList(),
                    quantities = emptySet(),
                    attributes = emptySet(),
                    forms = mapOf("one" to "%1\$s: %2\$d", "other" to "%1\$s: %2\$d"),
                ),
            ),
        )

        assertThrows(GradleException::class.java) { XcStrings.tables(listOf(file), emptyList()) }
    }

    @Test
    fun `a table row naming an unknown table, a missing key or a plural fails`() {
        assertThrows(GradleException::class.java) { AppleTableRow.parse(listOf("Widgets.title=app_name")) }
        assertThrows(GradleException::class.java) {
            XcStrings.tables(catalog(), AppleTableRow.parse(listOf("InfoPlist.CFBundleName=no_such_key")))
        }
        assertThrows(GradleException::class.java) {
            XcStrings.tables(catalog(), AppleTableRow.parse(listOf("AppShortcuts.lessons=settings_lessons")))
        }
    }

    @Test
    fun `an icon id becomes a camel-case Swift case`() {
        assertEquals("accountCircleFilled", AppSymbolSwift.caseName("account_circle_filled"))
        assertEquals("`repeat`", AppSymbolSwift.caseName("repeat"))
    }

    private fun export(): Map<String, String> = AppleExport.render(catalog(), tableRows(), iconRows())

    private fun catalog(): List<CatalogFile> = CatalogFile.parseAll(
        repo.walkTopDown().filter { it.isFile && it.extension == "xml" }.toList(),
        repo,
    )

    private fun tableRows() = File(repo, AppleExport.TABLES_FILE).readLines()

    private fun iconRows() = File(repo, AppleExport.ICONS_FILE).readLines()

    private fun table(name: String) = export().getValue("Strings/$name.xcstrings")

    /** The JSON text after `"value" : ` in the block of [key]; enough for single-unit strings. */
    private fun String.value(key: String): String =
        block(key).substringAfter("\"value\" : ").substringBefore('\n')

    private fun String.block(key: String): String = substringAfter("\n    \"$key\" : {").substringBefore("\n    }")

    private fun keys(table: String): List<String> =
        Regex("""^ {4}"([^"]+)" : \{$""", RegexOption.MULTILINE).findAll(table).map { it.groupValues[1] }.toList()

    private fun tree(dir: File): Map<String, String> = dir.walkTopDown().filter { it.isFile }
        .associate { it.relativeTo(dir).invariantSeparatorsPath to it.readText() }
}
