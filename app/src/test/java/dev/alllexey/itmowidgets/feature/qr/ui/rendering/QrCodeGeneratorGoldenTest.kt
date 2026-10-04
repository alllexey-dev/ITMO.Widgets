package dev.alllexey.itmowidgets.feature.qr.ui.rendering

import dev.alllexey.itmowidgets.feature.qr.data.demo.DemoQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The goldens under `qr/` were recorded with io.nayuki:qrcodegen 1.8.0 (`encodeSegments` of one byte segment, ECC L,
 * version 1..1, automatic mask, no boost) read through the old `toBooleans`. The pass screen, widget reveal frames,
 * tiles and reference captures draw these modules, so the generator must reproduce them exactly.
 */
class QrCodeGeneratorGoldenTest {

    private val generator = QrCodeGenerator()

    @Test
    fun `every golden matrix is reproduced module for module`() {
        GOLDENS.forEach { name ->
            val golden = Golden.load(name)

            val qrCode = generator.generate(golden.input)

            assertEquals("$name version", golden.version, qrCode.version)
            assertEquals("$name mask", golden.mask, qrCode.mask)
            assertEquals("$name modules", golden.rows, render(generator.toBooleans(qrCode)))
        }
    }

    @Test
    fun `the demo golden is the demo pass`() {
        assertEquals(DemoQr.HEX, Golden.load("demo-hex").input)
    }

    @Test
    fun `the goldens cover every mask`() {
        assertEquals((0..7).toSet(), GOLDENS.map { Golden.load(it).mask }.toSet())
    }

    @Test
    fun `seventeen bytes fit and eighteen are refused`() {
        assertEquals(1, generator.generate("0".repeat(QrCode.MAX_BYTES)).version)

        assertThrows(IllegalArgumentException::class.java) {
            generator.generate("0".repeat(QrCode.MAX_BYTES + 1))
        }
    }

    private fun render(matrix: List<List<Boolean>>): List<String> =
        matrix.map { line -> line.joinToString("") { if (it) "#" else "." } }

    private data class Golden(val input: String, val version: Int, val mask: Int, val rows: List<String>) {
        companion object {
            fun load(name: String): Golden {
                val stream = checkNotNull(Golden::class.java.classLoader?.getResourceAsStream("qr/$name.txt")) {
                    "Missing golden qr/$name.txt"
                }
                val lines = stream.bufferedReader().use { it.readLines() }
                val headers = lines.filter { it.startsWith("# ") }
                    .associate { it.removePrefix("# ").substringBefore(": ") to it.substringAfter(": ") }
                return Golden(
                    input = headers.getValue("input"),
                    version = headers.getValue("version").toInt(),
                    mask = headers.getValue("mask").toInt(),
                    rows = lines.filterNot { it.startsWith("# ") },
                )
            }
        }
    }

    private companion object {
        val GOLDENS = listOf(
            "demo-hex",
            "widget-preview",
            "itmo-test",
            "longest-hex",
        ) + (0..7).map { "pass-mask-$it" }
    }
}
