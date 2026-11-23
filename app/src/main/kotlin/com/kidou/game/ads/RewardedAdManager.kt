package com.kidou.game.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.kidou.game.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AdLoadState {
  IDLE,
  LOADING,
  READY,
  FAILED,
}

class RewardedAdManager {
  private val _adState = MutableStateFlow(AdLoadState.IDLE)
  val adState: StateFlow<AdLoadState> = _adState.asStateFlow()

  private var rewardedAd: RewardedAd? = null
  private var initialized = false

  fun initialize(context: Context) {
    if (initialized) return
    initialized = true
    val appCtx = context.applicationContext
    runCatching {
      MobileAds.initialize(appCtx) {
        preload(appCtx)
      }
    }.onFailure { err ->
      Log.w("KIDOU_ADS", "MobileAds init failed: ${err.message}")
      _adState.value = AdLoadState.FAILED
    }
  }

  fun preload(context: Context) {
    if (_adState.value == AdLoadState.LOADING || rewardedAd != null) return
    _adState.value = AdLoadState.LOADING
    val appCtx = context.applicationContext
    val request = AdRequest.Builder().build()

    RewardedAd.load(
      appCtx,
      BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
      request,
      object : RewardedAdLoadCallback() {
        override fun onAdLoaded(ad: RewardedAd) {
          rewardedAd = ad
          _adState.value = AdLoadState.READY
          Log.i("KIDOU_ADS", "Rewarded ad loaded")
        }

        override fun onAdFailedToLoad(error: LoadAdError) {
          rewardedAd = null
          _adState.value = AdLoadState.FAILED
          Log.w("KIDOU_ADS", "Rewarded ad failed to load: ${error.code} ${error.message}")
        }
      },
    )
  }

  fun showAd(
    activity: Activity,
    onRewardEarned: () -> Unit,
    onFailedOrClosed: (rewarded: Boolean) -> Unit,
  ) {
    val ad = rewardedAd
    if (ad == null) {
      preload(activity.applicationContext)
      onFailedOrClosed(false)
      return
    }

    var earned = false
    rewardedAd = null
    _adState.value = AdLoadState.IDLE

    ad.fullScreenContentCallback =
      object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
          preload(activity.applicationContext)
          onFailedOrClosed(earned)
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
          Log.w("KIDOU_ADS", "Rewarded ad failed to show: ${error.message}")
          preload(activity.applicationContext)
          onFailedOrClosed(false)
        }
      }

    ad.show(activity) {
      earned = true
      onRewardEarned()
    }
  }
}
