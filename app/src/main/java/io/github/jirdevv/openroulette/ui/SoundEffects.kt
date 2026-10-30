// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.ui

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Asset-free sound effects using the platform ToneGenerator (no binary blobs, no extra libs).
 * To ship real samples, bundle CC0 audio in res/raw and swap this class for a SoundPool.
 */
class SoundEffects {
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }.getOrNull()

    fun play(event: FeedbackEvent) {
        val t = tone ?: return
        when (event) {
            FeedbackEvent.CHIP -> t.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
            FeedbackEvent.SPIN -> t.startTone(ToneGenerator.TONE_PROP_ACK, 150)
            FeedbackEvent.WIN -> t.startTone(ToneGenerator.TONE_PROP_PROMPT, 300)
            FeedbackEvent.LOSE -> t.startTone(ToneGenerator.TONE_PROP_NACK, 200)
        }
    }

    fun release() { tone?.release() }
}
