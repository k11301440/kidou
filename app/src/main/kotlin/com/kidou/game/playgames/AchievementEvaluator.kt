package com.kidou.game.playgames

import com.kidou.game.engine.LevelEngine
import com.kidou.game.model.LoadedGameData
import com.kidou.game.model.SaveDataV2

enum class KidouAchievement(val key: String) {
  FIRST_ACTIVATION("first_activation"),
  BOARD_1_COMPLETE("board_1_complete"),
  BOARD_2_COMPLETE("board_2_complete"),
  BOARD_3_COMPLETE("board_3_complete"),
  BOARD_4_COMPLETE("board_4_complete"),
  BOARD_5_COMPLETE("board_5_complete"),
  ALL_125_COMPLETE("all_125_complete"),
  LEXICON_50_PERCENT("lexicon_50_percent"),
  LEXICON_100_PERCENT("lexicon_100_percent"),
  RTA_FIRST_CLEAR("rta_first_clear"),
  RTA_UNDER_3_MIN("rta_under_3_min"),
  RTA_UNDER_1_MIN("rta_under_1_min"),
}

data class AchievementProgressSnapshot(
  val totalClears: Int,
  val clearsByBoard: Map<Int, Int>,
  val boardTotals: Map<Int, Int>,
  val translationPercent: Int,
  val rtaClearCount: Int,
  val rtaBestTimeMs: Long?,
)

object AchievementEvaluator {
  const val TOTAL_PLAYABLE_TARGET = 125
  const val THREE_MINUTES_MS = 180_000L
  const val ONE_MINUTE_MS = 60_000L

  fun snapshotFrom(
    data: LoadedGameData,
    save: SaveDataV2,
    translationPercent: Int,
  ): AchievementProgressSnapshot {
    val clearsByBoard = mutableMapOf<Int, Int>()
    val boardTotals = mutableMapOf<Int, Int>()
    var totalClears = 0
    data.boardCollection.boards.forEach { board ->
      val clearedCount = LevelEngine.countBoardClears(board.id, save.clearedLevelKeys)
      clearsByBoard[board.id] = clearedCount
      boardTotals[board.id] = board.totalSlots
      totalClears += clearedCount
    }
    return AchievementProgressSnapshot(
      totalClears = totalClears,
      clearsByBoard = clearsByBoard,
      boardTotals = boardTotals,
      translationPercent = translationPercent,
      rtaClearCount = save.rtaClearCount,
      rtaBestTimeMs = save.rtaBestTimeMs,
    )
  }

  fun evaluateEligible(snapshot: AchievementProgressSnapshot): Set<KidouAchievement> {
    val earned = LinkedHashSet<KidouAchievement>()
    if (snapshot.totalClears >= 1) earned += KidouAchievement.FIRST_ACTIVATION

    if ((snapshot.clearsByBoard[1] ?: 0) >= (snapshot.boardTotals[1] ?: 25)) {
      earned += KidouAchievement.BOARD_1_COMPLETE
    }
    if ((snapshot.clearsByBoard[2] ?: 0) >= (snapshot.boardTotals[2] ?: 25)) {
      earned += KidouAchievement.BOARD_2_COMPLETE
    }
    if ((snapshot.clearsByBoard[3] ?: 0) >= (snapshot.boardTotals[3] ?: 25)) {
      earned += KidouAchievement.BOARD_3_COMPLETE
    }
    if ((snapshot.clearsByBoard[4] ?: 0) >= (snapshot.boardTotals[4] ?: 25)) {
      earned += KidouAchievement.BOARD_4_COMPLETE
    }
    if ((snapshot.clearsByBoard[5] ?: 0) >= (snapshot.boardTotals[5] ?: 25)) {
      earned += KidouAchievement.BOARD_5_COMPLETE
    }

    val totalTarget = snapshot.boardTotals.values.sum().coerceAtLeast(TOTAL_PLAYABLE_TARGET)
    if (snapshot.totalClears >= totalTarget) {
      earned += KidouAchievement.ALL_125_COMPLETE
    }

    if (snapshot.translationPercent >= 50) earned += KidouAchievement.LEXICON_50_PERCENT
    if (snapshot.translationPercent >= 100) earned += KidouAchievement.LEXICON_100_PERCENT

    val bestMs = snapshot.rtaBestTimeMs
    if (snapshot.rtaClearCount >= 1 || (bestMs != null && bestMs > 0L)) {
      earned += KidouAchievement.RTA_FIRST_CLEAR
    }
    if (bestMs != null && bestMs in 1L..THREE_MINUTES_MS) {
      earned += KidouAchievement.RTA_UNDER_3_MIN
    }
    if (bestMs != null && bestMs in 1L..ONE_MINUTE_MS) {
      earned += KidouAchievement.RTA_UNDER_1_MIN
    }

    return earned
  }

  fun computePendingUnlocks(
    snapshot: AchievementProgressSnapshot,
    alreadyUnlockedKeys: Set<String>,
  ): List<KidouAchievement> {
    val eligible = evaluateEligible(snapshot)
    return eligible.filter { it.key !in alreadyUnlockedKeys }
  }
}
