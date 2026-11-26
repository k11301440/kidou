package com.kidou.game

import android.app.Application
import com.google.android.gms.games.PlayGamesSdk

class KidouApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    // Safe initialization: emulators or missing Play Console project ID won't crash app startup.
    runCatching { PlayGamesSdk.initialize(this) }
  }
}
