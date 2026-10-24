package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.singam.lionlibrary.presentation.common.UiText
import com.singam.lionlibrary.presentation.player.PlayerPanel
import com.singam.lionlibrary.presentation.player.PlayerState
import com.singam.lionlibrary.presentation.player.engine.EnginePlaybackState
import org.junit.jupiter.api.Test

/**
 * Subtitles and the skip button hide exactly while the paused card shows. Break vectors: each
 * single condition that must keep the card away (controls up, playing, locked, buffering, a panel,
 * a failure, an unknown duration) while everything else says "show".
 */
class PlayerPauseCardTest {
    private val paused =
        PlayerState(
            showControls = false,
            isPlaying = false,
            playbackState = EnginePlaybackState.READY,
            durationMs = 1_000,
        )

    @Test
    fun `a paused, ready file with hidden controls shows the card`() {
        assertThat(paused.isPauseCardVisible).isTrue()
    }

    @Test
    fun `any one blocking condition keeps the card hidden`() {
        listOf(
                paused.copy(showControls = true),
                paused.copy(isPlaying = true),
                paused.copy(isLocked = true),
                paused.copy(playbackState = EnginePlaybackState.BUFFERING),
                paused.copy(playbackState = EnginePlaybackState.ENDED),
                paused.copy(panel = PlayerPanel.SUBTITLES),
                paused.copy(failure = UiText.Dynamic("x")),
                paused.copy(durationMs = 0),
            )
            .forEach { assertThat(it.isPauseCardVisible).isFalse() }
    }
}
