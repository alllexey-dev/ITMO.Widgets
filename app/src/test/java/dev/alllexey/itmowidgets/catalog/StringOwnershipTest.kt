package dev.alllexey.itmowidgets.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The string catalog is split by owner, not by prefix (ADR 0028): `strings_<unit>.xml` per v2.3 module,
 * `strings_common.xml` for ids of two or more units or of `core/`, `strings_platform.xml` for every id a system
 * surface reaches. `scripts/strings-owners.py --where <id|path>` names the file for a new string.
 */
class StringOwnershipTest {

    private val module = listOf(File("."), File("app"))
        .map { it.absoluteFile.normalize() }
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
    private val sources = File(module, "src")
    private val resources = File(sources, "main/res")
    private val catalog by lazy {
        File(resources, "values").listFiles { file -> file.name.startsWith("strings") }.orEmpty().sortedBy { it.name }
    }
    private val fileOf by lazy {
        catalog.flatMap { file -> entries(file).map { it.getAttribute("name") to unitOfCatalog(file) } }.toMap()
    }
    private val layouts by lazy { resourceFiles("layout") }
    private val menus by lazy { resourceFiles("menu") }
    private val kotlinUsers by lazy {
        PRODUCTION_SETS.flatMap { set -> File(sources, "$set/java").walkTopDown().filter { it.extension == "kt" } }
            .map { it to it.readText() }
    }
    private val layoutUnits by lazy {
        val bindings = layouts.keys.associateBy { binding(it) }
        val users = layouts.keys.associateWith { mutableSetOf<String>() }
        kotlinUsers.forEach { (file, text) ->
            val unit = unitOf(file)
            references(text, "layout").forEach { users[it]?.add(unit) }
            BINDING.findAll(text).mapNotNull { bindings[it.groupValues[1]] }.forEach { users.getValue(it).add(unit) }
        }
        APP_LAYOUTS.forEach { users.getValue(it).add(APP) }
        val parents = layouts.keys.associateWith { mutableSetOf<String>() }
        layouts.forEach { (name, file) -> xmlReferences(file.readText(), "layout").forEach { parents[it]?.add(name) } }
        layouts.keys.associateWith { unitsOf(it, users, parents, emptySet()) }
    }

    @Test
    fun `strings xml is split into owner files`() {
        assertFalse("strings.xml is split; add strings to strings_<file>.xml", File(resources, "values/strings.xml").exists())
        assertEquals(CATALOG_FILES.map { "strings_$it.xml" }.toSet(), catalog.map { it.name }.toSet())
    }

    @Test
    fun `every string id is defined once`() {
        val names = sources.listFiles().orEmpty()
            .flatMap { set -> File(set, "res").listFiles { dir -> dir.name.startsWith("values") }.orEmpty().toList() }
            .flatMap { dir -> dir.listFiles { file -> file.extension == "xml" }.orEmpty().toList() }
            .flatMap { file -> entries(file).map { it.getAttribute("name") } }
        assertEquals(emptyList<String>(), names.groupBy { it }.filterValues { it.size > 1 }.keys.sorted())
    }

    @Test
    fun `platform file holds every frozen key`() {
        val frozen = File(module.parentFile, "scripts/strings-frozen-keys.txt").readLines().filter { it.isNotBlank() }
        assertEquals("strings-frozen-keys.txt is sorted and unique", frozen.distinct().sorted(), frozen)
        assertEquals(emptyList<String>(), frozen.filter { fileOf[it] != PLATFORM })
    }

    @Test
    fun `system surfaces bind only platform strings`() {
        val surfaces = PRODUCTION_SETS.map { File(sources, "$it/AndroidManifest.xml") }.filter { it.isFile } +
            resources.listFiles { dir -> dir.name.startsWith("xml") }.orEmpty().flatMap { it.listFiles().orEmpty().toList() }
        val widgetLayouts = surfaces.flatMap { xmlReferences(it.readText(), "layout") }.map { layouts.getValue(it) }
        val misplaced = (surfaces + widgetLayouts).flatMap { file ->
            stringReferences(file.readText()).filter { fileOf[it] != PLATFORM }.map { "${file.name}: $it" }
        }
        assertEquals(emptyList<String>(), misplaced.distinct())
    }

    @Test
    fun `feature strings are used only by their own module`() {
        val misuse = mutableListOf<String>()
        kotlinUsers.forEach { (file, text) ->
            val unit = unitOf(file)
            (references(text, "string") + references(text, "plurals")).forEach { id ->
                val owner = fileOf[id]
                if (owner != null && owner !in SHARED_FILES && owner != unit) misuse += "${file.name} ($unit): $id of $owner"
            }
        }
        layouts.forEach { (name, file) ->
            checkXmlUsers(file, layoutUnits.getValue(name), misuse)
        }
        val menuUsers = menus.keys.associateWith { mutableSetOf<String>() }
        kotlinUsers.forEach { (file, text) -> references(text, "menu").forEach { menuUsers[it]?.add(unitOf(file)) } }
        layouts.forEach { (name, file) ->
            xmlReferences(file.readText(), "menu").forEach { menuUsers[it]?.addAll(layoutUnits.getValue(name)) }
        }
        APP_MENUS.forEach { menuUsers.getValue(it).add(APP) }
        menus.forEach { (name, file) -> checkXmlUsers(file, menuUsers.getValue(name), misuse) }
        resourceFiles("navigation").values.forEach { checkXmlUsers(it, setOf(APP), misuse) }
        assertEquals(emptyList<String>(), misuse.distinct().sorted())
    }

    @Test
    fun `xml carries no russian literal text`() {
        val files = PRODUCTION_SETS.map { File(sources, "$it/AndroidManifest.xml") }.filter { it.isFile } +
            resources.listFiles { dir -> dir.name.substringBefore('-') in LITERAL_FREE_DIRS }.orEmpty()
                .flatMap { it.listFiles { file -> file.extension == "xml" }.orEmpty().toList() }
                .filter { it.nameWithoutExtension !in LAUNCHER_PREVIEW_LAYOUTS }
        val literals = files.flatMap { file ->
            elements(document(file).documentElement).flatMap { element ->
                (0 until element.attributes.length).map { element.attributes.item(it) }
                    .filter { it.nodeName.substringAfter(':') in TEXT_ATTRIBUTES && !it.nodeName.startsWith("tools:") }
                    .filter { CYRILLIC.containsMatchIn(it.nodeValue) }
                    .map { "${file.parentFile.name}/${file.name}: ${it.nodeName}" }
            }
        }
        assertEquals(emptyList<String>(), literals)
    }

    private fun checkXmlUsers(file: File, units: Set<String>, misuse: MutableList<String>) {
        stringReferences(file.readText()).forEach { id ->
            val owner = fileOf[id] ?: return@forEach
            if (owner in SHARED_FILES) return@forEach
            units.filter { it != owner }.forEach { misuse += "${file.name} ($it): $id of $owner" }
            assertTrue("${file.name} has no user in app/src/{main,github,play}", units.isNotEmpty())
        }
    }

    private fun unitsOf(
        name: String,
        users: Map<String, Set<String>>,
        parents: Map<String, Set<String>>,
        seen: Set<String>
    ): Set<String> {
        if (name in seen) return emptySet()
        return users.getValue(name) + parents.getValue(name).flatMap { unitsOf(it, users, parents, seen + name) }
    }

    private fun unitOf(file: File): String {
        val path = file.invariantSeparatorsPath.substringAfter("/$PACKAGE/").split('/')
        return when {
            path.size == 1 || path[0] == "app" || path[0] == "di" -> APP
            path[0] == "core" -> if (path[1] == "debug") DEBUG else CORE
            path[0] == "feature" -> FEATURE_UNITS[path[1]] ?: error("${file.path}: no owner unit for feature/${path[1]}")
            else -> error("${file.path}: no owner unit")
        }
    }

    private fun unitOfCatalog(file: File): String = file.nameWithoutExtension.removePrefix("strings_")

    private fun resourceFiles(type: String): Map<String, File> =
        File(resources, type).listFiles { file -> file.extension == "xml" }.orEmpty().associateBy { it.nameWithoutExtension }

    private fun references(text: String, kind: String): Set<String> {
        val alias = if (APP_R_ALIAS.containsMatchIn(text)) "AppR" else "R"
        return Regex("""(?:(?<![\w.])|(?<=itmowidgets\.))$alias\.$kind\.(\w+)""").findAll(text)
            .map { it.groupValues[1] }.toSet()
    }

    private fun xmlReferences(text: String, kind: String): Set<String> =
        Regex("""([\w:]+)\s*=\s*"@$kind/(\w+)"""").findAll(text)
            .filterNot { it.groupValues[1].startsWith("tools:") }
            .map { it.groupValues[2] }.toSet()

    private fun stringReferences(text: String) = xmlReferences(text, "string") + xmlReferences(text, "plurals")

    private fun binding(layout: String) =
        layout.split('_').joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } } + "Binding"

    private fun entries(file: File): List<Element> = children(document(file).documentElement)
        .filter { it.tagName == "string" || it.tagName == "plurals" }

    private fun document(file: File) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    private fun children(element: Element): List<Element> =
        (0 until element.childNodes.length).map { element.childNodes.item(it) }.filterIsInstance<Element>()

    private fun elements(element: Element): List<Element> = listOf(element) + children(element).flatMap { elements(it) }

    private companion object {
        const val PACKAGE = "dev/alllexey/itmowidgets"
        const val APP = "app"
        const val CORE = "core"
        const val DEBUG = "debug"
        const val PLATFORM = "platform"
        val SHARED_FILES = setOf("common", PLATFORM)
        val CATALOG_FILES = setOf(
            "app", "common", "platform", "debug", "qr", "home", "schedule", "sport", "recordbook", "social",
            "settings", "resources", "reviews", "auth", "onboarding", "me", "weblogin", "web", "update"
        )
        // The v2.3 modules; src/debug previews and the test source sets may show any module's screens.
        val PRODUCTION_SETS = listOf("main", "github", "play")
        val FEATURE_UNITS = CATALOG_FILES.associateWith { it } + mapOf("friendselector" to "social")
        val APP_LAYOUTS = setOf("activity_main")
        val APP_MENUS = setOf("bottom_nav")
        val LITERAL_FREE_DIRS = setOf("layout", "menu", "navigation", "xml")
        val LAUNCHER_PREVIEW_LAYOUTS = setOf("widget_lesson_list_preview", "widget_single_lesson_preview")
        val TEXT_ATTRIBUTES = setOf("text", "label", "hint", "contentDescription")
        val CYRILLIC = Regex("[А-Яа-яЁё]")
        val BINDING = Regex("""\b([A-Z]\w*Binding)\b""")
        val APP_R_ALIAS = Regex("""import\s+dev\.alllexey\.itmowidgets\.R\s+as\s+AppR\b""")
    }
}
