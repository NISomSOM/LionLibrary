package com.singam.lionlibrary.data.local.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PreferencesReadErrorTest {
    // Break vectors: read fails before any value (corrupt file), read fails after a good value,
    // a non-I/O failure (programming error) must not be hidden.
    private val key = booleanPreferencesKey("flag")

    @Test
    fun `unreadable file emits empty preferences so readers see defaults`() = runTest {
        val broken = flow<Preferences> { throw IOException("corrupt") }

        val value = broken.orDefaultsOnReadError().first()

        assertThat(value[key]).isNull()
    }

    @Test
    fun `failure after a good value keeps that value and then falls back once`() = runTest {
        val source = flow {
            emit(preferencesOf(key to true))
            throw IOException("disk")
        }

        val values = source.orDefaultsOnReadError().toList().map { it[key] }

        assertThat(values).isEqualTo(listOf(true, null))
    }

    @Test
    fun `non io failures still propagate`() = runTest {
        val broken = flow<Preferences> { throw IllegalStateException("bug") }

        assertFailure { broken.orDefaultsOnReadError().first() }
            .isInstanceOf(IllegalStateException::class)
    }
}
