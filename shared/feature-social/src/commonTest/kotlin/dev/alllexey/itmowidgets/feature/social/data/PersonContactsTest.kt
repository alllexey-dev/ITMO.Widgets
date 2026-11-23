package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.feature.social.domain.model.ContactKind
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonContact
import kotlin.test.Test
import kotlin.test.assertEquals

/** The value shapes of the My ITMO directory survey (issue #612), with synthetic values only. */
class PersonContactsTest {

    private fun assertContact(alias: String, value: String, kind: ContactKind, shown: String, url: String?) =
        assertEquals(PersonContact(kind, alias, shown, url), personContact(alias, value), "$alias: $value")

    @Test
    fun mailAndPhonesOpenAsTheyAre() {
        assertContact("email", "test@example.test", ContactKind.EMAIL, "test@example.test", "mailto:test@example.test")
        assertContact("phone", "+7(812)0000000", ContactKind.PHONE, "+7(812)0000000", "tel:+78120000000")
        assertContact("phone", "+70000000000", ContactKind.PHONE, "+70000000000", "tel:+70000000000")
        assertContact("phone", "+7812 0000000", ContactKind.PHONE, "+7812 0000000", "tel:+78120000000")
        assertContact("email", "not a mail", ContactKind.EMAIL, "not a mail", null)
    }

    @Test
    fun sitesWithoutASchemeGetHttps() {
        assertContact("web", "t.me/test_handle", ContactKind.WEB, "t.me/test_handle", "https://t.me/test_handle")
        assertContact("web", "vk.com/test.handle", ContactKind.WEB, "vk.com/test.handle", "https://vk.com/test.handle")
        assertContact("web", "github.com/test/repo", ContactKind.WEB, "github.com/test/repo", "https://github.com/test/repo")
        assertContact("web", "scholar.example.test/citations?user=abc123", ContactKind.WEB,
            "scholar.example.test/citations?user=abc123", "https://scholar.example.test/citations?user=abc123")
        assertContact("web", "test.example.test", ContactKind.WEB, "test.example.test", "https://test.example.test")
        assertContact("web", "https://example.test/page", ContactKind.WEB, "https://example.test/page", "https://example.test/page")
        assertContact("web", "Личная страница на сайте", ContactKind.WEB, "Личная страница на сайте", null)
        assertContact("web", "http://example.test", ContactKind.WEB, "http://example.test", null)
        assertContact("web", "localhost", ContactKind.WEB, "localhost", null)
        assertContact("lj", "test.livejournal.com/", ContactKind.LIVEJOURNAL, "test.livejournal.com/", "https://test.livejournal.com/")
    }

    @Test
    fun vkHandlesLinkAndShowWithAnAt() {
        assertContact("vkontakte", "test_handle", ContactKind.VK, "@test_handle", "https://vk.com/test_handle")
        assertContact("vkontakte", "id12345", ContactKind.VK, "@id12345", "https://vk.com/id12345")
        assertContact("vkontakte", "test.handle", ContactKind.VK, "@test.handle", "https://vk.com/test.handle")
        assertContact("VK", "https://vk.com/test", ContactKind.VK, "@test", "https://vk.com/test")
        assertContact("Вконтакте", "vk.com/test/", ContactKind.VK, "@test", "https://vk.com/test")
        assertContact("vkontakte", "test..handle", ContactKind.VK, "test..handle", null)
        assertContact("vkontakte", "https://example.test/test", ContactKind.VK, "https://example.test/test", null)
    }

    @Test
    fun telegramAndXHandlesShowWithAnAt() {
        assertContact("telegram", "test_handle", ContactKind.TELEGRAM, "@test_handle", "https://t.me/test_handle")
        assertContact("telegram", "@test_handle", ContactKind.TELEGRAM, "@test_handle", "https://t.me/test_handle")
        assertContact("telegram", "https://t.me/test", ContactKind.TELEGRAM, "@test", "https://t.me/test")
        assertContact("twitter", "test_handle", ContactKind.TWITTER, "@test_handle", "https://twitter.com/test_handle")
        assertContact("telegram", "test/../x", ContactKind.TELEGRAM, "test/../x", null)
    }

    @Test
    fun linkedInLinksTheProfileAndShowsTheSlug() {
        val url = "https://www.linkedin.com/in/test-slug-123"
        assertContact("linkedin", "in/test-slug-123", ContactKind.LINKEDIN, "test-slug-123", url)
        assertContact("linkedin", "in/test-slug-123?trk=nav_responsive_tab_profile", ContactKind.LINKEDIN, "test-slug-123", url)
        assertContact("linkedin", "in/test-slug-123/", ContactKind.LINKEDIN, "test-slug-123", url)
        assertContact("linkedin", "test-slug-123", ContactKind.LINKEDIN, "test-slug-123", url)
        assertContact("linkedin", "https://www.linkedin.com/in/test-slug-123/?trk=x", ContactKind.LINKEDIN, "test-slug-123", url)
        assertContact("linkedin", "company/test", ContactKind.LINKEDIN, "company/test", null)
    }

    @Test
    fun facebookLinksTheProfile() {
        assertContact("facebook", "test.handle", ContactKind.FACEBOOK, "test.handle", "https://www.facebook.com/test.handle")
        assertContact("facebook", "profile.php?id=1", ContactKind.FACEBOOK, "profile.php?id=1", null)
    }

    @Test
    fun skypeAndUnknownShapesStayPlainText() {
        assertContact("skype", "test.login", ContactKind.SKYPE, "test.login", null)
        assertContact("skype", "https://example.test/page", ContactKind.SKYPE, "https://example.test/page", null)
        assertContact("icq", "123456", ContactKind.OTHER, "123456", null)
        assertContact("Другое", "https://example.test", ContactKind.OTHER, "https://example.test", "https://example.test")
        assertContact("Другое", "javascript:alert(1)", ContactKind.OTHER, "javascript:alert(1)", null)
        assertContact("telegram", "test handle", ContactKind.TELEGRAM, "test handle", null)
        assertContact("web", "example.test/\u0007", ContactKind.WEB, "example.test/\u0007", null)
    }
}
