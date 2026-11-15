package com.singam.lionlibrary.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.singam.lionlibrary.domain.model.ScanOutcome
import com.singam.lionlibrary.domain.model.ScanSummary
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class ScanSummarySerializationTest {
    // Break vectors: summaries stored before the enum (plain strings and "" while running), an
    // unknown/damaged name, lower-case look-alikes, and every constant round-tripping by name.

    @ParameterizedTest
    @EnumSource(ScanOutcome::class)
    fun `every outcome is stored under its exact legacy name`(outcome: ScanOutcome) {
        val encoded = Json.encodeToString(ScanSummary.serializer(), ScanSummary(outcome = outcome))

        assertThat(encoded.contains("\"outcome\":\"${outcome.name}\"")).isEqualTo(true)
        assertThat(Json.decodeFromString(ScanSummary.serializer(), encoded).outcome)
            .isEqualTo(outcome)
    }

    @Test
    fun `summary written by the previous version decodes unchanged`() {
        val legacy =
            """{"matched":3,"unidentified":1,"skipped":2,"failed":0,"finishedAt":99,"outcome":"PERMISSION_REVOKED","wasRunning":false}"""

        val summary = Json.decodeFromString(ScanSummary.serializer(), legacy)

        assertThat(summary)
            .isEqualTo(ScanSummary(3, 1, 2, 0, 99, ScanOutcome.PERMISSION_REVOKED, false))
    }

    @Test
    fun `running marker with empty outcome stays empty both ways`() {
        val running = """{"outcome":"","wasRunning":true}"""

        val summary = Json.decodeFromString(ScanSummary.serializer(), running)

        assertThat(summary.outcome).isNull()
        assertThat(Json.encodeToString(ScanSummary.serializer(), summary).contains("\"outcome\""))
            .isEqualTo(false) // default value omitted, decodes back to null
    }

    @Test
    fun `unknown or differently cased names read as no outcome`() {
        for (stored in listOf("FINISHED", "complete", " COMPLETE")) assertThat(
                Json.decodeFromString(ScanSummary.serializer(), """{"outcome":"$stored"}""").outcome
            )
            .isNull()
    }
}
