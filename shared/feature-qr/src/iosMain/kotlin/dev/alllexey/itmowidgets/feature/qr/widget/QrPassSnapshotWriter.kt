package dev.alllexey.itmowidgets.feature.qr.widget

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.storage.SnapshotFile
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
)

/**
 * Keeps [FILE] on the pass that [QrCodeRepository] holds: every new valid pass is written with its expiry and the QR
 * widget kind is reloaded. Nothing is written while there is no valid pass; on sign-out the App Group cleaner removes
 * the file and the cleared cache emits nothing new, so the file stays gone. Times come from the wall [clock].
 */
class QrPassSnapshotWriter(
    private val repository: QrCodeRepository,
    private val writer: AppGroupSnapshotWriter,
    private val demo: DemoMode,
    private val clock: Clock,
    private val log: AppLog,
) {
    private val generator = QrCodeGenerator()

    /** Follows the repository's pass in [scope] until the scope ends. */
    fun launchIn(scope: CoroutineScope): Job = repository.observeQrHex()
        .onEach(::publish)
        .launchIn(scope)

    /** Writes the snapshot of [hex] when it is still the repository's valid pass. */
    suspend fun publish(hex: String) {
        val pass = repository.currentQr()?.takeIf { it.hex == hex } ?: return
        val snapshot = try {
            QrPassSnapshot(
                generatedAt = clock.now(),
                expiresAt = Instant.fromEpochMilliseconds(pass.expiresAtMillis),
                demo = demo.isActive(),
                matrix = matrixOf(pass.hex),
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
