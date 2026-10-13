package com.rhulhiking.attendance.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The app's own tap sounds and haptics (platform NFC sounds are disabled in reader mode). */
@Singleton
class FeedbackPlayer @Inject constructor(@ApplicationContext context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }

    private val tones: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()
    }

    fun success() = play(ToneGenerator.TONE_PROP_ACK, 160, longArrayOf(0, 40, 70, 40))
    fun warning() = play(ToneGenerator.TONE_PROP_BEEP2, 220, longArrayOf(0, 140))
    fun error() = play(ToneGenerator.TONE_PROP_NACK, 320, longArrayOf(0, 350))
    fun queued() = play(ToneGenerator.TONE_PROP_BEEP, 120, longArrayOf(0, 60))

    private fun play(tone: Int, durationMs: Int, pattern: LongArray) {
        runCatching { tones?.startTone(tone, durationMs) }
        runCatching {
            if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }
}
