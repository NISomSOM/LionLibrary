package com.singam.lionlibrary.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * How the last scan ended. [name] is the value stored in the `scan_summary` preference, identical
 * to the strings written before this enum existed; do not rename constants.
 */
enum class ScanOutcome {
    COMPLETE,
    API_KEY_MISSING,
    INVALID_API_KEY,
    NO_INTERNET,
    PERMISSION_REVOKED,
    CANCELLED,
    ERROR,
    /** The process ended while a scan was running (converted on the next start). */
    INTERRUPTED;

    companion object {
        /** Terminal scanner statuses; null for in-progress ones. */
        fun of(status: ScanStatus): ScanOutcome? =
            when (status) {
                ScanStatus.COMPLETE -> COMPLETE
                ScanStatus.API_KEY_MISSING -> API_KEY_MISSING
                ScanStatus.INVALID_API_KEY -> INVALID_API_KEY
                ScanStatus.NO_INTERNET -> NO_INTERNET
                ScanStatus.PERMISSION_REVOKED -> PERMISSION_REVOKED
                ScanStatus.SCANNING,
                ScanStatus.MATCHED,
                ScanStatus.UNIDENTIFIED,
                ScanStatus.SKIPPED,
                ScanStatus.ERROR -> null
            }
    }
}

/** Stores the outcome as its plain name and "no outcome yet" as "", exactly as before. */
internal object StoredScanOutcomeSerializer : KSerializer<ScanOutcome?> {
    override val descriptor = PrimitiveSerialDescriptor("ScanOutcome", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ScanOutcome?) =
        encoder.encodeString(value?.name.orEmpty())

    // Unknown names (a future or damaged value) read as "no outcome" instead of failing the
    // summary.
    override fun deserialize(decoder: Decoder): ScanOutcome? =
        decoder.decodeString().let { stored -> ScanOutcome.entries.find { it.name == stored } }
}

@Serializable
data class ScanSummary(
    val matched: Int = 0,
    val unidentified: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val finishedAt: Long = 0,
    @Serializable(with = StoredScanOutcomeSerializer::class) val outcome: ScanOutcome? = null,
    val wasRunning: Boolean = false,
)
