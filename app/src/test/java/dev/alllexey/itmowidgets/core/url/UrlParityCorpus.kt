package dev.alllexey.itmowidgets.core.url

import kotlin.random.Random

/**
 * Inputs on which [StrictUri] and every link policy must decide exactly as they did on `java.net.URI`: the
 * hand-written [policyUrls] name the known traps, [combinations] mixes them into whole links and [noise] strings
 * random characters together. Fixed seeds keep every run on the same inputs.
 */
object UrlParityCorpus {

    val policyUrls: List<String> = listOf(
        // Plain links of every policy.
        "https://my.itmo.ru/", "https://my.itmo.ru/login/callback", "https://my.itmo.ru/login/callback?code=x&state=y",
        "https://id.itmo.ru/auth/realms/itmo", "https://my.itmo.ru:443/login?redirect=/", "https://t.me/itmowidgets",
        "https://t.me/itmowidgets/42", "https://t.me/+AbCd-12_x", "https://t.me/joinchat/AbCd", "https://t.me/c/1234567/89",
        "https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit#gid=12",
        "https://docs.google.com/spreadsheets/u/1/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit?gid=7",
        "https://docs.google.com/spreadsheets/d/e/2PACX-1vRabcdefghijklmnop/pubhtml", "https://docs.google.com/forms/d/e/x",
        "https://widgets.alllexey.dev/app/login?code=ABCD-2345", "https://dev.widgets.alllexey.dev/app/login/?code=abcd2345",
        "https://vk.com/video-123_456", "https://lms.itmo.ru/course/view.php?id=1", "github.com/itmo/labs",
        // Mixed-case schemes and hosts.
        "HTTPS://MY.ITMO.RU/", "Https://My.Itmo.Ru/login/callback", "hTTps://T.ME/itmowidgets", "HTTP://my.itmo.ru/",
        // Userinfo.
        "https://user@my.itmo.ru/", "https://@my.itmo.ru/", "https://user:pass@my.itmo.ru/login/callback",
        "https://my.itmo.ru@evil.invalid/", "https://a@b@my.itmo.ru/", "https://%40@my.itmo.ru/", "https://us er@my.itmo.ru/",
        "https://[user]@my.itmo.ru/", "https://user;x=y@t.me/itmowidgets",
        // Ports.
        "https://my.itmo.ru:443/", "https://my.itmo.ru:0443/", "https://my.itmo.ru:8443/", "https://my.itmo.ru:/",
        "https://my.itmo.ru:", "https://my.itmo.ru:99999999999/", "https://my.itmo.ru:2147483647/",
        "https://my.itmo.ru:2147483648/", "https://my.itmo.ru:-1/", "https://my.itmo.ru:+443/", "https://my.itmo.ru:44a/",
        "https://my.itmo.ru:443:443/", "https://:443/",
        // Host forms: IDN, punycode, IPv4, IPv6, odd labels.
        "https://пример.рф/", "https://xn--e1afmkfd.xn--p1ai/", "https://my.itmo.ру/", "https://t.me./itmowidgets",
        "https://my.itmo.ru./", "https://my..itmo.ru/", "https://.my.itmo.ru/", "https://-my.itmo.ru/", "https://my-.itmo.ru/",
        "https://my_itmo.ru/", "https://my%2eitmo.ru/", "https://my.itmo.ru%2F@evil.invalid/", "https://1.2.3.4/",
        "https://01.02.03.004/", "https://256.1.1.1/", "https://1.2.3/", "https://1.2.3.4.5/", "https://1.2.3.4./",
        "https://123/", "https://0x7f.1/", "https://[::1]/", "https://[::1]:443/", "https://[::]/",
        "https://[1:2:3:4:5:6:7:8]/", "https://[1:2:3:4:5:6:7::]/", "https://[1:2:3:4:5:6:7:8::]/", "https://[1::2::3]/",
        "https://[::ffff:1.2.3.4]/", "https://[1:2:3:4:5:6:1.2.3.4]/", "https://[fe80::1%eth0]/", "https://[fe80::1%]/",
        "https://[fe80::1%25eth0]/", "https://[12345::]/", "https://[::1/", "https://::1]/", "https://[my.itmo.ru]/",
        "https://localhost/", "https://my.itmo.ru\u00A0/", "https:///my.itmo.ru/", "https://", "https:", "https:/",
        // Paths, %2F and decoding.
        "https://my.itmo.ru/login%2Fcallback", "https://my.itmo.ru/%6Cogin/callback", "https://my.itmo.ru/login/callback/",
        "https://my.itmo.ru/login/callback;x", "https://my.itmo.ru//login/callback", "https://my.itmo.ru/login/./callback",
        "https://t.me/%2Bxyz", "https://t.me/itmo%2Fwidgets", "https://t.me//itmowidgets", "https://t.me/путь",
        "https://docs.google.com/spreadsheets%2Fd/1AbCdEfGhIjKlMnOpQrStUvWxYz", "https://widgets.alllexey.dev/app%2Flogin?code=ABCD2345",
        "https://widgets.alllexey.dev/%61pp/login?code=ABCD2345", "https://my.itmo.ru/%C3%28", "https://my.itmo.ru/%FF%FE",
        "https://my.itmo.ru/%zz", "https://my.itmo.ru/%4", "https://my.itmo.ru/a|b", "https://my.itmo.ru/[x]",
        // Queries, duplicate parameters and fragments.
        "https://widgets.alllexey.dev/app/login?code=ABCD2345&code=EFGH6789", "https://widgets.alllexey.dev/app/login?code=",
        "https://widgets.alllexey.dev/app/login?code=ABCD+2345", "https://widgets.alllexey.dev/app/login?code=ABCD%202345",
        "https://widgets.alllexey.dev/app/login?code=%41BCD2345", "https://widgets.alllexey.dev/app/login?code=ABCD%2",
        "https://widgets.alllexey.dev/app/login?Code=ABCD2345", "https://widgets.alllexey.dev/app/login?code=ABCD2345#x",
        "https://widgets.alllexey.dev/app/login#?code=ABCD2345", "https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit?gid=1#gid=2",
        "https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit#gid=1&gid=2",
        "https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit#gid=x", "https://my.itmo.ru/?a=[b]",
        "https://my.itmo.ru/#", "https://my.itmo.ru/?", "https://my.itmo.ru/#a#b", "https://my.itmo.ru/#[x]",
        "https://my.itmo.ru/?a b", "https://my.itmo.ru/#a b", "https://my.itmo.ru?x", "https://my.itmo.ru#x",
        // Backslashes, whitespace and controls.
        "https://my.itmo.ru\\@evil.invalid", "https:\\\\my.itmo.ru\\", "https://my.itmo.ru/\\login", " https://my.itmo.ru/",
        "https://my.itmo.ru/ ", "https://my.itmo.ru/\tx", "https://my.itmo.ru/\u2028", "https://my.itmo.ru/\u0000",
        "https://my.itmo.ru/\u0085", "https://my.itmo.ru/\u00AD", "https://my.itmo.ru/\u00A0",
        // Other schemes.
        "javascript:alert(1)", "JavaScript:alert(document.cookie)", "javascript://my.itmo.ru/%0Aalert(1)",
        "data:text/html,test", "file:///etc/passwd", "content://my.itmo.ru/", "intent://my.itmo.ru/#Intent;end",
        "tg://resolve?domain=itmowidgets", "mailto:a@b.c", "geo:0,0?q=x", "1https://my.itmo.ru/", "ht tps://my.itmo.ru/",
        "+https://my.itmo.ru/", "://my.itmo.ru/", "//my.itmo.ru/", "//", "/login/callback", "?code=ABCD2345", "#x", "",
        "not a URL", "не ссылка", "my.itmo.ru:443/login",
    )

    private val schemes = listOf("https", "HTTPS", "http", "javascript", "", "1x", "a+b", "tg")
    private val separators = listOf("://", ":", ":/", "", "//", ":\\\\")
    private val userInfos = listOf("", "", "", "user@", "@", "u:p@", "a@b@", "%41@", "[x]@", "u%zz@", "й@")
    private val hosts = listOf(
        "my.itmo.ru", "MY.ITMO.RU", "my.itmo.ru.", "пример.рф", "xn--e1afmkfd.xn--p1ai", "a_b.com", "1.2.3.4", "256.1.1.1",
        "1.2.3", "123", "[::1]", "[fe80::1%eth0]", "[1:2:3:4:5:6:7:8]", "[::ffff:1.2.3.4]", "[1::2::3]", "[::1", "-a.com",
        "a-.com", "a..b", "", "my%2eitmo.ru", "localhost", "t.me", "www.t.me", "docs.google.com", "widgets.alllexey.dev",
        "a b", "a\\b", "x.1", "x.1a", "a.b-c.d",
    )
    private val ports = listOf("", "", "", ":", ":443", ":8443", ":99999999999", ":-1", ":0x1", ":443x")
    private val paths = listOf(
        "", "/", "/login/callback", "/login%2Fcallback", "/%6Cogin/callback", "/a b", "/a\\b", "/путь", "/a%zz",
        "/app/login", "/app/login/", "/[x]", "/a|b", "/%C3%28", "/%FF", "/itmowidgets", "/+AbCd", "/joinchat/AbCd",
        "/c/123/45", "/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit", "/forms/x", "/video-1", "//x", "/a;b=c",
    )
    private val queries = listOf(
        "", "", "?", "?code=ABCD-2345", "?code=a+b", "?code=%41BCD2345", "?gid=1&gid=2", "?a=[b]", "?%", "?a b", "?a?b",
    )
    private val fragments = listOf("", "", "#", "#gid=5", "#a#b", "#[x]", "#a b", "#%41", "#gid=1&gid=2")

    /** Whole links glued from the parts above. */
    fun combinations(count: Int, seed: Int = 23): List<String> {
        val random = Random(seed)
        return List(count) {
            schemes.random(random) + separators.random(random) + userInfos.random(random) + hosts.random(random) +
                ports.random(random) + paths.random(random) + queries.random(random) + fragments.random(random)
        }
    }

    private const val NOISE = "aZ09:/?#[]@%!$&'()*+,;=-._~\\ |^`{}\"<>éй\u00A0\u2028\u0085\u0000\t" +
        "\uD83D\uDE00\ud800"

    /** Short strings of URI delimiters, escapes and characters on both sides of every class boundary. */
    fun noise(count: Int, seed: Int = 7): List<String> {
        val random = Random(seed)
        val prefixes = listOf("", "https://", "https:", "//", "a:", "https://h")
        return List(count) {
            val body = buildString { repeat(random.nextInt(0, 12)) { append(NOISE[random.nextInt(NOISE.length)]) } }
            prefixes.random(random) + body
        }
    }
}
