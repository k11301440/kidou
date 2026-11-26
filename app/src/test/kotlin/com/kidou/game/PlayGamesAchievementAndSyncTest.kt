package com.kidou.game

import com.kidou.game.model.AppLanguage
import com.kidou.game.model.DictionaryEntry
import com.kidou.game.model.EntrySource
import com.kidou.game.model.SaveDataV2
import com.kidou.game.model.WordColorTag
import com.kidou.game.playgames.AchievementEvaluator
import com.kidou.game.playgames.AchievementProgressSnapshot
import com.kidou.game.playgames.KidouAchievement
import com.kidou.game.playgames.SaveMergePolicy
import com.kidou.game.ui.theme.ColorTheme
import com.kidou.game.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayGamesAchievementAndSyncTest {

  private fun baseSnapshot(
    totalClears: Int = 0,
    clearsByBoard: Map<Int, Int> = emptyMap(),
    translationPercent: Int = 0,
    rtaClearCount: Int = 0,
    rtaBestTimeMs: Long? = null,
  ) = AchievementProgressSnapshot(
    totalClears = totalClears,
    clearsByBoard = clearsByBoard,
    boardTotals = mapOf(1 to 25, 2 to 25, 3 to 25, 4 to 25, 5 to 25),
    translationPercent = translationPercent,
    rtaClearCount = rtaClearCount,
    rtaBestTimeMs = rtaBestTimeMs,
  )

  @Test
  fun achievementEvaluationCoversAll12MilestonesAndBoundaries() {
    assertTrue(AchievementEvaluator.evaluateEligible(baseSnapshot()).isEmpty())

    // First activation
    val first = AchievementEvaluator.evaluateEligible(baseSnapshot(totalClears = 1, clearsByBoard = mapOf(1 to 1)))
    assertEquals(setOf(KidouAchievement.FIRST_ACTIVATION), first)

    // Board 1 complete boundary (24 vs 25)
    assertFalse(
      KidouAchievement.BOARD_1_COMPLETE in
        AchievementEvaluator.evaluateEligible(baseSnapshot(totalClears = 24, clearsByBoard = mapOf(1 to 24))),
    )
    assertTrue(
      KidouAchievement.BOARD_1_COMPLETE in
        AchievementEvaluator.evaluateEligible(baseSnapshot(totalClears = 25, clearsByBoard = mapOf(1 to 25))),
    )

    // All 5 boards & 125 clears
    val allCleared =
      AchievementEvaluator.evaluateEligible(
        baseSnapshot(
          totalClears = 125,
          clearsByBoard = mapOf(1 to 25, 2 to 25, 3 to 25, 4 to 25, 5 to 25),
        ),
      )
    assertTrue(KidouAchievement.BOARD_2_COMPLETE in allCleared)
    assertTrue(KidouAchievement.BOARD_3_COMPLETE in allCleared)
    assertTrue(KidouAchievement.BOARD_4_COMPLETE in allCleared)
    assertTrue(KidouAchievement.BOARD_5_COMPLETE in allCleared)
    assertTrue(KidouAchievement.ALL_125_COMPLETE in allCleared)

    // Translation rate milestones
    assertFalse(KidouAchievement.LEXICON_50_PERCENT in AchievementEvaluator.evaluateEligible(baseSnapshot(translationPercent = 49)))
    assertTrue(KidouAchievement.LEXICON_50_PERCENT in AchievementEvaluator.evaluateEligible(baseSnapshot(translationPercent = 50)))
    assertFalse(KidouAchievement.LEXICON_100_PERCENT in AchievementEvaluator.evaluateEligible(baseSnapshot(translationPercent = 99)))
    assertTrue(KidouAchievement.LEXICON_100_PERCENT in AchievementEvaluator.evaluateEligible(baseSnapshot(translationPercent = 100)))

    // RTA time milestones
    val over3Min = AchievementEvaluator.evaluateEligible(baseSnapshot(rtaClearCount = 1, rtaBestTimeMs = 180_001L))
    assertTrue(KidouAchievement.RTA_FIRST_CLEAR in over3Min)
    assertFalse(KidouAchievement.RTA_UNDER_3_MIN in over3Min)
    assertFalse(KidouAchievement.RTA_UNDER_1_MIN in over3Min)

    val exactly3Min = AchievementEvaluator.evaluateEligible(baseSnapshot(rtaClearCount = 1, rtaBestTimeMs = 180_000L))
    assertTrue(KidouAchievement.RTA_UNDER_3_MIN in exactly3Min)
    assertFalse(KidouAchievement.RTA_UNDER_1_MIN in exactly3Min)

    val exactly1Min = AchievementEvaluator.evaluateEligible(baseSnapshot(rtaClearCount = 2, rtaBestTimeMs = 60_000L))
    assertTrue(KidouAchievement.RTA_UNDER_1_MIN in exactly1Min)
  }

  @Test
  fun offlineCatchUpAndIdempotentPendingUnlocks() {
    val offlineProgressed =
      baseSnapshot(
        totalClears = 125,
        clearsByBoard = mapOf(1 to 25, 2 to 25, 3 to 25, 4 to 25, 5 to 25),
        translationPercent = 100,
        rtaClearCount = 3,
        rtaBestTimeMs = 55_400L,
      )

    val pendingFirstPass = AchievementEvaluator.computePendingUnlocks(offlineProgressed, emptySet())
    assertEquals(12, pendingFirstPass.size)
    assertEquals(KidouAchievement.entries.toSet(), pendingFirstPass.toSet())

    // Re-evaluating after unlock keys recorded must be completely idempotent (0 pending)
    val recorded = pendingFirstPass.map { it.key }.toSet()
    val pendingSecondPass = AchievementEvaluator.computePendingUnlocks(offlineProgressed, recorded)
    assertTrue(pendingSecondPass.isEmpty())
  }

  @Test
  fun saveMergePolicyResolvesConflictsWithoutLosingProgress() {
    val local =
      SaveDataV2(
        clearedLevelKeys = setOf("1-1", "1-2"),
        discoveredWordIds = setOf("n_machine", "v_push"),
        dictionary =
          mapOf(
            "n_button" to DictionaryEntry("n_button", "ぼたん", WordColorTag.OBJECT, "note", EntrySource.PLAYER),
          ),
        language = AppLanguage.JA,
        themeMode = ThemeMode.DARK,
        colorTheme = ColorTheme.AMBER_RUIN,
        rtaBestTimeMs = 92_000L,
        rtaClearCount = 1,
        playGamesOptIn = true,
        unlockedAchievements = setOf(KidouAchievement.FIRST_ACTIVATION.key),
        updatedAtMs = 1000L,
      )

    val cloud =
      SaveDataV2(
        clearedLevelKeys = setOf("1-2", "1-3", "1-4", "1-5"),
        discoveredWordIds = setOf("n_machine", "n_dial"),
        dictionary =
          mapOf(
            "n_button" to DictionaryEntry("n_button", "", WordColorTag.UNCLASSIFIED),
            "n_dial" to DictionaryEntry("n_dial", "だいやる", WordColorTag.OBJECT, source = EntrySource.PLAYER),
          ),
        language = AppLanguage.EN,
        themeMode = ThemeMode.LIGHT,
        colorTheme = ColorTheme.COBALT_SIGNAL,
        rtaBestTimeMs = 74_500L,
        rtaClearCount = 2,
        playGamesOptIn = true,
        unlockedAchievements = setOf(KidouAchievement.RTA_FIRST_CLEAR.key),
        updatedAtMs = 900L,
      )

    val merged = SaveMergePolicy.resolveConflict(local, cloud)
    // Cloud had 4 clears vs local's 2 -> cloud wins settings & priority, but union preserves ALL progress!
    assertEquals(setOf("1-1", "1-2", "1-3", "1-4", "1-5"), merged.clearedLevelKeys)
    assertEquals(setOf("n_machine", "v_push", "n_dial"), merged.discoveredWordIds)
    assertEquals(74_500L, merged.rtaBestTimeMs)
    assertEquals(2, merged.rtaClearCount)
    assertEquals(
      setOf(KidouAchievement.FIRST_ACTIVATION.key, KidouAchievement.RTA_FIRST_CLEAR.key),
      merged.unlockedAchievements,
    )
    // Blank entry on winning cloud snapshot must NOT overwrite populated local dictionary entry
    assertEquals("ぼたん", merged.dictionary["n_button"]?.translation)
    assertEquals("だいやる", merged.dictionary["n_dial"]?.translation)
    assertEquals(AppLanguage.EN, merged.language)
    assertEquals(ThemeMode.LIGHT, merged.themeMode)
  }
}
