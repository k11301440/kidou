package com.kidou.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.kidou.game.model.SoundEffect
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

class SoundSynth {
  private val executor = Executors.newSingleThreadExecutor()
  private val sampleRate = 22050

  fun play(effect: SoundEffect, enabled: Boolean) {
    if (!enabled) return
    executor.execute {
      runCatching {
        val samples = synthesize(effect)
        val bufferSize = samples.size * 2
        val track =
          AudioTrack.Builder()
            .setAudioAttributes(
              AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            )
            .setAudioFormat(
              AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            )
            .setBufferSizeInBytes(bufferSize.coerceAtLeast(1024))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size)
        track.play()
        val durationMs = (samples.size * 1000L) / sampleRate + 30L
        Thread.sleep(durationMs)
        track.stop()
        track.release()
      }
    }
  }

  private fun synthesize(effect: SoundEffect): ShortArray {
    return when (effect) {
      SoundEffect.CLICK -> toneSequence(listOf(Tone(680.0, 45, 0.25, square = true)))
      SoundEffect.CLUNK ->
        toneSequence(
          listOf(
            Tone(190.0, 50, 0.35, square = true),
            Tone(130.0, 60, 0.30, square = true),
          )
        )
      SoundEffect.RATCHET ->
        toneSequence(
          listOf(
            Tone(460.0, 28, 0.25, square = true),
            Tone(620.0, 28, 0.20, square = true),
          )
        )
      SoundEffect.CHIME ->
        toneSequence(
          listOf(
            Tone(523.25, 70, 0.25, square = false),
            Tone(783.99, 120, 0.25, square = false),
          )
        )
      SoundEffect.WARN ->
        toneSequence(
          listOf(
            Tone(220.0, 90, 0.35, square = true),
            Tone(0.0, 30, 0.0, square = false),
            Tone(175.0, 140, 0.35, square = true),
          )
        )
      SoundEffect.PHASE_CLEAR ->
        toneSequence(
          listOf(
            Tone(440.0, 80, 0.25, square = false),
            Tone(554.37, 80, 0.25, square = false),
            Tone(659.25, 160, 0.28, square = false),
          )
        )
      SoundEffect.CORE_ACTIVATE ->
        toneSequence(
          listOf(
            Tone(392.00, 90, 0.25, square = false),
            Tone(493.88, 90, 0.25, square = false),
            Tone(587.33, 90, 0.25, square = false),
            Tone(783.99, 260, 0.30, square = false),
          )
        )
    }
  }

  private data class Tone(
    val freqHz: Double,
    val durationMs: Int,
    val volume: Double,
    val square: Boolean,
  )

  private fun toneSequence(tones: List<Tone>): ShortArray {
    val totalSamples = tones.sumOf { (sampleRate * it.durationMs) / 1000 }
    val out = ShortArray(totalSamples)
    var offset = 0
    for (tone in tones) {
      val count = (sampleRate * tone.durationMs) / 1000
      for (i in 0 until count) {
        val env =
          when {
            i < count / 10 -> i.toDouble() / (count / 10.0).coerceAtLeast(1.0)
            i > count * 8 / 10 -> (count - i).toDouble() / (count * 2 / 10.0).coerceAtLeast(1.0)
            else -> 1.0
          }
        val wave =
          if (tone.freqHz <= 0.0) {
            0.0
          } else {
            val s = sin(2.0 * PI * tone.freqHz * i / sampleRate)
            if (tone.square) {
              if (s >= 0) 0.7 else -0.7
            } else {
              s
            }
          }
        val sample = (wave * tone.volume * env * Short.MAX_VALUE).toInt()
        out[offset + i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
      }
      offset += count
    }
    return out
  }
}
