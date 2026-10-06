package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

/** Synthetic sheets under `src/androidHostTest/resources/sheets`: made-up names and ISUs, no real cell. */
object SheetFixtures {
    fun text(name: String): String {
        val stream = checkNotNull(SheetFixtures::class.java.classLoader?.getResourceAsStream("sheets/$name")) {
            "No fixture sheets/$name"
        }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun csv(name: String): SheetGrid = CsvGrid.parse(text(name))
}
