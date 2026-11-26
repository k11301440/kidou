package com.kidou.game

import com.kidou.game.config.AppConfig
import com.kidou.game.config.PlayGamesConfig
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KidouV5Test {

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
  fun glyphsWordsAndBoardsSchemaAreValidForV5() {
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

    fun checkSlots(slots: List<List<String>>, ctx: String) {
      slots.forEach { slot ->
        slot.forEach { wordId ->
          assertTrue("Unknown wordId '$wordId' in $ctx", data.words.containsKey(wordId))
        }
      }
    }

    data.boardCollection.guideLines.forEachIndexed { idx, line -> checkSlots(line, "guideLines[$idx]") }
    assertEquals(5, data.boardCollection.boards.size)

    for (boardId in 1..5) {
      val b = data.boardCollection.boardById(boardId)
      assertEquals("Board $boardId must have 25 levels", 25, b.levels.size)
      assertTrue("Board $boardId must have story lines", b.storyLines.isNotEmpty())
      if (boardId > 1) {
        assertEquals(boardId - 1, b.requiredBoardId)
        assertEquals(20, b.requiredClears)
      }
    }

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
  fun all125LevelsCanBeSolvedBoardsUnlockAt20AndRtaUnlocksAtFinalClear() {
    val data = loadAssets()
    val cleared = mutableSetOf<String>()
    assertFalse(LevelEngine.isFinalLevelCleared(cleared))

    for (boardId in 1..5) {
      val board = data.boardCollection.boardById(boardId)
      val nextBoard = data.boardCollection.boards.firstOrNull { it.requiredBoardId == boardId }
      board.levels.forEachIndexed { idx, level ->
        solveLevelAndAssert(boardId = boardId, level = level)
        cleared.add(LevelEngine.levelKey(boardId, level.levelNumber))
        if (nextBoard != null) {
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
        val expectedFinal = (boardId == 5 && level.levelNumber == 25)
        assertEquals(expectedFinal, LevelEngine.isFinalLevelCleared(cleared))
      }
    }

    assertEquals(125, cleared.size)
    assertTrue(LevelEngine.isFinalLevelCleared(cleared))
  }

  @Test
  fun vocabularyContextThicknessCoversKeyVerbsAndModifiers() {
    val data = loadAssets()
    val counts = mutableMapOf<String, Int>()
    for (board in data.boardCollection.boards) {
      board.storyLines.flatten().flatten().forEach { counts[it] = (counts[it] ?: 0) + 1 }
      board.levels.forEach { lv ->
        lv.plates.flatten().flatten().forEach { counts[it] = (counts[it] ?: 0) + 1 }
        lv.initialState.primaryMessage.flatten().forEach { counts[it] = (counts[it] ?: 0) + 1 }
        lv.initialState.secondaryMessage.flatten().forEach { counts[it] = (counts[it] ?: 0) + 1 }
      }
    }

    val thickenedVerbs =
      listOf("v_turn", "v_decrease", "v_increase", "v_up", "v_pull", "v_remove", "v_cut", "v_left", "m_same", "v_hide", "n_lamp", "v_close", "adj_danger")
    thickenedVerbs.forEach { wordId ->
      val freq = counts[wordId] ?: 0
      assertTrue("Expected thickened context >= 6 for '$wordId', got $freq", freq >= 6)
    }
  }

  @Test
  fun translationRateAndPlayGamesConfigAndPrivacyUrlAreValidForV5() {
    val data = loadAssets()
    val dict = LevelEngine.buildDefaultDictionary(data, AppLanguage.JA).toMutableMap()
    val discovered = data.wordOrder.toSet()

    val startStats = LevelEngine.computeTranslationStats(data, discovered, dict, AppLanguage.JA)
    assertEquals(0, startStats.translatedCount)
    assertEquals(data.wordOrder.size - data.defaultSetWordIds.size, startStats.totalCount)

    dict["n_button"] = DictionaryEntry("n_button", "ボタン", WordColorTag.OBJECT, source = EntrySource.AD_REVEALED)
    val midStats = LevelEngine.computeTranslationStats(data, discovered, dict, AppLanguage.JA)
    assertEquals(1, midStats.translatedCount)

    assertEquals("https://k11301440.github.io/kidou/privacy-policy.html", AppConfig.PRIVACY_POLICY_URL)
    assertFalse(PlayGamesConfig.isAppIdConfigured(PlayGamesConfig.APP_ID_PLACEHOLDER))
    assertTrue(PlayGamesConfig.isAppIdConfigured("123456789012"))
    com.kidou.game.playgames.KidouAchievement.entries.forEach { ach ->
      assertNull(PlayGamesConfig.configuredAchievementId(ach))
    }

    assertEquals(listOf("デフォルト", "グリーン", "ブルー", "レッド"), ColorTheme.entries.map { it.label(AppLanguage.JA) })
    assertEquals(listOf("Default", "Green", "Blue", "Red"), ColorTheme.entries.map { it.label(AppLanguage.EN) })
    ThemeMode.entries.forEach { mode ->
      ColorTheme.entries.forEach { ct ->
        val p = buildKidouPalette(mode, ct)
        assertTrue(p.accentPrimary != p.background)
        assertTrue(p.glyph != p.background)
      }
    }

    listOf(AppLanguage.JA, AppLanguage.EN).forEach { lang ->
      val credits = stringsFor(lang).creditsBody
      assertTrue(credits.contains("Purrple Cat"))
      assertTrue(credits.contains("CC BY 4.0"))
      assertTrue(credits.contains("DotGothic16"))
      assertTrue(credits.contains("OFL-1.1"))
    }
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
      state = wrongRes.newState

      var t = 10L
      level.sequenceSteps.forEach { step ->
        repeat(step.repeatCount) {
          state = LevelEngine.step(level, state, step.trigger, t, t++).newState
        }
      }
      if (level.memoryMode) {
        val resetRes = LevelEngine.step(level, state, "memory_reset", t, t + 1)
        assertFalse(resetRes.newState.hasOperated)
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
    findSwitchPath(
      start = SwitchTuple(state.swA, state.swB, state.swC, state.swD),
      goal = SwitchTuple(target.swA, target.swB, target.swC, target.swD),
      links = level.switchLinks,
    ).forEach { trig ->
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

    var targetDial = req.dialEq
    var targetValve = req.valveEq
    if (req.dialEqualsValve == true) {
      if (targetDial != null && targetValve == null) targetValve = targetDial
      else if (targetValve != null && targetDial == null) targetDial = targetValve
      else if (targetDial == null && targetValve == null) {
        if (req.dialPlusValveEq != null) {
          targetDial = req.dialPlusValveEq / 2
          targetValve = req.dialPlusValveEq - targetDial
        } else {
          targetDial = state.dial
          targetValve = state.dial
        }
      }
    } else if (req.dialPlusValveEq != null) {
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
          when {
            req.requireHold == true -> "button_hold"
            "dual_button" in level.unlockedControls -> "dual_push"
            else -> "button_push"
          }
        while (state.tickerDigit == req.tickerTargets.first()) {
          state = LevelEngine.advanceTicker(level, state)
        }
        val missRes = LevelEngine.step(level, state, submitTrigger, t, t++)
        assertTrue("Missed ticker digit must not fail with RED", missRes.newState.lamp != LampState.RED)
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
        if (seen.add(nxt)) q.add(nxt to newPath)
      }
    }
    error("Unreachable switch goal $goal from $start with links $links")
  }

  private data class ModuleConfig(
    val swA: Boolean, val swB: Boolean, val swC: Boolean, val swD: Boolean,
    val route: Int, val lever: String, val key: String,
    val conduit: String, val lock: String, val slider: String,
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

    for (swA in swAOpts) for (swB in swBOpts) for (swC in swCOpts) for (swD in swDOpts)
      for (route in routeOpts) for (lever in leverOpts) for (key in keyOpts)
        for (conduit in conduitOpts) for (lock in lockOpts) for (slider in sliderOpts) {
          val cfg = ModuleConfig(swA, swB, swC, swD, route, lever, key, conduit, lock, slider)
          if (req.logicClauses.all { clauseMatches(cfg, it) }) return cfg
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
}
