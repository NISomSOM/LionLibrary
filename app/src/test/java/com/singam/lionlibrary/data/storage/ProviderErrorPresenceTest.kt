package com.singam.lionlibrary.data.storage

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * Break vectors: the real external-storage message for a deleted file (including brackets and
 * Unicode in names), unrelated argument errors, a message that only mentions "missing", and no
 * message at all.
 */
class ProviderErrorPresenceTest {
    @Test
    fun `a wrapped not-found from the storage provider means the file is missing`() {
        val message =
            "Failed to determine if primary:Movies/Shows/[DKB] Show/[Judas] Show - S06E05 ジョジョ.mkv" +
                " is child of primary:Movies/Shows: java.io.FileNotFoundException: Missing file for" +
                " primary:Movies/Shows/[DKB] Show/[Judas] Show - S06E05 ジョジョ.mkv"
        assertThat(presenceForProviderError(message)).isEqualTo(DocumentPresence.MISSING)
    }

    @Test
    fun `other argument errors keep the row`() {
        assertThat(presenceForProviderError("Invalid URI: content://x"))
            .isEqualTo(DocumentPresence.UNAVAILABLE)
        assertThat(presenceForProviderError("Missing file")).isEqualTo(DocumentPresence.UNAVAILABLE)
        assertThat(presenceForProviderError(null)).isEqualTo(DocumentPresence.UNAVAILABLE)
    }
}
