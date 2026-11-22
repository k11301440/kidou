package com.kidou.game.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kidou.game.model.SaveData
import java.io.File

class SaveRepository(context: Context) {
  private val saveFile = File(context.filesDir, "kidou_save.json")
  private val tempFile = File(context.filesDir, "kidou_save.tmp")
  private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

  fun load(): SaveData? {
    if (!saveFile.exists()) return null
    return runCatching {
      saveFile.readText(Charsets.UTF_8).let { text ->
        gson.fromJson(text, SaveData::class.java)
      }
    }.getOrNull()
  }

  fun save(data: SaveData) {
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
