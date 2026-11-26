package com.kidou.game.config

import com.kidou.game.playgames.KidouAchievement

/**
 * Play Games Services v2 IDs & constants.
 * Placeholder values must be replaced before Play Console production publishing:
 * 1) Play Games App ID -> pass -PKIDOU_PLAY_GAMES_APP_ID=... (or update R.string.game_services_project_id)
 * 2) Achievement IDs -> replace each "CgkI_PLACEHOLDER_..." with the Play Console generated ID.
 */
object PlayGamesConfig {
  const val APP_ID_PLACEHOLDER = "000000000000"
  const val SNAPSHOT_UNIQUE_NAME = "kidou_cloud_save_v1"
  const val PLAY_GAMES_APP_PACKAGE = "com.google.android.play.games"

  val ACHIEVEMENT_IDS: Map<KidouAchievement, String> =
    mapOf(
      KidouAchievement.FIRST_ACTIVATION to "CgkI_PLACEHOLDER_FIRST_ACTIVATION",
      KidouAchievement.BOARD_1_COMPLETE to "CgkI_PLACEHOLDER_BOARD_1_COMPLETE",
      KidouAchievement.BOARD_2_COMPLETE to "CgkI_PLACEHOLDER_BOARD_2_COMPLETE",
      KidouAchievement.BOARD_3_COMPLETE to "CgkI_PLACEHOLDER_BOARD_3_COMPLETE",
      KidouAchievement.BOARD_4_COMPLETE to "CgkI_PLACEHOLDER_BOARD_4_COMPLETE",
      KidouAchievement.BOARD_5_COMPLETE to "CgkI_PLACEHOLDER_BOARD_5_COMPLETE",
      KidouAchievement.ALL_125_COMPLETE to "CgkI_PLACEHOLDER_ALL_125_COMPLETE",
      KidouAchievement.LEXICON_50_PERCENT to "CgkI_PLACEHOLDER_LEXICON_50_PERCENT",
      KidouAchievement.LEXICON_100_PERCENT to "CgkI_PLACEHOLDER_LEXICON_100_PERCENT",
      KidouAchievement.RTA_FIRST_CLEAR to "CgkI_PLACEHOLDER_RTA_FIRST_CLEAR",
      KidouAchievement.RTA_UNDER_3_MIN to "CgkI_PLACEHOLDER_RTA_UNDER_3_MIN",
      KidouAchievement.RTA_UNDER_1_MIN to "CgkI_PLACEHOLDER_RTA_UNDER_1_MIN",
    )

  fun isAppIdConfigured(appId: String): Boolean =
    appId.isNotBlank() && appId != APP_ID_PLACEHOLDER

  fun isAchievementIdConfigured(id: String?): Boolean =
    !id.isNullOrBlank() && !id.startsWith("CgkI_PLACEHOLDER_")

  fun configuredAchievementId(achievement: KidouAchievement): String? {
    val id = ACHIEVEMENT_IDS[achievement]
    return if (isAchievementIdConfigured(id)) id else null
  }
}
