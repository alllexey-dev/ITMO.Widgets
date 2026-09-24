package dev.alllexey.itmowidgets.core.ui

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkIconResTest {
    @Test fun `chats show the messenger they lead to`() {
        assertEquals(R.drawable.ic_brand_telegram, linkIconRes(LinkCategory.CHAT, "https://t.me/+AbCd"))
        assertEquals(R.drawable.ic_brand_telegram, linkIconRes(LinkCategory.CHAT, " https://telegram.me/itmo "))
        assertEquals(R.drawable.ic_brand_vk, linkIconRes(LinkCategory.CHAT, "https://vk.me/join/abc"))
        assertEquals(R.drawable.ic_brand_vk, linkIconRes(LinkCategory.CHAT, "https://www.vk.com/im?sel=c1"))
    }

    @Test fun `unknown chats and other categories keep the category symbol`() {
        assertEquals(R.drawable.ic_chat, linkIconRes(LinkCategory.CHAT, "https://chat.whatsapp.com/x"))
        assertEquals(R.drawable.ic_chat, linkIconRes(LinkCategory.CHAT, "не ссылка"))
        assertEquals(R.drawable.ic_table, linkIconRes(LinkCategory.SCORES, "https://t.me/x"))
    }
}
