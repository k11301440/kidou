package com.kidou.game

import com.kidou.game.engine.GameDataLoader
import com.kidou.game.engine.LevelEngine
import com.kidou.game.engine.RtaArchetype
import com.kidou.game.engine.RtaGenConfig
import com.kidou.game.engine.RtaLevelGenerator
import com.kidou.game.model.LoadedGameData
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RtaLevelGeneratorTest {

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

  private fun assertGrammarAndVocabulary(
    data: LoadedGameData,
    level: com.kidou.game.model.LevelDef,
  ) {
    assertTrue("Plates must not be empty", level.plates.isNotEmpty())
    assertTrue(
      "All words must belong to words.json",
      RtaLevelGenerator.usesOnlyValidWords(level, data.words.keys),
    )
    level.plates.forEach { plate ->
      assertTrue("Each plate must have >= 2 slots", plate.size >= 2)
      plate.forEach { slot ->
        assertTrue("Slot must be non-empty", slot.isNotEmpty())
        slot.forEach { wordId ->
          assertTrue("Unknown vocabulary word '$wordId'", data.words.containsKey(wordId))
        }
      }
      // First slot should start with an Object/Switch/Route/Digit ordinal/Logic prefix
      val firstWordPos = data.words[plate.first().first()]!!.pos
      assertTrue(
        "Unexpected head word pos '$firstWordPos' in plate $plate",
        firstWordPos == "noun" || firstWordPos == "digit",
      )
      // At least one verb or negation/condition/digit marker appears cleanly
      val poses = plate.flatten().map { data.words[it]!!.pos }.toSet()
      assertTrue(
        "Plate $plate must contain grammatical verb/modifier predicate",
        poses.contains("verb") || poses.contains("modifier") || poses.contains("digit"),
      )
    }
  }

  @Test
  fun multiSeedRunsPassSimulationSolvabilityAndGrammarChecks() {
    val data = loadAssets()
    val validWords = data.words.keys

    // 250 seeds x 10 machines = 2,500 reverse-engineered generated levels
    for (seed in 1L..250L) {
      val run = RtaLevelGenerator.generateRun(seed = seed, count = 10, validWordIds = validWords)
      assertEquals("Seed $seed must produce 10 levels", 10, run.size)
      run.forEachIndexed { idx, gen ->
        assertEquals(idx + 1, gen.levelDef.levelNumber)
        val init = LevelEngine.createInitialState(0, gen.levelDef)
        assertFalse("Seed $seed level ${idx + 1} must not start cleared", init.isCleared)
        assertTrue(
          "Seed $seed level ${idx + 1} (${gen.archetype}) failed simulation solvability",
          RtaLevelGenerator.verifySolvable(gen),
        )
        assertGrammarAndVocabulary(data, gen.levelDef)
      }
    }
  }

  @Test
  fun allArchetypesCanBeGeneratedAndSolvedIndependently() {
    val data = loadAssets()
    val validWords = data.words.keys

    RtaArchetype.entries.forEach { archetype ->
      val cfg = RtaGenConfig(minModules = 2, maxModules = 5, minConditions = 1, maxConditions = 4, allowedArchetypes = setOf(archetype))
      for (seed in 1000L..1020L) {
        val run = RtaLevelGenerator.generateRun(seed = seed, count = 5, validWordIds = validWords, config = cfg)
        assertEquals(5, run.size)
        run.forEach { gen ->
          assertEquals(archetype, gen.archetype)
          assertTrue(
            "Archetype $archetype seed $seed failed simulation",
            RtaLevelGenerator.verifySolvable(gen),
          )
          assertGrammarAndVocabulary(data, gen.levelDef)
        }
      }
    }
  }

  @Test
  fun extremeModuleAndConditionBoundsDoNotBreak() {
    val data = loadAssets()
    val validWords = data.words.keys

    val minExtreme = RtaGenConfig(minModules = 1, maxModules = 1, minConditions = 1, maxConditions = 1)
    for (seed in 500L..530L) {
      val run = RtaLevelGenerator.generateRun(seed = seed, count = 10, validWordIds = validWords, config = minExtreme)
      assertEquals(10, run.size)
      run.forEach { gen ->
        assertTrue("Min-extreme must stay within 1 module", gen.levelDef.unlockedControls.size <= 1)
        assertTrue("Min-extreme seed $seed failed simulation", RtaLevelGenerator.verifySolvable(gen))
        assertGrammarAndVocabulary(data, gen.levelDef)
      }
    }

    val maxExtreme = RtaGenConfig(minModules = 5, maxModules = 5, minConditions = 4, maxConditions = 4)
    for (seed in 800L..840L) {
      val run = RtaLevelGenerator.generateRun(seed = seed, count = 10, validWordIds = validWords, config = maxExtreme)
      assertEquals(10, run.size)
      run.forEach { gen ->
        assertTrue("Max-extreme must have unlocked controls", gen.levelDef.unlockedControls.isNotEmpty())
        assertTrue("Max-extreme seed $seed failed simulation", RtaLevelGenerator.verifySolvable(gen))
        assertGrammarAndVocabulary(data, gen.levelDef)
      }
    }
  }
}
