package com.kidou.game.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kidou.game.model.GlyphGrid
import com.kidou.game.model.LoadedGameData
import com.kidou.game.model.MachineDef
import com.kidou.game.model.WordDef
import java.io.InputStream
import java.io.InputStreamReader

object GameDataLoader {
  private val gson = Gson()

  fun loadFromAssets(context: Context): LoadedGameData {
    val assets = context.assets
    return assets.open("glyphs.json").use { gStream ->
      assets.open("words.json").use { wStream ->
        assets.open("machine.json").use { mStream ->
          loadFromStreams(gStream, wStream, mStream)
        }
      }
    }
  }

  fun loadFromStreams(
    glyphsStream: InputStream,
    wordsStream: InputStream,
    machineStream: InputStream,
  ): LoadedGameData {
    val glyphsType = object : TypeToken<Map<String, List<String>>>() {}.type
    val rawGlyphs: Map<String, List<String>> =
      InputStreamReader(glyphsStream, Charsets.UTF_8).use { reader ->
        gson.fromJson(reader, glyphsType)
      }
    val glyphs = rawGlyphs.mapValues { (_, rows) -> GlyphGrid(rows) }

    val wordsType = object : TypeToken<List<WordDef>>() {}.type
    val wordList: List<WordDef> =
      InputStreamReader(wordsStream, Charsets.UTF_8).use { reader ->
        gson.fromJson(reader, wordsType)
      }
    val words = wordList.associateBy { it.id }
    val wordOrder = wordList.map { it.id }

    val machine: MachineDef =
      InputStreamReader(machineStream, Charsets.UTF_8).use { reader ->
        gson.fromJson(reader, MachineDef::class.java)
      }

    return LoadedGameData(
      glyphs = glyphs,
      words = words,
      wordOrder = wordOrder,
      machine = machine,
    )
  }
}
