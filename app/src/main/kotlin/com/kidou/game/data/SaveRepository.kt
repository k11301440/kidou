package com.kidou.game.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kidou.game.model.AppLanguage
import com.kidou.game.model.SaveDataV2
import com.kidou.game.ui.theme.ColorTheme
import com.kidou.game.ui.theme.ThemeMode
import java.io.File

class SaveRepository(context: Context) {
  private val saveFile = File(context.filesDir, "kidou_save_v2.json")
  private val tempFile = File(context.filesDir, "kidou_save_v2.tmp")
  private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

  @Suppress("USELESS_ELVIS")
  fun load(): SaveDataV2? {
    if (!saveFile.exists()) return null
    return runCatching {
      saveFile.readText(Charsets.UTF_8).let { text ->
        val raw = gson.fromJson(text, SaveDataV2::class.java) ?: return null
        raw.copy(
          dictionary = raw.dictionary ?: emptyMap(),
          discoveredWordIds = raw.discoveredWordIds ?: emptySet(),
          clearedLevelKeys = raw.clearedLevelKeys ?: emptySet(),
          operationLog = raw.operationLog ?: emptyList(),
          language = raw.language ?: AppLanguage.JA,
          themeMode = raw.themeMode ?: ThemeMode.DARK,
          colorTheme = raw.colorTheme ?: ColorTheme.AMBER_RUIN,
        )
      }
    }.getOrNull()
  }

  fun save(data: SaveDataV2) {
    runCatching {
      val json = gson.toJson(data)
      tempFile.writeText(json, Charsets.UTF_8)
      if (!tempFile.renameTo(saveFile)) {
        saveFile.writeText(json, Charsets.UTF_8)
        tempFile.delete()
      }
    }
  }

  fun clear() {
    runCatching {
      if (saveFile.exists()) saveFile.delete()
      if (tempFile.exists()) tempFile.delete()
    }
  }
}
