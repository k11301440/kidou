package com.kidou.game

import com.kidou.game.engine.GameDataLoader
import com.kidou.game.engine.MachineEngine
import com.kidou.game.model.LampState
import com.kidou.game.model.LoadedGameData
import com.kidou.game.model.SoundEffect
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MachineEngineTest {

  private fun loadAssets(): LoadedGameData {
    val candidates =
      listOf(
        File("src/main/assets"),
        File("app/src/main/assets"),
      )
    val assetsDir =
      candidates.firstOrNull { it.exists() && it.isDirectory }
        ?: error("Could not locate assets directory from ${File(".").absolutePath}")

    return File(assetsDir, "glyphs.json").inputStream().use { g ->
      File(assetsDir, "words.json").inputStream().use { w ->
        File(assetsDir, "machine.json").inputStream().use { m ->
          GameDataLoader.loadFromStreams(g, w, m)
        }
      }
    }
  }

  @Test
  fun glyphsAreAllValid12x12Grids() {
    val data = loadAssets()
    assertTrue("Expected at least 30 glyphs", data.glyphs.size >= 30)
    data.glyphs.forEach { (id, grid) ->
      assertEquals("Glyph $id must have 12 rows", 12, grid.rows.size)
      grid.rows.forEachIndexed { rowIdx, row ->
        assertEquals("Glyph $id row $rowIdx must have length 12", 12, row.length)
        assertTrue(
          "Glyph $id row $rowIdx must contain only '.' and '#'",
          row.all { it == '.' || it == '#' },
        )
      }
    }
  }

  @Test
  fun allMachineReferencesExistInWordsAndGlyphs() {
    val data = loadAssets()
    data.words.values.forEach { word ->
      assertTrue("Missing glyph ${word.glyphId} for word ${word.id}", data.glyphs.containsKey(word.glyphId))
    }

    val referenced = mutableSetOf<String>()
    fun checkSlots(slots: List<List<String>>, context: String) {
      slots.flatten().forEach { token ->
        if (!token.startsWith("\$")) {
          assertTrue("Unknown word '$token' in $context", data.words.containsKey(token))
          referenced.add(token)
        }
      }
    }

    checkSlots(data.machine.machineNameSlots, "machineNameSlots")
    data.machine.phases.forEach { phase ->
      phase.plates.forEachIndexed { i, plate -> checkSlots(plate, "phase ${phase.phase} plate $i") }
      checkSlots(phase.initialState.primaryMessage, "phase ${phase.phase} init primary")
      checkSlots(phase.initialState.secondaryMessage, "phase ${phase.phase} init secondary")
      phase.rules.forEachIndexed { i, rule ->
        checkSlots(rule.effects.primaryMessage, "phase ${phase.phase} rule $i primary")
        checkSlots(rule.effects.secondaryMessage, "phase ${phase.phase} rule $i secondary")
      }
    }
    assertTrue("Expected at least 30 words in words.json", data.words.size >= 30)
    assertTrue("Expected core words referenced", referenced.contains("n_button"))
  }

  @Test
  fun fullPlaythroughReachesActivationAndHandlesWarnings() {
    val data = loadAssets()
    var state = MachineEngine.createInitialState(data.machine)
    assertEquals(1, state.currentPhase)
    assertFalse(state.isFullyActivated)

    // Phase 1: push button twice
    var res = MachineEngine.step(data.machine, state, "button_push", 1L, 1L)
    state = res.newState
    assertEquals(1, state.currentPhase)
    assertEquals(LampState.AMBER, state.lamp)

    res = MachineEngine.step(data.machine, state, "button_push", 2L, 2L)
    state = res.newState
    assertEquals(2, state.currentPhase)
    assertEquals(2, state.maxUnlockedPhase)
    assertEquals(LampState.GREEN, state.lamp)
    assertEquals(SoundEffect.PHASE_CLEAR, res.sound)

    // Phase 2: warning when lever up, then pull lever + push button
    res = MachineEngine.step(data.machine, state, "button_push", 3L, 3L)
    state = res.newState
    assertEquals(2, state.currentPhase)
    assertEquals(LampState.RED, state.lamp)
    assertEquals(SoundEffect.WARN, res.sound)

    res = MachineEngine.step(data.machine, state, "lever_pull", 4L, 4L)
    state = res.newState
    assertEquals("down", state.lever)

    res = MachineEngine.step(data.machine, state, "button_push", 5L, 5L)
    state = res.newState
    assertEquals(3, state.currentPhase)
    assertEquals(3, state.maxUnlockedPhase)
    assertEquals(LampState.GREEN, state.lamp)

    // Phase 3: warning for dial < 4, turn dial to 4, warning for lever down, lever up, push button
    res = MachineEngine.step(data.machine, state, "button_push", 6L, 6L)
    state = res.newState
    assertEquals(LampState.RED, state.lamp)

    repeat(3) { idx ->
      res = MachineEngine.step(data.machine, state, "dial_turn", 10L + idx, 10L + idx)
      state = res.newState
    }
    assertEquals(4, state.dial)

    res = MachineEngine.step(data.machine, state, "button_push", 15L, 15L)
    state = res.newState
    assertEquals(LampState.RED, state.lamp)
    assertEquals(3, state.currentPhase)

    res = MachineEngine.step(data.machine, state, "lever_push", 16L, 16L)
    state = res.newState
    assertEquals("up", state.lever)

    res = MachineEngine.step(data.machine, state, "button_push", 17L, 17L)
    state = res.newState
    assertEquals(4, state.currentPhase)
    assertEquals(4, state.maxUnlockedPhase)

    // Phase 4: out-of-order action triggers fault reset
    res = MachineEngine.step(data.machine, state, "button_push", 18L, 18L)
    state = res.newState
    assertEquals(LampState.RED, state.lamp)
    assertEquals(0, state.seqStep)

    // Phase 4 proper sequence: key_insert -> dial_turn x3 -> lever_pull -> key_remove
    res = MachineEngine.step(data.machine, state, "key_insert", 19L, 19L)
    state = res.newState
    assertEquals(1, state.seqStep)

    repeat(3) { idx ->
      res = MachineEngine.step(data.machine, state, "dial_turn", 20L + idx, 20L + idx)
      state = res.newState
    }
    assertEquals(2, state.seqStep)

    res = MachineEngine.step(data.machine, state, "lever_pull", 25L, 25L)
    state = res.newState
    assertEquals(3, state.seqStep)

    res = MachineEngine.step(data.machine, state, "key_remove", 26L, 26L)
    state = res.newState
    assertEquals(5, state.currentPhase)
    assertEquals(5, state.maxUnlockedPhase)
    assertEquals(LampState.GREEN, state.lamp)

    // Phase 5: align conduit, key, dial=5, lever=up, push twice
    res = MachineEngine.step(data.machine, state, "button_push", 27L, 27L)
    state = res.newState
    assertEquals(LampState.RED, state.lamp)

    state = MachineEngine.step(data.machine, state, "conduit_connect", 28L, 28L).newState
    state = MachineEngine.step(data.machine, state, "key_insert", 29L, 29L).newState
    repeat(4) { idx ->
      state = MachineEngine.step(data.machine, state, "dial_turn", 30L + idx, 30L + idx).newState
    }
    assertEquals(5, state.dial)
    state = MachineEngine.step(data.machine, state, "lever_push", 35L, 35L).newState

    res = MachineEngine.step(data.machine, state, "button_push", 36L, 36L)
    state = res.newState
    assertEquals(LampState.AMBER, state.lamp)
    assertFalse(state.isFullyActivated)

    res = MachineEngine.step(data.machine, state, "button_push", 37L, 37L)
    state = res.newState
    assertEquals(LampState.GREEN, state.lamp)
    assertEquals(SoundEffect.CORE_ACTIVATE, res.sound)
    assertTrue(state.isFullyActivated)
    assertNotNull(res.logEntry)

    // Switching back to unlocked phase preserves maxUnlockedPhase & activation
    val switched = MachineEngine.switchToPhase(data.machine, state, 2)
    assertEquals(2, switched.currentPhase)
    assertEquals(5, switched.maxUnlockedPhase)
    assertTrue(switched.isFullyActivated)
  }

  @Test
  fun collectScreenWordIdsScalesByPhase() {
    val data = loadAssets()
    val state1 = MachineEngine.createInitialState(data.machine)
    val words1 = MachineEngine.collectScreenWordIds(data.machine, state1)
    assertTrue(words1.contains("n_button"))
    assertTrue(words1.contains("v_push"))
    assertFalse(words1.contains("n_lever"))
    assertFalse(words1.contains("n_conduit"))
  }
}
