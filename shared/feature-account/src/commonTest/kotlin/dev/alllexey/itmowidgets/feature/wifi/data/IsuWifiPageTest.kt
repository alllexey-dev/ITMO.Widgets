package dev.alllexey.itmowidgets.feature.wifi.data

import dev.alllexey.itmowidgets.core.network.IsuException
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiAccess
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** ISU's Wi-Fi page in the shape it had on 2026-10-26, with synthetic values. */
class IsuWifiPageTest {

    @Test
    fun readsTheLoginAndTheActivePassword() {
        assertEquals(WifiAccess.Ready(WifiCredentials("123456", "12345678")), IsuWifiPage.parse(page(), owner = 123456))
    }

    @Test
    fun decodesEntitiesAndTrims() {
        val access = IsuWifiPage.parse(page(password = " a&amp;b&#60;c&#x3E; "), owner = 123456)

        assertEquals(WifiAccess.Ready(WifiCredentials("123456", "a&b<c>")), access)
    }

    @Test
    fun aFieldWithoutAPasswordMeansNoneWasIssued() {
        assertEquals(WifiAccess.NoPassword, IsuWifiPage.parse(page(password = null), owner = 123456))
        assertEquals(WifiAccess.NoPassword, IsuWifiPage.parse(page(password = ""), owner = 123456))
    }

    @Test
    fun anotherAccountsPageIsRefused() {
        assertFailsWith<IsuException> { IsuWifiPage.parse(page(), owner = 654321) }
    }

    @Test
    fun aPageWithoutTheFieldsFails() {
        assertFailsWith<IsuException> { IsuWifiPage.parse("<html>Старт</html>", owner = 123456) }
    }

    private fun page(password: String? = "12345678"): String {
        val passwordSpan = password?.let { """<span class="wifi-pass wifi-pass-hidden text-bg">$it</span>""" }.orEmpty()
        return """
            <input type="hidden" name="p_t02" value="123456" id="USER_ID" />
            <div id="wifi_account_panel" class="panel " >
            <div class="panel-body">
            <div class="form-group"><label for="P6_USERNAME" class="control-label optional optional-help"><a href="javascript:G2.help('1','2')" class="optional-w-help">Имя пользователя</a></label><span id="P6_USERNAME" class="display_only text-bg"  >123456</span></div><div class="form-group"><label for="P6_PASS_CURRENT" class="control-label optional optional-help"><a href="javascript:G2.help('3','2')" class="optional-w-help">Активный пароль Wi-Fi</a></label><span id="P6_PASS_CURRENT" class="display_only" >$passwordSpan</span></div><div class="form-group"><label for="P6_PASS_NEW" class="control-label optional optional-help"><a href="javascript:G2.help('4','2')" class="optional-w-help">Новый пароль Wi-Fi</a></label><span id="P6_PASS_NEW" class="display_only" ><span class="text-muted">не запрошен</span></span></div>
            </div></div>
        """.trimIndent()
    }
}
