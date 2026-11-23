package com.kidou.game

import com.kidou.game.engine.Base5
import com.kidou.game.engine.GameDataLoader
import com.kidou.game.engine.LevelEngine
import com.kidou.game.model.AppLanguage
import com.kidou.game.model.ConditionRequirement
import com.kidou.game.model.DictionaryEntry
import com.kidou.game.model.EntrySource
import com.kidou.game.model.LampState
import com.kidou.game.model.LevelDef
import com.kidou.game.model.LoadedGameData
import com.kidou.game.model.PuzzleType
import com.kidou.game.model.SoundEffect
import com.kidou.game.model.WordColorTag
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidouV2Test {

  private fun loadAssets(): LoadedGameData {
    val candidates = listOf(File("src/main/assets"), File("app/src/main/assets"))
    val assetsDir =
      candidates.firstOrNull { it.exists() && it.isDirectory }
        ?: error("Could not locate assets directory from ${File(".").absolutePath}")

    return File(assetsDir, "glyphs.json").inputStream().use { g ->
      File(assetsDir, "words.json").inputStream().use { w ->
        File(assetsDir, "boards.json").inputStream().use { b ->
          GameDataLoader.loadFromStreams(g, w, b)
        }
      }
    }
  }

  @Test
  fun glyphsAreValid12x12AndAllWordsHaveBilingualMeanings() {
    val data = loadAssets()
    assertTrue(data.glyphs.size >= 45)
    data.glyphs.forEach { (id, grid) ->
      assertEquals("Glyph $id must have 12 rows", 12, grid.rows.size)
      grid.rows.forEachIndexed { idx, row ->
        assertEquals("Glyph $id row $idx must have length 12", 12, row.length)
        assertTrue(
          "Glyph $id row $idx invalid char",
          row.all { it == '.' || it == '#' },
        )
      }
    }

    data.words.values.forEach { word ->
      assertTrue("Missing glyph ${word.glyphId}", data.glyphs.containsKey(word.glyphId))
      assertTrue("Missing meaningJa on ${word.id}", word.meaningJa.isNotBlank())
      assertTrue("Missing meaningEn on ${word.id}", word.meaningEn.isNotBlank())
    }

    fun checkSlots(slots: List<List<String>>, ctx: String) {
      slots.flatten().forEach { wordId ->
        assertTrue("Unknown word '$wordId' in $ctx", data.words.containsKey(wordId))
      }
    }

    data.boardCollection.guideLines.forEachIndexed { i, line -> checkSlots(line, "guide $i") }
    assertEquals(2, data.boardCollection.boards.size)
    val board1 = data.boardCollection.boardById(1)
    assertEquals(25, board1.levels.size)
    val board2 = data.boardCollection.boardById(2)
    assertEquals(1, board2.levels.size)

    data.boardCollection.boards.forEach { board ->
      checkSlots(board.titleSlots, "board ${board.id} title")
      board.storyLines.forEachIndexed { i, line -> checkSlots(line, "board ${board.id} story $i") }
      board.levels.forEach { level ->
        level.plates.forEachIndexed { p, plate -> checkSlots(plate, "B${board.id}-L${level.levelNumber} plate $p") }
        checkSlots(level.initialState.primaryMessage, "B${board.id}-L${level.levelNumber} init1")
        checkSlots(level.initialState.secondaryMessage, "B${board.id}-L${level.levelNumber} init2")
        checkSlots(level.completionPrimary, "B${board.id}-L${level.levelNumber} comp1")
        checkSlots(level.completionSecondary, "B${board.id}-L${level.levelNumber} comp2")
        level.sequenceSteps.forEachIndexed { s, step ->
          checkSlots(step.hintPrimary, "B${board.id}-L${level.levelNumber} step $s hint")
        }
      }
    }
  }

  @Test
  fun base5ConversionProducesExactDigits() {
    assertEquals(listOf("d_0"), Base5.toDigitWordIds(0))
    assertEquals(listOf("d_1"), Base5.toDigitWordIds(1))
    assertEquals(listOf("d_4"), Base5.toDigitWordIds(4))
    assertEquals(listOf("d_1", "d_0"), Base5.toDigitWordIds(5))
    assertEquals(listOf("d_1", "d_1"), Base5.toDigitWordIds(6))
    assertEquals(listOf("d_3", "d_4"), Base5.toDigitWordIds(19))
    assertEquals(listOf("d_4", "d_0"), Base5.toDigitWordIds(20))
    assertEquals(listOf("d_1", "d_0", "d_0"), Base5.toDigitWordIds(25))
  }

  @Test
  fun allBoard1LevelsAndBoard2Level1AreSolvableAndGateWorksAt20Of25() {
    val data = loadAssets()
    val board1 = data.boardCollection.boardById(1)
    val board2 = data.boardCollection.boardById(2)
    val cleared = mutableSetOf<String>()

    board1.levels.forEachIndexed { idx, level ->
      solveLevelAndAssertWarnings(boardId = 1, level = level)
      cleared.add(LevelEngine.levelKey(1, level.levelNumber))
      if (idx + 1 < 20) {
        assertFalse("Board 2 should stay locked at ${idx + 1}/25", LevelEngine.isBoardUnlocked(board2, cleared))
      } else {
        assertTrue("Board 2 must unlock at ${idx + 1}/25", LevelEngine.isBoardUnlocked(board2, cleared))
      }
    }

    val b2l1 = board2.levelByNumber(1)!!
    solveLevelAndAssertWarnings(boardId = 2, level = b2l1)
  }

  private fun solveLevelAndAssertWarnings(boardId: Int, level: LevelDef) {
    var state = LevelEngine.createInitialState(boardId, level)
    assertFalse(state.isCleared)

    if (level.puzzleType == PuzzleType.SEQUENCE) {
      // Trigger wrong action first to verify soft reset without game over
      val wrongTrigger = if (level.sequenceSteps.first().trigger == "button_push") "key_insert" else "button_push"
      val wrongRes = LevelEngine.step(level, state, wrongTrigger, 1L, 1L)
      assertEquals(LampState.RED, wrongRes.newState.lamp)
      assertEquals(SoundEffect.WARN, wrongRes.sound)
      assertEquals(0, wrongRes.newState.seqStep)
      state = wrongRes.newState

      var t = 10L
      level.sequenceSteps.forEach { step ->
        repeat(step.repeatCount) {
          val res = LevelEngine.step(level, state, step.trigger, t, t)
          t += 1L
          state = res.newState
        }
      }
      assertTrue("Sequence level B$boardId-L${level.levelNumber} must clear", state.isCleared)
      assertEquals(LampState.GREEN, state.lamp)
      return
    }

    val req: ConditionRequirement = level.requirements ?: ConditionRequirement()
    var t = 1L

    if (req.conduitEq != null && state.conduit != req.conduitEq) {
      val trig = if (req.conduitEq == "connected") "conduit_connect" else "conduit_cut"
      state = LevelEngine.step(level, state, trig, t, t++).newState
    }
    if (req.lockEq != null && state.lock != req.lockEq) {
      val trig = if (req.lockEq == "locked") "lock_lock" else "lock_unlock"
      state = LevelEngine.step(level, state, trig, t, t++).newState
    }
    if (req.keyEq != null && state.key != req.keyEq) {
      val trig = if (req.keyEq == "in") "key_insert" else "key_remove"
      state = LevelEngine.step(level, state, trig, t, t++).newState
    }
    if (req.leverEq != null && state.lever != req.leverEq) {
      val trig = if (req.leverEq == "up") "lever_push" else "lever_pull"
      state = LevelEngine.step(level, state, trig, t, t++).newState
    }
    if (req.dialEq != null) {
      while (state.dial < req.dialEq) {
        val trig = if (req.dialEq - state.dial >= 5) "dial_inc5" else "dial_inc"
        state = LevelEngine.step(level, state, trig, t, t++).newState
      }
      while (state.dial > req.dialEq) {
        state = LevelEngine.step(level, state, "dial_dec", t, t++).newState
      }
    }
    if (req.valveEq != null) {
      while (state.valve < req.valveEq) {
        val trig = if (req.valveEq - state.valve >= 5) "valve_inc5" else "valve_inc"
        state = LevelEngine.step(level, state, trig, t, t++).newState
      }
      while (state.valve > req.valveEq) {
        state = LevelEngine.step(level, state, "valve_dec", t, t++).newState
      }
    }

    val presses = (req.buttonPressesEq ?: 1).coerceAtLeast(1)
    repeat(presses) {
      state = LevelEngine.step(level, state, "button_push", t, t++).newState
    }
    assertTrue("Condition level B$boardId-L${level.levelNumber} must clear", state.isCleared)
    assertEquals(LampState.GREEN, state.lamp)
  }

  @Test
  fun translationRateExcludesDefaultSetAndSupportsLanguageSwitch() {
    val data = loadAssets()
    val dict = LevelEngine.buildDefaultDictionary(data, AppLanguage.JA).toMutableMap()
    val discovered = data.wordOrder.toSet()

    // 7 default-set words are pre-populated, yet translation rate starts at 0%
    val startStats = LevelEngine.computeTranslationStats(data, discovered, dict, AppLanguage.JA)
    assertEquals(0, startStats.translatedCount)
    assertEquals(data.wordOrder.size - data.defaultSetWordIds.size, startStats.totalCount)
    assertEquals(0, startStats.percent)

    // Ad reveal counts in rate & adapts to language switch
    val buttonDef = data.words["n_button"]!!
    dict["n_button"] =
      DictionaryEntry(
        wordId = "n_button",
        translation = buttonDef.meaningJa,
        colorTag = WordColorTag.OBJECT,
        source = EntrySource.AD_REVEALED,
      )
    // Player custom entry stays untouched on language switch
    dict["v_push"] =
      DictionaryEntry(
        wordId = "v_push",
        translation = "おす(仮)",
        colorTag = WordColorTag.HYPOTHESIS,
        source = EntrySource.PLAYER,
      )

    val midStats = LevelEngine.computeTranslationStats(data, discovered, dict, AppLanguage.JA)
    assertEquals(2, midStats.translatedCount)
    assertTrue(midStats.percent > 0)

    // Switch to EN: default-set and ad-revealed switch to EN, player text stays "おす(仮)"
    assertEquals("you", dict["n_you"]!!.resolvedTranslation(data.words["n_you"], AppLanguage.EN))
    assertEquals("button", dict["n_button"]!!.resolvedTranslation(buttonDef, AppLanguage.EN))
    assertEquals("おす(仮)", dict["v_push"]!!.resolvedTranslation(data.words["v_push"], AppLanguage.EN))

    // Filling all non-default words reaches 100%
    data.words.values.filter { !it.isDefaultSet }.forEach { w ->
      dict[w.id] = DictionaryEntry(wordId = w.id, translation = w.meaningEn, source = EntrySource.DEBUG_BULK)
    }
    val fullStats = LevelEngine.computeTranslationStats(data, discovered, dict, AppLanguage.EN)
    assertEquals(fullStats.totalCount, fullStats.translatedCount)
    assertEquals(100, fullStats.percent)
  }
}
