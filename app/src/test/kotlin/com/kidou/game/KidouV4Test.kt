package com.kidou.game

import com.kidou.game.config.AppConfig
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
import com.kidou.game.ui.i18n.stringsFor
import com.kidou.game.ui.theme.ColorTheme
import com.kidou.game.ui.theme.ThemeMode
import com.kidou.game.ui.theme.buildKidouPalette
import java.io.File
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidouV4Test {

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
  fun glyphsWordsAndBoardsSchemaAreValidForV4() {
    val data = loadAssets()
    assertTrue("Expected at least 66 glyphs, got ${data.glyphs.size}", data.glyphs.size >= 66)
    assertTrue("Expected at least 66 words, got ${data.words.size}", data.words.size >= 66)

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
    assertEquals(5, data.boardCollection.boards.size)

    val b1 = data.boardCollection.boardById(1)
    val b2 = data.boardCollection.boardById(2)
    val b3 = data.boardCollection.boardById(3)
    val b4 = data.boardCollection.boardById(4)
    val b5 = data.boardCollection.boardById(5)

    assertEquals(25, b1.levels.size)
    assertEquals(25, b2.levels.size)
    assertEquals(25, b3.levels.size)
    assertEquals(25, b4.levels.size)
    assertTrue(b5.levels.isEmpty())

    assertEquals(1, b2.requiredBoardId)
    assertEquals(20, b2.requiredClears)
    assertEquals(2, b3.requiredBoardId)
    assertEquals(20, b3.requiredClears)
    assertEquals(3, b4.requiredBoardId)
    assertEquals(20, b4.requiredClears)
    assertEquals(4, b5.requiredBoardId)
    assertEquals(20, b5.requiredClears)

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
        if (level.tickerEnabled) {
          assertTrue("$tag tickerSequence must be non-empty", level.tickerSequence.isNotEmpty())
          val targets = level.requirements?.tickerTargets.orEmpty()
          assertTrue("$tag tickerTargets must be non-empty", targets.isNotEmpty())
          targets.forEach { tgt ->
            assertTrue("$tag target $tgt missing in tickerSequence", tgt in level.tickerSequence)
          }
        }
      }
    }
  }

  @Test
  fun all100PlayableLevelsCanBeSolvedAndBoardsUnlockAt20Clears() {
    val data = loadAssets()
    val board1 = data.boardCollection.boardById(1)
    val board2 = data.boardCollection.boardById(2)
    val board3 = data.boardCollection.boardById(3)
    val board4 = data.boardCollection.boardById(4)
    val board5 = data.boardCollection.boardById(5)

    val cleared = mutableSetOf<String>()

    fun runBoard(boardId: Int, levels: List<LevelDef>, nextBoard: com.kidou.game.model.BoardDef) {
      levels.forEachIndexed { idx, level ->
        solveLevelAndAssert(boardId = boardId, level = level)
        cleared.add(LevelEngine.levelKey(boardId, level.levelNumber))
        if (idx + 1 < 20) {
          assertFalse(
            "Board ${nextBoard.id} must stay locked at B$boardId ${idx + 1}/25",
            LevelEngine.isBoardUnlocked(nextBoard, cleared),
          )
        } else {
          assertTrue(
            "Board ${nextBoard.id} must unlock at B$boardId ${idx + 1}/25",
            LevelEngine.isBoardUnlocked(nextBoard, cleared),
          )
        }
      }
    }

    runBoard(1, board1.levels, board2)
    runBoard(2, board2.levels, board3)
    runBoard(3, board3.levels, board4)
    runBoard(4, board4.levels, board5)
    assertEquals(100, cleared.size)
  }

  private fun solveLevelAndAssert(boardId: Int, level: LevelDef) {
    var state = LevelEngine.createInitialState(boardId, level)
    assertFalse(state.isCleared)

    if (level.puzzleType == PuzzleType.SEQUENCE) {
      val wrongTrigger =
        if (level.sequenceSteps.first().trigger == "button_push") "key_insert" else "button_push"
      val wrongRes = LevelEngine.step(level, state, wrongTrigger, 1L, 1L)
      assertEquals(LampState.RED, wrongRes.newState.lamp)
      assertEquals(SoundEffect.WARN, wrongRes.sound)
      assertEquals(0, wrongRes.newState.seqStep)
      assertFalse(wrongRes.newState.hasOperated)
      state = wrongRes.newState

      var t = 10L
      level.sequenceSteps.forEach { step ->
        repeat(step.repeatCount) {
          val res = LevelEngine.step(level, state, step.trigger, t, t)
          t += 1L
          state = res.newState
          if (level.memoryMode) {
            assertTrue(state.hasOperated)
          }
        }
      }
      if (level.memoryMode) {
        val resetRes = LevelEngine.step(level, state, "memory_reset", t, t + 1)
        assertFalse(resetRes.newState.hasOperated)
        // Re-solve cleanly after reset test
        state = resetRes.newState
        t += 10L
        level.sequenceSteps.forEach { step ->
          repeat(step.repeatCount) {
            state = LevelEngine.step(level, state, step.trigger, t, t++).newState
          }
        }
      }
      assertTrue("Sequence level B$boardId-L${level.levelNumber} must clear", state.isCleared)
      assertEquals(LampState.GREEN, state.lamp)
      return
    }

    val req: ConditionRequirement = level.requirements ?: ConditionRequirement()
    var t = 1L

    if (req.requireHold == true && req.tickerTargets.isEmpty()) {
      val badPush = LevelEngine.step(level, state, "button_push", t, t++)
      assertEquals(LampState.RED, badPush.newState.lamp)
      assertEquals(SoundEffect.WARN, badPush.sound)
      assertFalse(badPush.newState.isCleared)
      state = badPush.newState
    }

    val target = findSatisfyingConfig(req)

    // Use BFS to solve switch links cleanly
    val switchTriggers =
      findSwitchPath(
        start = SwitchTuple(state.swA, state.swB, state.swC, state.swD),
        goal = SwitchTuple(target.swA, target.swB, target.swC, target.swD),
        links = level.switchLinks,
      )
    switchTriggers.forEach { trig ->
      state = LevelEngine.step(level, state, trig, t, t++).newState
    }

    if (state.route != target.route) {
      state = LevelEngine.step(level, state, "route_${target.route}", t, t++).newState
    }
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

    // Resolve dial & valve targets including dialEqualsValve and dialPlusValveEq
    var targetDial = req.dialEq
    var targetValve = req.valveEq
    if (req.dialEqualsValve == true) {
      if (targetDial != null && targetValve == null) targetValve = targetDial
      else if (targetValve != null && targetDial == null) targetDial = targetValve
      else if (targetDial == null && targetValve == null) {
        targetDial = state.dial
        targetValve = state.dial
      }
    }
    if (req.dialPlusValveEq != null) {
      val sum = req.dialPlusValveEq
      if (targetDial == null && targetValve == null) {
        targetDial = (sum / 2).coerceIn(0, LevelEngine.MAX_NUMERIC_VALUE)
        targetValve = (sum - targetDial).coerceIn(0, LevelEngine.MAX_NUMERIC_VALUE)
      } else if (targetDial != null && targetValve == null) {
        targetValve = (sum - targetDial).coerceIn(0, LevelEngine.MAX_NUMERIC_VALUE)
      } else if (targetValve != null && targetDial == null) {
        targetDial = (sum - targetValve).coerceIn(0, LevelEngine.MAX_NUMERIC_VALUE)
      }
    }

    if (targetDial != null) {
      while (state.dial < targetDial) {
        val trig = if (targetDial - state.dial >= 5) "dial_inc5" else "dial_inc"
        state = LevelEngine.step(level, state, trig, t, t++).newState
      }
      while (state.dial > targetDial) {
        state = LevelEngine.step(level, state, "dial_dec", t, t++).newState
      }
    }
    if (targetValve != null) {
      while (state.valve < targetValve) {
        val trig = if (targetValve - state.valve >= 5) "valve_inc5" else "valve_inc"
        state = LevelEngine.step(level, state, trig, t, t++).newState
      }
      while (state.valve > targetValve) {
        state = LevelEngine.step(level, state, "valve_dec", t, t++).newState
      }
    }

    when {
      req.orderSequence.isNotEmpty() -> {
        // Test wrong first order button triggers soft reset without crash
        val wrongOrder = if (req.orderSequence.first() == 1) "order_2" else "order_1"
        val badRes = LevelEngine.step(level, state, wrongOrder, t, t++)
        assertEquals(LampState.RED, badRes.newState.lamp)
        assertEquals(0, badRes.newState.orderProgress)
        state = badRes.newState

        req.orderSequence.forEach { d ->
          state = LevelEngine.step(level, state, "order_$d", t, t++).newState
        }
      }
      req.tickerTargets.isNotEmpty() -> {
        val submitTrigger =
          if ("dual_button" in level.unlockedControls) "dual_push" else "button_push"
        // Verify missed digit has ZERO penalty / NO RED lamp
        while (state.tickerDigit == req.tickerTargets.first()) {
          state = LevelEngine.advanceTicker(level, state)
        }
        val missRes = LevelEngine.step(level, state, submitTrigger, t, t++)
        assertTrue("Missed ticker digit must not trigger RED failure", missRes.newState.lamp != LampState.RED)
        assertEquals(0, missRes.newState.tickerCaptures)
        state = missRes.newState

        req.tickerTargets.forEach { expectedDigit ->
          var guard = 0
          while (state.tickerDigit != expectedDigit && guard < 20) {
            state = LevelEngine.advanceTicker(level, state)
            guard++
          }
          assertEquals(expectedDigit, state.tickerDigit)
          state = LevelEngine.step(level, state, submitTrigger, t, t++).newState
        }
      }
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

  private data class SwitchTuple(val a: Boolean, val b: Boolean, val c: Boolean, val d: Boolean)

  private fun findSwitchPath(
    start: SwitchTuple,
    goal: SwitchTuple,
    links: Map<String, List<String>>,
  ): List<String> {
    if (start == goal) return emptyList()
    val keys = listOf("sw_a", "sw_b", "sw_c", "sw_d")
    val q = ArrayDeque<Pair<SwitchTuple, List<String>>>()
    val seen = mutableSetOf(start)
    q.add(start to emptyList())
    while (q.isNotEmpty()) {
      val (cur, path) = q.removeFirst()
      for (k in keys) {
        val flip = setOf(k) + links[k].orEmpty()
        val nxt =
          SwitchTuple(
            a = if ("sw_a" in flip) !cur.a else cur.a,
            b = if ("sw_b" in flip) !cur.b else cur.b,
            c = if ("sw_c" in flip) !cur.c else cur.c,
            d = if ("sw_d" in flip) !cur.d else cur.d,
          )
        val newPath = path + "${k}_toggle"
        if (nxt == goal) return newPath
        if (seen.add(nxt)) {
          q.add(nxt to newPath)
        }
      }
    }
    error("Unreachable switch goal $goal from $start with links $links")
  }

  private data class ModuleConfig(
    val swA: Boolean,
    val swB: Boolean,
    val swC: Boolean,
    val swD: Boolean,
    val route: Int,
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
    val swDOpts = if (req.swDEq != null) listOf(req.swDEq) else listOf(false, true)
    val routeOpts = if (req.routeEq != null) listOf(req.routeEq) else listOf(1, 2, 3)
    val leverOpts = if (req.leverEq != null) listOf(req.leverEq) else listOf("up", "down")
    val keyOpts = if (req.keyEq != null) listOf(req.keyEq) else listOf("out", "in")
    val conduitOpts = if (req.conduitEq != null) listOf(req.conduitEq) else listOf("cut", "connected")
    val lockOpts = if (req.lockEq != null) listOf(req.lockEq) else listOf("locked", "unlocked")
    val sliderOpts = if (req.sliderEq != null) listOf(req.sliderEq) else listOf("left", "right")

    for (swA in swAOpts) {
      for (swB in swBOpts) {
        for (swC in swCOpts) {
          for (swD in swDOpts) {
            for (route in routeOpts) {
              for (lever in leverOpts) {
                for (key in keyOpts) {
                  for (conduit in conduitOpts) {
                    for (lock in lockOpts) {
                      for (slider in sliderOpts) {
                        val cfg = ModuleConfig(swA, swB, swC, swD, route, lever, key, conduit, lock, slider)
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
          "sw_d" -> cfg.swD.toString()
          "route" -> cfg.route.toString()
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
  fun themeNamesPalettesCreditsAndPrivacyAreValidForV4() {
    assertTrue(AppConfig.PRIVACY_POLICY_URL.startsWith("https://"))

    assertEquals(listOf("デフォルト", "グリーン", "ブルー", "レッド"), ColorTheme.entries.map { it.label(AppLanguage.JA) })
    assertEquals(listOf("Default", "Green", "Blue", "Red"), ColorTheme.entries.map { it.label(AppLanguage.EN) })

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

    listOf(AppLanguage.JA, AppLanguage.EN).forEach { lang ->
      val credits = stringsFor(lang).creditsBody
      assertTrue(credits.contains("Purrple Cat"))
      assertTrue(credits.contains("CC BY 4.0"))
      assertTrue(credits.contains("DotGothic16"))
      assertTrue(credits.contains("OFL-1.1"))
    }
  }
}
