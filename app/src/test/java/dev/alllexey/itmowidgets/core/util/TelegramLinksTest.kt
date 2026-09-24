package dev.alllexey.itmowidgets.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramLinksTest {
    @Test fun `public usernames, posts and invites map to the client scheme`() {
        assertEquals("tg://resolve?domain=itmowidgets", TelegramLinks.deepLink("https://t.me/itmowidgets"))
        assertEquals("tg://resolve?domain=itmowidgets&post=42", TelegramLinks.deepLink("https://t.me/itmowidgets/42"))
        assertEquals("tg://resolve?domain=itmo_group", TelegramLinks.deepLink(" https://www.telegram.me/itmo_group "))
        assertEquals("tg://join?invite=AbCd-12_x", TelegramLinks.deepLink("https://t.me/+AbCd-12_x"))
        assertEquals("tg://join?invite=AbCd", TelegramLinks.deepLink("https://t.me/joinchat/AbCd"))
        assertEquals("tg://privatepost?channel=1234567&post=89", TelegramLinks.deepLink("https://t.me/c/1234567/89"))
    }

    @Test fun `anything else opens as is`() {
        assertNull(TelegramLinks.deepLink("https://vk.me/join/abc"))
        assertNull(TelegramLinks.deepLink("http://t.me/itmowidgets"))
        assertNull(TelegramLinks.deepLink("https://t.me/"))
        assertNull(TelegramLinks.deepLink("https://t.me/addstickers/pack"))
        assertNull(TelegramLinks.deepLink("https://t.me/abc"))
        assertNull(TelegramLinks.deepLink("не ссылка"))
    }
}
