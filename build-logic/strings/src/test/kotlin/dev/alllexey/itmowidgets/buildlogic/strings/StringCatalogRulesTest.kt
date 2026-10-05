package dev.alllexey.itmowidgets.buildlogic.strings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StringCatalogRulesTest {

    private val platform = fixture("strings_platform.xml", "app/src/main/res/values/strings_platform.xml")
    private val frozen = listOf("app_name", "lessons_count")

    @Test
    fun `a clean catalog passes every rule`() {
        assertEquals(emptyList<String>(), StringCatalogRules.check(listOf(platform), frozen))
    }

    @Test
    fun `a bare placeholder fails`() {
        val file = fixture("bare_placeholder.xml")

        assertEquals(
            listOf("catalog/bare_placeholder.xml: changes_title has the placeholder '%d'; use %1\$s, %1\$d or %%"),
            StringCatalogRules.positionalPlaceholders(listOf(file)),
        )
    }

    @Test
    fun `a plural without many fails`() {
        val file = fixture("missing_many.xml")

        assertEquals(
            listOf("catalog/missing_many.xml: points_left lacks the plural forms [many]"),
            StringCatalogRules.completePlurals(listOf(file)),
        )
    }

    @Test
    fun `a key in two files fails`() {
        val module = fixture("duplicate.xml", "shared/core/src/commonMain/composeResources/values/strings_common.xml")

        assertEquals(
            listOf(
                "app_name is defined 2 times: app/src/main/res/values/strings_platform.xml, " +
                    "shared/core/src/commonMain/composeResources/values/strings_common.xml",
            ),
            StringCatalogRules.uniqueKeys(listOf(platform, module)),
        )
    }

    @Test
    fun `translatable fails`() {
        val file = fixture("translatable.xml")

        assertEquals(
            listOf("catalog/translatable.xml: project_name has translatable"),
            StringCatalogRules.noTranslatable(listOf(file)),
        )
    }

    @Test
    fun `a frozen key outside every platform file fails`() {
        val elsewhere = fixture("duplicate.xml", "app/src/main/res/values/strings_common.xml")

        assertEquals(
            listOf("frozen key app_name is missing from every strings_platform.xml"),
            StringCatalogRules.frozenKeys(listOf(elsewhere), listOf("app_name")),
        )
    }

    @Test
    fun `a missing frozen key fails`() {
        assertEquals(
            listOf("frozen key notification_sport_success is missing from every strings_platform.xml"),
            StringCatalogRules.frozenKeys(listOf(platform), frozen + "notification_sport_success"),
        )
    }

    @Test
    fun `an unsorted frozen key list fails`() {
        assertEquals(
            listOf("scripts/strings-frozen-keys.txt is not sorted and unique"),
            StringCatalogRules.frozenKeys(listOf(platform), frozen.reversed()),
        )
    }

    @Test
    fun `a URL fails while a bare scheme passes`() {
        val file = fixture("url.xml")

        assertEquals(
            listOf("catalog/url.xml: project_github_url holds a URL; move it to a constant"),
            StringCatalogRules.noUrls(listOf(file, platform)),
        )
    }

    @Test
    fun `check reports every rule`() {
        val files = listOf("bare_placeholder.xml", "missing_many.xml", "translatable.xml", "url.xml").map { fixture(it) }

        assertEquals(6, StringCatalogRules.check(files, frozen).size)
    }

    private fun fixture(name: String, path: String = "catalog/$name"): CatalogFile =
        CatalogFile.parse(path, File(requireNotNull(javaClass.getResource("/catalog/$name")).toURI()))
}
