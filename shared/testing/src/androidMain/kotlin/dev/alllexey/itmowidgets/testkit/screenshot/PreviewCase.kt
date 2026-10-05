package dev.alllexey.itmowidgets.testkit.screenshot

import dev.alllexey.itmowidgets.designsystem.preview.PreviewAppearance
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/** One capture: a preview in one appearance, its baseline file and its window. */
class PreviewCase(
    val baseName: String,
    val appearance: PreviewAppearance,
    val preview: ComposablePreview<AndroidPreviewInfo>,
    val size: CaptureSize,
) {
    /** The test name and the baseline file name without the extension. */
    val name: String get() = "${baseName}_${appearance.name}"

    val fileName: String get() = BaselineDirectory.fileName(baseName, appearance)

    val qualifiers: String get() = size.qualifiers(appearance)
}

/** The previews of one [PreviewScreenshotTest] subclass and the cases they make in this run. */
class PreviewSuite private constructor(testClass: Class<*>) {

    private val settings: PreviewScreenshots? = testClass.getAnnotation(PreviewScreenshots::class.java)

    val packageTree: String = settings?.packageTree?.ifEmpty { null } ?: testClass.packageName

    val accessibilityChecks: Boolean = settings?.accessibilityChecks ?: true

    val directory = BaselineDirectory(File(System.getProperty(OUTPUT_DIR_PROPERTY) ?: DEFAULT_OUTPUT_DIR))

    private val size = settings?.let { CaptureSize(it.widthDp, it.heightDp, it.density) } ?: CaptureSize()

    /** The previews by base name; the scan includes private previews, which is how the kit writes them. */
    val previews: Map<String, ComposablePreview<AndroidPreviewInfo>> = AndroidComposablePreviewScanner()
        .scanPackageTrees(packageTree)
        .includePrivatePreviews()
        .getPreviews()
        .groupBy(::baseName)
        .mapValues { (base, previews) ->
            check(previews.size == 1) { "Previews share the baseline name $base: give each @Preview a distinct name" }
            previews.single()
        }
        .toSortedMap()

    /** Every preview in its appearances ([BaselineDirectory.appearances]), in a fixed order. */
    val cases: List<PreviewCase> = previews.flatMap { (base, preview) ->
        val full = settings?.allAppearances == true || ShotsRun.fullMatrix
        directory.appearances(base, full).map { PreviewCase(base, it, preview, size.forPreview(preview.previewInfo)) }
    }

    fun case(name: String): PreviewCase = cases.singleOrNull { it.name == name }
        ?: error("No preview case $name in $packageTree; the previews changed during the run")

    companion object {
        private const val OUTPUT_DIR_PROPERTY = "roborazzi.output.dir"
        private const val DEFAULT_OUTPUT_DIR = "screenshots"
        private val STATE = Regex("[A-Za-z0-9-]+")

        private val suites = ConcurrentHashMap<Class<*>, PreviewSuite>()

        /** One scan per test class and class loader (the runner's and Robolectric's sandbox each scan once). */
        fun of(testClass: Class<*>): PreviewSuite = suites.getOrPut(testClass) { PreviewSuite(testClass) }

        /**
         * `<function>` or `<function>_<state>`, the state being `@Preview(name)`; a parameter provider's previews add
         * their index.
         */
        fun baseName(preview: ComposablePreview<AndroidPreviewInfo>): String {
            val state = preview.previewInfo.name.trim()
            check(state.isEmpty() || STATE.matches(state)) {
                "@Preview(name = \"$state\") on ${preview.methodName}: use letters, digits and '-' only"
            }
            return listOfNotNull(preview.methodName, state.ifEmpty { null }, preview.previewIndex?.toString())
                .joinToString("_")
        }

        private fun CaptureSize.forPreview(info: AndroidPreviewInfo) = copy(
            widthDp = info.widthDp.takeIf { it > 0 } ?: widthDp,
            heightDp = info.heightDp.takeIf { it > 0 } ?: heightDp,
        )
    }
}
