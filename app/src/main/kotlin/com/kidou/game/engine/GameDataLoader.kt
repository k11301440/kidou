package com.kidou.game.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kidou.game.model.BoardCollectionDef
import com.kidou.game.model.BoardDef
import com.kidou.game.model.GlyphGrid
import com.kidou.game.model.LevelDef
import com.kidou.game.model.LevelInitialState
import com.kidou.game.model.LoadedGameData
import com.kidou.game.model.PuzzleType
import com.kidou.game.model.SequenceStepDef
import com.kidou.game.model.WordDef
import java.io.InputStream
import java.io.InputStreamReader

object GameDataLoader {
  private val gson = Gson()

  fun loadFromAssets(context: Context): LoadedGameData {
    val assets = context.assets
    return assets.open("glyphs.json").use { gStream ->
      assets.open("words.json").use { wStream ->
        assets.open("boards.json").use { bStream ->
          loadFromStreams(gStream, wStream, bStream)
        }
      }
    }
  }

  @Suppress("USELESS_ELVIS")
  fun loadFromStreams(
    glyphsStream: InputStream,
    wordsStream: InputStream,
    boardsStream: InputStream,
  ): LoadedGameData {
    val glyphsType = object : TypeToken<Map<String, List<String>>>() {}.type
    val rawGlyphs: Map<String, List<String>> =
      InputStreamReader(glyphsStream, Charsets.UTF_8).use { reader ->
        gson.fromJson(reader, glyphsType)
      }
    val glyphs = rawGlyphs.mapValues { (_, rows) -> GlyphGrid(rows ?: emptyList()) }

    val wordsType = object : TypeToken<List<WordDef>>() {}.type
    val rawWordList: List<WordDef> =
      InputStreamReader(wordsStream, Charsets.UTF_8).use { reader ->
        gson.fromJson(reader, wordsType)
      }
    val wordList =
      rawWordList.map { w ->
        w.copy(morphemes = w.morphemes ?: emptyList())
      }
    val words = wordList.associateBy { it.id }
    val wordOrder = wordList.map { it.id }

    val rawBoards: BoardCollectionDef =
      InputStreamReader(boardsStream, Charsets.UTF_8).use { reader ->
        gson.fromJson(reader, BoardCollectionDef::class.java)
      }
    val boardCollection = normalizeBoards(rawBoards)

    return LoadedGameData(
      glyphs = glyphs,
      words = words,
      wordOrder = wordOrder,
      boardCollection = boardCollection,
    )
  }

  @Suppress("USELESS_ELVIS")
  private fun normalizeBoards(raw: BoardCollectionDef): BoardCollectionDef {
    return raw.copy(
      guideLines = raw.guideLines ?: emptyList(),
      boards =
        (raw.boards ?: emptyList()).map { b ->
          b.copy(
            titleSlots = b.titleSlots ?: emptyList(),
            storyLines = b.storyLines ?: emptyList(),
            levels =
              (b.levels ?: emptyList()).map { l ->
                val init = l.initialState ?: LevelInitialState()
                l.copy(
                  puzzleType = l.puzzleType ?: PuzzleType.CONDITION,
                  unlockedControls = l.unlockedControls ?: emptyList(),
                  plates = l.plates ?: emptyList(),
                  initialState =
                    init.copy(
                      lever = init.lever ?: "up",
                      key = init.key ?: "out",
                      conduit = init.conduit ?: "cut",
                      lock = init.lock ?: "locked",
                      lamp = init.lamp ?: "off",
                      primaryMessage = init.primaryMessage ?: emptyList(),
                      secondaryMessage = init.secondaryMessage ?: emptyList(),
                    ),
                  sequenceSteps =
                    (l.sequenceSteps ?: emptyList()).map { s ->
                      s.copy(
                        repeatCount = s.repeatCount.coerceAtLeast(1),
                        hintPrimary = s.hintPrimary ?: emptyList(),
                      )
                    },
                  completionPrimary = l.completionPrimary ?: emptyList(),
                  completionSecondary = l.completionSecondary ?: emptyList(),
                )
              },
          )
        },
    )
  }
}
