package dev.alllexey.itmowidgets.feature.qr.data.demo

/**
 * The pass of the demo session: a code no turnstile accepts. It must fit the version 1 code the pass screen draws
 * (17 bytes at most), like the real My ITMO pass.
 */
object DemoQr {
    val HEX: String = "DEMO" + "0".repeat(12)
}
