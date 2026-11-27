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

    @Test fun `a Google Drive file is read by its id`() {
        listOf(
            "https://drive.google.com/file/d/$id/view?usp=sharing",
            "https://drive.google.com/file/d/$id",
            "https://drive.google.com/file/u/1/d/$id/edit",
            "https://drive.google.com/open?id=$id&authuser=0",
        ).forEach { assertEquals(it, GoogleSheetUrl(id, null), GoogleSheetUrl.parse(it)) }
        assertNull(GoogleSheetUrl.parseSheets("https://drive.google.com/file/d/$id/view"))
    }

    @Test fun `other Drive addresses are not sheets`() {
        listOf(
            "http://drive.google.com/file/d/$id/view",
            "https://drive.google.com.evil.com/file/d/$id/view",
            "https://drive.google.com/drive/folders/$id",
            "https://drive.google.com/file/d/short/view",
            "https://drive.google.com/uc?id=$id",
            "https://drive.google.com/open?id=short",
        ).forEach { assertNull(it, GoogleSheetUrl.parse(it)) }
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
