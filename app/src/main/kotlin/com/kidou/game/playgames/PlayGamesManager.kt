package com.kidou.game.playgames

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.kidou.game.BuildConfig
import com.kidou.game.config.PlayGamesConfig
import com.kidou.game.data.SaveRepository
import com.kidou.game.model.LoadedGameData
import com.kidou.game.model.SaveDataV2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayGamesStatus(
  val isConfigured: Boolean = PlayGamesConfig.isAppIdConfigured(BuildConfig.PLAY_GAMES_APP_ID),
  val isAuthenticated: Boolean = false,
  val isBusy: Boolean = false,
  val statusMessageKey: String? = null,
)

class PlayGamesManager(
  context: Context,
  private val saveRepository: SaveRepository,
) {
  private val appContext = context.applicationContext
  private val _status = MutableStateFlow(PlayGamesStatus())
  val status: StateFlow<PlayGamesStatus> = _status.asStateFlow()

  fun checkAuthState(
    activity: Activity?,
    onResult: (Boolean) -> Unit = {},
  ) {
    if (activity == null) {
      onResult(false)
      return
    }
    runCatching {
      PlayGames.getGamesSignInClient(activity).isAuthenticated
        .addOnCompleteListener { task ->
          val authed = task.isSuccessful && task.result?.isAuthenticated == true
          _status.value = _status.value.copy(isAuthenticated = authed, isBusy = false)
          onResult(authed)
        }
    }.onFailure {
      _status.value = _status.value.copy(isAuthenticated = false, isBusy = false)
      onResult(false)
    }
  }

  fun requestSignIn(
    activity: Activity?,
    onComplete: (Boolean) -> Unit,
  ) {
    if (activity == null) {
      onComplete(false)
      return
    }
    _status.value = _status.value.copy(isBusy = true, statusMessageKey = null)
    runCatching {
      PlayGames.getGamesSignInClient(activity).signIn()
        .addOnCompleteListener { task ->
          val authed = task.isSuccessful && task.result?.isAuthenticated == true
          _status.value =
            _status.value.copy(
              isAuthenticated = authed,
              isBusy = false,
              statusMessageKey = if (authed) "connected" else "unavailable",
            )
          onComplete(authed)
        }
    }.onFailure {
      _status.value =
        _status.value.copy(
          isAuthenticated = false,
          isBusy = false,
          statusMessageKey = "unavailable",
        )
      onComplete(false)
    }
  }

  fun openAchievementsUi(
    activity: Activity?,
    onIntentReady: (Intent) -> Unit,
    onUnavailable: () -> Unit,
  ) {
    if (activity == null || !_status.value.isAuthenticated) {
      onUnavailable()
      return
    }
    runCatching {
      PlayGames.getAchievementsClient(activity).achievementsIntent
        .addOnSuccessListener { intent -> onIntentReady(intent) }
        .addOnFailureListener { onUnavailable() }
    }.onFailure {
      onUnavailable()
    }
  }

  /**
   * Evaluates state-derived achievements and unlocks pending ones if opted in and signed in.
   * Returns newly recorded achievement keys (persisted locally even when placeholder IDs are used).
   */
  fun syncAchievements(
    activity: Activity?,
    data: LoadedGameData,
    save: SaveDataV2,
    translationPercent: Int,
  ): Set<String> {
    if (!save.playGamesOptIn) return save.unlockedAchievements
    val snapshot = AchievementEvaluator.snapshotFrom(data, save, translationPercent)
    val pending = AchievementEvaluator.computePendingUnlocks(snapshot, save.unlockedAchievements)
    if (pending.isEmpty()) return save.unlockedAchievements

    val updatedUnlocked = save.unlockedAchievements + pending.map { it.key }
    if (activity != null && _status.value.isAuthenticated) {
      runCatching {
        val client = PlayGames.getAchievementsClient(activity)
        pending.forEach { ach ->
          val remoteId = PlayGamesConfig.configuredAchievementId(ach)
          if (remoteId != null) {
            client.unlock(remoteId)
          }
        }
      }
    }
    return updatedUnlocked
  }

  fun saveToCloud(
    activity: Activity?,
    localSave: SaveDataV2,
  ) {
    if (!localSave.playGamesOptIn || activity == null || !_status.value.isAuthenticated) return
    _status.value = _status.value.copy(isBusy = true)
    runCatching {
      val snapshotsClient = PlayGames.getSnapshotsClient(activity)
      val payload = saveRepository.serialize(localSave)
      snapshotsClient.open(
        PlayGamesConfig.SNAPSHOT_UNIQUE_NAME,
        true,
        SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED,
      ).addOnSuccessListener { dataOrConflict ->
        val snapshot = dataOrConflict.data
        if (snapshot != null) {
          snapshot.snapshotContents.writeBytes(payload)
          val metadataChange =
            SnapshotMetadataChange.Builder()
              .setDescription("KIDOU Cleared: ${localSave.clearedLevelKeys.size}")
              .build()
          snapshotsClient.commitAndClose(snapshot, metadataChange)
            .addOnCompleteListener {
              _status.value = _status.value.copy(isBusy = false)
            }
        } else {
          _status.value = _status.value.copy(isBusy = false)
        }
      }.addOnFailureListener {
        _status.value = _status.value.copy(isBusy = false)
      }
    }.onFailure {
      _status.value = _status.value.copy(isBusy = false)
    }
  }

  fun restoreFromCloud(
    activity: Activity?,
    localSave: SaveDataV2,
    onMerged: (SaveDataV2) -> Unit,
    onUnavailable: () -> Unit,
  ) {
    if (!localSave.playGamesOptIn || activity == null || !_status.value.isAuthenticated) {
      _status.value = _status.value.copy(statusMessageKey = "unavailable")
      onUnavailable()
      return
    }
    _status.value = _status.value.copy(isBusy = true, statusMessageKey = null)
    runCatching {
      val snapshotsClient = PlayGames.getSnapshotsClient(activity)
      snapshotsClient.open(
        PlayGamesConfig.SNAPSHOT_UNIQUE_NAME,
        false,
        SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED,
      ).addOnSuccessListener { dataOrConflict ->
        val snapshot = dataOrConflict.data
        if (snapshot == null) {
          _status.value = _status.value.copy(isBusy = false, statusMessageKey = "no_cloud_save")
          onUnavailable()
          return@addOnSuccessListener
        }
        val cloudBytes = runCatching { snapshot.snapshotContents.readFully() }.getOrNull()
        runCatching { snapshotsClient.discardAndClose(snapshot) }
        val cloudSave = cloudBytes?.let { saveRepository.deserialize(it) }
        if (cloudSave == null) {
          _status.value = _status.value.copy(isBusy = false, statusMessageKey = "no_cloud_save")
          onUnavailable()
          return@addOnSuccessListener
        }
        val merged = SaveMergePolicy.resolveConflict(localSave, cloudSave)
        _status.value = _status.value.copy(isBusy = false, statusMessageKey = "restored")
        onMerged(merged)
      }.addOnFailureListener {
        _status.value = _status.value.copy(isBusy = false, statusMessageKey = "unavailable")
        onUnavailable()
      }
    }.onFailure {
      _status.value = _status.value.copy(isBusy = false, statusMessageKey = "unavailable")
      onUnavailable()
    }
  }

  fun disconnect() {
    _status.value = _status.value.copy(isAuthenticated = false, isBusy = false, statusMessageKey = "disconnected")
  }
}
