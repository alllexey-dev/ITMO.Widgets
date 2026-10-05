package dev.alllexey.itmowidgets.testkit.screenshot

import dev.alllexey.itmowidgets.designsystem.preview.PreviewAppearance
import java.io.File

/**
 * A module's baselines, `<module>/screenshots/`: one `<base>_<appearance>.png` per capture, where `<base>` is
 * `<Preview>` or `<Preview>_<state>` ([PreviewCase.baseName]), and [REFERENCES], the bases `:app` recorded from XML
 * screens under the name of a preview that does not exist yet (`XmlReferenceCapture`).
 */
class BaselineDirectory(val dir: File) {

    fun file(base: String, appearance: PreviewAppearance): File = File(dir, fileName(base, appearance))

    /**
     * The appearances [base] is captured in: light and dark, all four when [full], and every appearance it already
     * has a baseline in, so a recorded baseline is never skipped by a narrower run.
     */
    fun appearances(base: String, full: Boolean): List<PreviewAppearance> =
        PreviewAppearance.All.filter { full || it in PreviewAppearance.Default || file(base, it).isFile }

    /** Every PNG here with its base and appearance; both null for a name outside the scheme. */
    fun baselines(): List<Baseline> = dir.listFiles { file -> file.isFile && file.extension == PNG }
        .orEmpty()
        .map { parse(it.name) }
        .sortedBy { it.fileName }

    /** The bases [REFERENCES] lists. */
    fun references(): Set<String> = referencesFile.takeIf { it.isFile }
        ?.readLines()
        ?.map(String::trim)
        ?.filter { it.isNotEmpty() && !it.startsWith("#") }
        ?.toSet()
        .orEmpty()

    /** Lists [base] in [REFERENCES]; the file stays sorted, so parallel reference cards merge line by line. */
    fun addReference(base: String) {
        val bases = references() + base
        dir.mkdirs()
        referencesFile.writeText(REFERENCES_HEADER + bases.sorted().joinToString("\n", postfix = "\n"))
    }

    private val referencesFile: File get() = File(dir, REFERENCES)

    data class Baseline(val fileName: String, val base: String?, val appearance: PreviewAppearance?)

    companion object {
        /** The index of XML references pending their port, one base per line. */
        const val REFERENCES = "references.txt"

        private const val PNG = "png"

        private const val REFERENCES_HEADER =
            "# XML references recorded by :app (XmlReferenceCapture) under the name of a future preview.\n" +
                "# The port that adds the preview re-records the files and deletes its line and the reference test.\n"

        fun fileName(base: String, appearance: PreviewAppearance): String = "${base}_${appearance.name}.$PNG"

        /** `shared/<module>/screenshots` under the repository [root]. */
        fun ofModule(root: File, module: String): BaselineDirectory =
            BaselineDirectory(File(root, "shared/$module/screenshots"))

        fun parse(fileName: String): Baseline {
            val appearance = PreviewAppearance.All
                .sortedByDescending { it.name.length }
                .firstOrNull { fileName.endsWith("_${it.name}.$PNG") }
            val base = appearance?.let { fileName.removeSuffix("_${it.name}.$PNG") }?.takeIf(String::isNotEmpty)
            return Baseline(fileName, base, appearance.takeIf { base != null })
        }
    }
}
