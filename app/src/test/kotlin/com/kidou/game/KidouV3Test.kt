package com.kidou.game

import com.kidou.game.config.AppConfig
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
import com.kidou.game.model.LogicClause
import com.kidou.game.model.ModuleCheck
import com.kidou.game.model.PuzzleType
import com.kidou.game.model.SoundEffect
import com.kidou.game.model.WordColorTag
import com.kidou.game.ui.theme.ColorTheme
import com.kidou.game.ui.theme.ThemeMode
import com.kidou.game.ui.theme.buildKidouPalette
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidouV3Test {

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
  fun glyphsWordsAndBoardsSchemaAreValidForV3() {
    val data = loadAssets()
    assertTrue("Expected at least 58 glyphs, got ${data.glyphs.size}", data.glyphs.size >= 58)
    assertTrue("Expected at least 58 words, got ${data.words.size}", data.words.size >= 58)

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

    assertEquals("言語解読 × 試行錯誤", data.boardCollection.titleSubtitle(AppLanguage.JA))
    assertTrue(data.boardCollection.titleSubtitle(AppLanguage.EN).isNotBlank())

    fun checkSlots(slots: List<List<String>>, ctx: String) {
      slots.forEach { slot ->
        slot.forEach { wordId ->
          assertTrue("Unknown wordId '$wordId' in $ctx", data.words.containsKey(wordId))
        }
      }
    }

    data.boardCollection.guideLines.forEachIndexed { idx, line -> checkSlots(line, "guideLines[$idx]") }
    assertEquals(3, data.boardCollection.boards.size)

    val b1 = data.boardCollection.boardById(1)
    val b2 = data.boardCollection.boardById(2)
    val b3 = data.boardCollection.boardById(3)
    assertEquals(25, b1.levels.size)
    assertEquals(25, b2.levels.size)
    assertTrue(b3.levels.isNotEmpty())
    assertEquals(1, b2.requiredBoardId)
    assertEquals(20, b2.requiredClears)
    assertEquals(2, b3.requiredBoardId)
    assertEquals(20, b3.requiredClears)

    data.boardCollection.boards.forEach { board ->
      checkSlots(board.titleSlots, "board${board.id}.title")
      board.storyLines.forEachIndexed { idx, line -> checkSlots(line, "board${board.id}.story[$idx]") }
      board.levels.forEach { level ->
        val tag = "B${board.id}-L${level.levelNumber}"
        level.plates.forEachIndexed { idx, line -> checkSlots(line, "$tag.plates[$idx]") }
        checkSlots(level.initialState.primaryMessage, "$tag.initPrimary")
        checkSlots(level.initialState.secondaryMessage, "$tag.initSecondary")
        checkSlots(level.completionPrimary, "$tag.completePrimary")
        checkSlots(level.completionSecondary, "$tag.completeSecondary")
        level.sequenceSteps.forEachIndexed { idx, step ->
          checkSlots(step.hintPrimary, "$tag.seq[$idx]")
        }
        level.requirements?.logicClauses?.forEachIndexed { idx, clause ->
          checkSlots(clause.hintPrimary, "$tag.clausePrimary[$idx]")
          checkSlots(clause.hintSecondary, "$tag.clauseSecondary[$idx]")
        }
      }
    }
  }

  @Test
  fun all51LevelsCanBeSolvedAndBoardsUnlockAt20Clears() {
    val data = loadAssets()
    val board1 = data.boardCollection.boardById(1)
    val board2 = data.boardCollection.boardById(2)
    val board3 = data.boardCollection.boardById(3)

    val cleared = mutableSetOf<String>()

    // Board 1: levels 1..25, unlocks Board 2 at 20 clears
    board1.levels.forEachIndexed { idx, level ->
      solveLevelAndAssert(boardId = 1, level = level)
      cleared.add(LevelEngine.levelKey(1, level.levelNumber))
      if (idx + 1 < 20) {
        assertFalse("Board 2 should stay locked at ${idx + 1}/25", LevelEngine.isBoardUnlocked(board2, cleared))
      } else {
        assertTrue("Board 2 must unlock at ${idx + 1}/25", LevelEngine.isBoardUnlocked(board2, cleared))
      }
    }

    // Board 2: levels 1..25, unlocks Board 3 at 20 clears
    board2.levels.forEachIndexed { idx, level ->
      solveLevelAndAssert(boardId = 2, level = level)
      cleared.add(LevelEngine.levelKey(2, level.levelNumber))
      if (idx + 1 < 20) {
        assertFalse("Board 3 should stay locked at B2 ${idx + 1}/25", LevelEngine.isBoardUnlocked(board3, cleared))
      } else {
        assertTrue("Board 3 must unlock at B2 ${idx + 1}/25", LevelEngine.isBoardUnlocked(board3, cleared))
      }
    }

    // Board 3: all defined levels
    board3.levels.forEach { level ->
      solveLevelAndAssert(boardId = 3, level = level)
      cleared.add(LevelEngine.levelKey(3, level.levelNumber))
    }
    assertEquals(51, cleared.size)
  }

  private fun solveLevelAndAssert(boardId: Int, level: LevelDef) {
    var state = LevelEngine.createInitialState(boardId, level)
    assertFalse(state.isCleared)

    if (level.puzzleType == PuzzleType.SEQUENCE) {
      // Wrong step must trigger soft reset with RED lamp and WARN sound
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

    // If hold is required, tapping normal push first must fail with RED/WARN
    if (req.requireHold == true) {
      val badPush = LevelEngine.step(level, state, "button_push", t, t++)
      assertEquals(LampState.RED, badPush.newState.lamp)
      assertEquals(SoundEffect.WARN, badPush.sound)
      assertFalse(badPush.newState.isCleared)
      state = badPush.newState
    }

    // Resolve target boolean/enum configuration satisfying both req.*Eq and req.logicClauses
    val target = findSatisfyingConfig(req)

    if (state.swA != target.swA) state = LevelEngine.step(level, state, "sw_a_toggle", t, t++).newState
    if (state.swB != target.swB) state = LevelEngine.step(level, state, "sw_b_toggle", t, t++).newState
    if (state.swC != target.swC) state = LevelEngine.step(level, state, "sw_c_toggle", t, t++).newState

    if (state.conduit != target.conduit) {
      val trig = if (target.conduit == "connected") "conduit_connect" else "conduit_cut"
      state = LevelEngine.step(level, state, trig, t, t++).newState
    }
    if (state.lock != target.lock) {
      val trig = if (target.lock == "locked") "lock_lock" else "lock_unlock"
      state = LevelEngine.step(level, state, trig, t, t++).newState
    }
    if (state.key != target.key) {
      val trig = if (target.key == "in") "key_insert" else "key_remove"
      state = LevelEngine.step(level, state, trig, t, t++).newState
    }
    if (state.slider != target.slider) {
      val trig = if (target.slider == "right") "slider_right" else "slider_left"
      state = LevelEngine.step(level, state, trig, t, t++).newState
    }
    if (state.lever != target.lever) {
      val trig = if (target.lever == "up") "lever_push" else "lever_pull"
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

    when {
      req.requireHold == true -> {
        state = LevelEngine.step(level, state, "button_hold", t, t++).newState
      }
      req.dualPressesEq != null && req.dualPressesEq > 0 -> {
        repeat(req.dualPressesEq) {
          state = LevelEngine.step(level, state, "dual_push", t, t++).newState
        }
      }
      else -> {
        val presses = (req.buttonPressesEq ?: 1).coerceAtLeast(1)
        repeat(presses) {
          state = LevelEngine.step(level, state, "button_push", t, t++).newState
        }
      }
    }

    assertTrue("Condition level B$boardId-L${level.levelNumber} must clear", state.isCleared)
    assertEquals(LampState.GREEN, state.lamp)
  }

  private data class ModuleConfig(
    val swA: Boolean,
    val swB: Boolean,
    val swC: Boolean,
    val lever: String,
    val key: String,
    val conduit: String,
    val lock: String,
    val slider: String,
  )

  private fun findSatisfyingConfig(req: ConditionRequirement): ModuleConfig {
    val swAOpts = if (req.swAEq != null) listOf(req.swAEq) else listOf(false, true)
    val swBOpts = if (req.swBEq != null) listOf(req.swBEq) else listOf(false, true)
    val swCOpts = if (req.swCEq != null) listOf(req.swCEq) else listOf(false, true)
    val leverOpts = if (req.leverEq != null) listOf(req.leverEq) else listOf("up", "down")
    val keyOpts = if (req.keyEq != null) listOf(req.keyEq) else listOf("out", "in")
    val conduitOpts = if (req.conduitEq != null) listOf(req.conduitEq) else listOf("cut", "connected")
    val lockOpts = if (req.lockEq != null) listOf(req.lockEq) else listOf("locked", "unlocked")
    val sliderOpts = if (req.sliderEq != null) listOf(req.sliderEq) else listOf("left", "right")

    for (swA in swAOpts) {
      for (swB in swBOpts) {
        for (swC in swCOpts) {
          for (lever in leverOpts) {
            for (key in keyOpts) {
              for (conduit in conduitOpts) {
                for (lock in lockOpts) {
                  for (slider in sliderOpts) {
                    val cfg = ModuleConfig(swA, swB, swC, lever, key, conduit, lock, slider)
                    if (req.logicClauses.all { clauseMatches(cfg, it) }) {
                      return cfg
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    error("No satisfying module configuration found for $req")
  }

  private fun clauseMatches(cfg: ModuleConfig, clause: LogicClause): Boolean {
    if (clause.checks.isEmpty()) return true
    fun matchCheck(check: ModuleCheck): Boolean {
      val actual =
        when (check.module) {
          "sw_a" -> cfg.swA.toString()
          "sw_b" -> cfg.swB.toString()
          "sw_c" -> cfg.swC.toString()
          "lever" -> cfg.lever
          "key" -> cfg.key
          "conduit" -> cfg.conduit
          "lock" -> cfg.lock
          "slider" -> cfg.slider
          else -> ""
        }
      return actual == check.expected
    }
    return when (clause.operator.uppercase()) {
      "OR" -> clause.checks.any(::matchCheck)
      "XOR" -> clause.checks.count(::matchCheck) % 2 == 1
      "NOT" -> clause.checks.none(::matchCheck)
      else -> clause.checks.all(::matchCheck)
    }
  }

  @Test
  fun translationRateExcludesDefaultSetAndSupportsLanguageSwitch() {
    val data = loadAssets()
    val dict = LevelEngine.buildDefaultDictionary(data, AppLanguage.JA).toMutableMap()
    val discovered = data.wordOrder.toSet()

    val startStats = LevelEngine.computeTranslationStats(data, discovered, dict, AppLanguage.JA)
    assertEquals(0, startStats.translatedCount)
    assertEquals(data.wordOrder.size - data.defaultSetWordIds.size, startStats.totalCount)
    assertEquals(0, startStats.percent)

    val buttonDef = data.words["n_button"]!!
    dict["n_button"] =
      DictionaryEntry(
        wordId = "n_button",
        translation = buttonDef.meaningJa,
        colorTag = WordColorTag.OBJECT,
        source = EntrySource.AD_REVEALED,
      )
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

    assertEquals("you", dict["n_you"]!!.resolvedTranslation(data.words["n_you"], AppLanguage.EN))
    assertEquals("button", dict["n_button"]!!.resolvedTranslation(buttonDef, AppLanguage.EN))
    assertEquals("おす(仮)", dict["v_push"]!!.resolvedTranslation(data.words["v_push"], AppLanguage.EN))

    data.words.values.filter { !it.isDefaultSet }.forEach { w ->
      dict[w.id] = DictionaryEntry(wordId = w.id, translation = w.meaningEn, source = EntrySource.DEBUG_BULK)
    }
    val fullStats = LevelEngine.computeTranslationStats(data, discovered, dict, AppLanguage.EN)
    assertEquals(fullStats.totalCount, fullStats.translatedCount)
    assertEquals(100, fullStats.percent)
  }

  @Test
  fun themePalettesAndPrivacyConfigAreValid() {
    assertTrue(AppConfig.PRIVACY_POLICY_URL.startsWith("https://"))

    val accentColors = mutableSetOf<Long>()
    ThemeMode.entries.forEach { mode ->
      ColorTheme.entries.forEach { colorTheme ->
        val palette = buildKidouPalette(mode, colorTheme)
        assertEquals(mode == ThemeMode.DARK, palette.isDark)
        assertTrue(palette.accentPrimary != palette.background)
        assertTrue(palette.glyph != palette.background)
        if (mode == ThemeMode.DARK) {
          accentColors.add(palette.accentPrimary.value.toLong())
        }
      }
    }
    assertEquals(4, accentColors.size)
  }
}
