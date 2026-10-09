package dev.alllexey.itmowidgets.feature.qr.widget

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.SnapshotFile
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * What the QR widget draws, `qr-pass-v1.json` in the App Group container (ADR 0027): the widget extension links no
 * Kotlin, so it gets the modules of the shared [QrCodeGenerator] (version 1, ECC LOW), the very code the pass
 * screen and Android's widget draw, and never encodes a QR code itself.
 */
@Serializable
data class QrPassSnapshot(
    /** When this snapshot was written, on the wall clock. */
    @SerialName("generatedAt") @Serializable(with = IsoInstantSerializer::class) val generatedAt: Instant,
    /** The local validity deadline of the pass; past it the widget shows no code. */
    @SerialName("expiresAt") @Serializable(with = IsoInstantSerializer::class) val expiresAt: Instant,
    /** The demo session's pass: a code no turnstile accepts. */
    @SerialName("demo") val demo: Boolean,
    /** One string per row from the top, one character per module from the left: `1` dark, `0` light. */
    @SerialName("matrix") val matrix: List<String>,
    /** `settings_qr_spoiler_title`: the widget shows the code only after a tap. */
    @SerialName("spoiler") val spoiler: Boolean,
    /** `settings_qr_dynamic_colors_title`: the widget draws the code and the spoiler in the app's scheme. */
    @SerialName("dynamicColors") val dynamicColors: Boolean,
    /** `settings_qr_animation_title`, a [QrAnimationType] name (a stable identifier). */
    @SerialName("animation") val animation: String,
)

/**
 * The QR widget options of the settings page. Android keeps them global, so one value serves every placed widget;
 * the widget extension reads them from the pass snapshot.
 */
data class QrWidgetAppearance(
    val spoiler: Boolean,
    val dynamicColors: Boolean,
    val animation: QrAnimationType,
) {
    companion object {
        /** The options while nothing is stored: Android's defaults. */
        val Default = QrWidgetAppearance(spoiler = true, dynamicColors = true, animation = QrAnimationType.CIRCLE)
    }
}

/**
 * Keeps [FILE] on the pass that [QrCodeRepository] holds and on the QR widget options of [settings]: every new valid
 * pass and every changed option is written with the pass's expiry and the QR widget kind is reloaded, so a changed
 * switch reaches placed widgets at once. Nothing is written while there is no valid pass; on sign-out the App Group
 * cleaner removes the file and the cleared cache emits nothing new, so the file stays gone until the next pass.
 * Times come from the wall [clock].
 */
class QrPassSnapshotWriter(
    private val repository: QrCodeRepository,
    private val settings: QrSettingsPreferences,
    private val writer: AppGroupSnapshotWriter,
    private val demo: DemoMode,
    private val clock: Clock,
    private val log: AppLog,
) {
    private val generator = QrCodeGenerator()

    /** Follows the repository's pass and the widget options in [scope] until the scope ends. */
    fun launchIn(scope: CoroutineScope): Job = combine(repository.observeQrHex(), appearance(), ::Pair)
        .onEach { (hex, appearance) -> publish(hex, appearance) }
        .launchIn(scope)

    /** Writes the snapshot of [hex] with [appearance] when [hex] is still the repository's valid pass. */
    suspend fun publish(hex: String, appearance: QrWidgetAppearance) {
        val pass = repository.currentQr()?.takeIf { it.hex == hex } ?: return
        val snapshot = try {
            QrPassSnapshot(
                generatedAt = clock.now(),
                expiresAt = Instant.fromEpochMilliseconds(pass.expiresAtMillis),
                demo = demo.isActive(),
                matrix = matrixOf(pass.hex),
                spoiler = appearance.spoiler,
                dynamicColors = appearance.dynamicColors,
                animation = appearance.animation.name,
            )
        } catch (error: IllegalArgumentException) {
            // A pass longer than a version 1 code holds: the screen shows its error state, the widget keeps nothing.
            log.warn(TAG, "The pass does not fit a version 1 code", error)
            return
        }
        try {
            writer.write(FILE, snapshot, listOf(QR_WIDGET_KIND))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            log.warn(TAG, "Could not write the QR pass snapshot", error)
        }
    }

    private fun appearance(): Flow<QrWidgetAppearance> = combine(
        settings.observeQrSpoilerEnabled(),
        settings.observeQrDynamicColorsEnabled(),
        settings.observeQrSpoilerAnimationType(),
        ::QrWidgetAppearance,
    ).distinctUntilChanged()

    private fun matrixOf(hex: String): List<String> {
        val code = generator.generate(hex)
        return List(code.size) { y ->
            buildString(code.size) {
                repeat(code.size) { x -> append(if (code.getModule(x, y)) DARK else LIGHT) }
            }
        }
    }

    companion object {
        val FILE = SnapshotFile(name = "qr-pass", version = 1, serializer = QrPassSnapshot.serializer())

        /** The QR widget's WidgetKit kind (ADR 0027), a stable identifier. */
        const val QR_WIDGET_KIND = "dev.alllexey.itmowidgets.widget.qr"

        private const val DARK = '1'
        private const val LIGHT = '0'
        private const val TAG = "QrPassSnapshot"
    }
}

/** `Instant.toString()` and back: ISO 8601 in UTC, as `schedule-timeline-v1.json` writes its times. */
internal object IsoInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.alllexey.itmowidgets.feature.qr.widget.IsoInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
