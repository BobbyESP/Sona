/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.components

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/**
 * The tone a dialpad key makes under the finger, heard on this phone only: what the other side
 * hears of a key in a call is sent by the engine, not played here.
 */
internal class DialpadTones {
    private val generator: ToneGenerator? =
        try {
            ToneGenerator(AudioManager.STREAM_DTMF, VOLUME)
        } catch (_: RuntimeException) {
            // The system may refuse a generator (no audio, too many open): keys are then silent.
            null
        }

    fun play(key: Char) {
        val tone = toneOf(key) ?: return
        generator?.startTone(tone, DURATION_MS)
    }

    fun release() {
        generator?.release()
    }

    private companion object {
        const val VOLUME = 80
        const val DURATION_MS = 120

        fun toneOf(key: Char): Int? =
            when (key) {
                '0' -> ToneGenerator.TONE_DTMF_0
                '1' -> ToneGenerator.TONE_DTMF_1
                '2' -> ToneGenerator.TONE_DTMF_2
                '3' -> ToneGenerator.TONE_DTMF_3
                '4' -> ToneGenerator.TONE_DTMF_4
                '5' -> ToneGenerator.TONE_DTMF_5
                '6' -> ToneGenerator.TONE_DTMF_6
                '7' -> ToneGenerator.TONE_DTMF_7
                '8' -> ToneGenerator.TONE_DTMF_8
                '9' -> ToneGenerator.TONE_DTMF_9
                '*' -> ToneGenerator.TONE_DTMF_S
                '#' -> ToneGenerator.TONE_DTMF_P
                else -> null
            }
    }
}

/** A [DialpadTones] for as long as the caller is composed; `null` when tones are off. */
@Composable
internal fun rememberDialpadTones(enabled: Boolean): DialpadTones? {
    if (!enabled) return null
    val tones = remember { DialpadTones() }
    DisposableEffect(tones) { onDispose { tones.release() } }
    return tones
}
