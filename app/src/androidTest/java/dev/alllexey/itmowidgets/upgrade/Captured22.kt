package dev.alllexey.itmowidgets.upgrade

import java.time.Instant

/** Values `capture/UpgradeFixtureCapture22.kt.txt` wrote at tag `v2.2`; all synthetic. */
object Captured22 {
    val AT: Instant = Instant.parse("2026-10-04T09:00:00Z")
    val AT_MS: Long = AT.toEpochMilli()
    const val DAY_MS = 86_400_000L

    /** Capture time plus the 100-year token lifetimes. */
    const val TOKEN_EXPIRES_AT = 4_944_704_400_000L
    const val ISU = 123456
    const val SUBJECT = "Тестовая дисциплина"
    const val SUBJECT_ID = 2001L
    const val HALF = "2026/2027-1"
    const val PERIOD = "2026-1"
    const val LINK_ID = "00000000-0000-4000-8000-000000000022"
    const val QR_HEX = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"
    const val BARS_HEADER = "Bearer upgrade22-bars-session"

    /** `{"alg":"none"}.{"isu":123456,"name":"Тестовый пользователь"}.signature`, Base64url without padding. */
    const val ID_TOKEN = "eyJhbGciOiJub25lIn0.eyJpc3UiOjEyMzQ1NiwibmFtZSI6ItCi0LXRgdGC0L7QstGL0Lkg0L_QvtC70YzQt9C-0LLQsNGC0LXQu9GMIn0.signature"
}
