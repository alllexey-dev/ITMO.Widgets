package dev.alllexey.itmowidgets.core.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleSheetUrlTest {
    private val id = "1TestSheetIdForUnitTests_0123456789-abc"
    private val base = "https://docs.google.com/spreadsheets/d/$id"

    @Test fun `a sharing link has the id and no tab`() {
        assertEquals(GoogleSheetUrl(id, null), GoogleSheetUrl.parse("$base/edit?usp=sharing"))
    }

    @Test fun `the tab comes from the fragment, then from the parameter`() {
        assertEquals(1583779415L, GoogleSheetUrl.parse("$base/edit?gid=1583779415#gid=1583779415")?.gid)
        assertEquals(42L, GoogleSheetUrl.parse("$base/edit#gid=42")?.gid)
        assertEquals(7L, GoogleSheetUrl.parse("$base/edit?usp=sharing&gid=7")?.gid)
        assertEquals(42L, GoogleSheetUrl.parse("$base/edit?gid=7#gid=42")?.gid)
        assertEquals(42L, GoogleSheetUrl.parse("$base/edit#range=A1&gid=42")?.gid)
    }

    @Test fun `an account path is the same sheet`() {
        assertEquals(GoogleSheetUrl(id, null), GoogleSheetUrl.parse("https://docs.google.com/spreadsheets/u/0/d/$id/edit"))
        assertEquals(GoogleSheetUrl(id, null), GoogleSheetUrl.parse("  $base  "))
    }

    @Test fun `other addresses are not sheets`() {
        listOf(
            "http://docs.google.com/spreadsheets/d/$id/edit",
            "https://docs.google.com.evil.com/spreadsheets/d/$id/edit",
            "https://evil.com/docs.google.com/spreadsheets/d/$id",
            "https://docs.google.com/forms/d/$id/viewform",
            "https://docs.google.com/document/d/$id/edit",
            "https://docs.google.com/spreadsheets/d/e/2PACX-1vTestPublishedSheetId0123456789/pubhtml",
            "https://docs.google.com/spreadsheets/d/short/edit",
            "docs.google.com/spreadsheets/d/$id",
            "not a link",
        ).forEach { assertNull(it, GoogleSheetUrl.parse(it)) }
    }

    @Test fun `a tab that is not a number is no tab`() {
        assertEquals(GoogleSheetUrl(id, null), GoogleSheetUrl.parse("$base/edit#gid=abc"))
        assertEquals(GoogleSheetUrl(id, null), GoogleSheetUrl.parse("$base/edit?gid="))
    }

    @Test fun `the tab address opens the tab in the browser`() {
        assertEquals("$base/edit#gid=42", GoogleSheetUrl(id, null).tabUrl(42))
    }
}
