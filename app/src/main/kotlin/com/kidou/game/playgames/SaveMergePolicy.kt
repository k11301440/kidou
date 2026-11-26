package com.kidou.game.playgames

import com.kidou.game.model.DictionaryEntry
import com.kidou.game.model.SaveDataV2

object SaveMergePolicy {
  /**
   * Resolves local vs cloud saved game snapshots without losing player progress:
   * - Cleared levels, discovered words, and unlocked achievements use set union.
   * - RTA best time keeps the faster non-null record; RTA clear count keeps max.
   * - Settings and dictionary entry conflicts favor whichever snapshot has more clears,
   *   breaking ties by newer updatedAtMs (local preferred on exact tie).
   */
  fun resolveConflict(local: SaveDataV2, cloud: SaveDataV2): SaveDataV2 {
    val preferCloud =
      when {
        cloud.clearedLevelKeys.size != local.clearedLevelKeys.size ->
          cloud.clearedLevelKeys.size > local.clearedLevelKeys.size
        else -> cloud.updatedAtMs > local.updatedAtMs
      }

    val primary = if (preferCloud) cloud else local
    val secondary = if (preferCloud) local else cloud

    val mergedDictionary = LinkedHashMap<String, DictionaryEntry>()
    val allWordKeys = local.dictionary.keys + cloud.dictionary.keys
    allWordKeys.forEach { key ->
      val p = primary.dictionary[key]
      val s = secondary.dictionary[key]
      when {
        p != null && s != null -> {
          val pHasText = p.translation.isNotBlank() || p.memo.isNotBlank()
          val sHasText = s.translation.isNotBlank() || s.memo.isNotBlank()
          mergedDictionary[key] = if (!pHasText && sHasText) s else p
        }
        p != null -> mergedDictionary[key] = p
        s != null -> mergedDictionary[key] = s
      }
    }

    val mergedBestTime = listOfNotNull(local.rtaBestTimeMs, cloud.rtaBestTimeMs).filter { it > 0L }.minOrNull()

    return primary.copy(
      dictionary = mergedDictionary,
      discoveredWordIds = local.discoveredWordIds + cloud.discoveredWordIds,
      clearedLevelKeys = local.clearedLevelKeys + cloud.clearedLevelKeys,
      operationLog = if (primary.operationLog.isNotEmpty()) primary.operationLog else secondary.operationLog,
      rtaBestTimeMs = mergedBestTime,
      rtaClearCount = maxOf(local.rtaClearCount, cloud.rtaClearCount),
      playGamesOptIn = local.playGamesOptIn || cloud.playGamesOptIn,
      unlockedAchievements = local.unlockedAchievements + cloud.unlockedAchievements,
      updatedAtMs = maxOf(local.updatedAtMs, cloud.updatedAtMs),
    )
  }
}
