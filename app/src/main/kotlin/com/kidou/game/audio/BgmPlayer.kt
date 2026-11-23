package com.kidou.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.kidou.game.R

class BgmPlayer(context: Context) {
  private val appContext = context.applicationContext
  private var mediaPlayer: MediaPlayer? = null
  private var isMuted: Boolean = false
  private var isForeground: Boolean = false

  private fun ensureCreated() {
    if (mediaPlayer != null) return
    runCatching {
      val player = MediaPlayer.create(appContext, R.raw.loading_screen) ?: return
      player.setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_GAME)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build()
      )
      player.isLooping = true
      player.setVolume(0.45f, 0.45f)
      mediaPlayer = player
    }
  }

  fun onForeground(bgmEnabled: Boolean) {
    isForeground = true
    isMuted = !bgmEnabled
    if (!isMuted) {
      startIfReady()
    }
  }

  fun onBackground() {
    isForeground = false
    runCatching {
      if (mediaPlayer?.isPlaying == true) {
        mediaPlayer?.pause()
      }
    }
  }

  fun setEnabled(enabled: Boolean) {
    isMuted = !enabled
    if (isMuted) {
      runCatching {
        if (mediaPlayer?.isPlaying == true) {
          mediaPlayer?.pause()
        }
      }
    } else if (isForeground) {
      startIfReady()
    }
  }

  fun isPlaying(): Boolean = runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(false)

  private fun startIfReady() {
    ensureCreated()
    runCatching {
      val mp = mediaPlayer ?: return
      if (!mp.isPlaying) {
        mp.start()
      }
    }
  }

  fun release() {
    runCatching {
      mediaPlayer?.stop()
      mediaPlayer?.release()
    }
    mediaPlayer = null
  }
}
