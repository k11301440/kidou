package com.kidou.game.engine

import com.kidou.game.model.ConditionRequirement
import com.kidou.game.model.LampState
import com.kidou.game.model.LevelDef
import com.kidou.game.model.LevelInitialState
import com.kidou.game.model.LogicClause
import com.kidou.game.model.ModuleCheck
import com.kidou.game.model.PuzzleType
import com.kidou.game.model.SequenceStepDef
import kotlin.random.Random

enum class RtaArchetype {
  STANDARD_CONDITION,
  LOGIC_SWITCHES,
  LINKED_SWITCHES,
  ORDER_PAD,
  ARITHMETIC_DIAL_VALVE,
  SIGNAL_TICKER,
  SEQUENCE,
}

data class RtaGenConfig(
  val minModules: Int = 2,
  val maxModules: Int = 5,
  val minConditions: Int = 1,
  val maxConditions: Int = 4,
  val allowedArchetypes: Set<RtaArchetype> = RtaArchetype.entries.toSet(),
)

data class GeneratedRtaLevel(
  val levelDef: LevelDef,
  val solutionTriggers: List<String>,
  val archetype: RtaArchetype,
)

/**
 * Reverse-engineering procedural level generator for RTA mode (§2):
 * Decides the goal state and exact solution sequence first, derives a non-trivial
 * initial state and grammatical glyph plates from existing vocabulary, and accepts
 * only levels verified solvable by simulating [LevelEngine].
 */
object RtaLevelGenerator {

  fun generateRun(
    seed: Long,
    count: Int = 10,
    validWordIds: Set<String>,
    config: RtaGenConfig = RtaGenConfig(),
  ): List<GeneratedRtaLevel> {
    if (count <= 0) return emptyList()
    val rng = Random(seed)
    val minMod = config.minModules.coerceIn(1, 6)
    val maxMod = config.maxModules.coerceIn(minMod, 6)
    val minCond = config.minConditions.coerceIn(1, 5)
    val maxCond = config.maxConditions.coerceIn(minCond, 5)
    val normalized =
      config.copy(
        minModules = minMod,
        maxModules = maxMod,
        minConditions = minCond,
        maxConditions = maxCond,
        allowedArchetypes = config.allowedArchetypes.ifEmpty { RtaArchetype.entries.toSet() },
      )

    val schedule = buildArchetypeSchedule(rng, count, normalized)
    return (1..count).map { idx ->
      val preferred = schedule[idx - 1]
      generateSingleLevel(
        levelNumber = idx,
        rng = Random(rng.nextLong()),
        validWordIds = validWordIds,
        preferredArchetype = preferred,
        config = normalized,
      )
    }
  }

  private fun buildArchetypeSchedule(
    rng: Random,
    count: Int,
    config: RtaGenConfig,
  ): List<RtaArchetype> {
    val eligible =
      config.allowedArchetypes.filter { archetype ->
        when (archetype) {
          RtaArchetype.STANDARD_CONDITION -> true
          RtaArchetype.ORDER_PAD -> true
          RtaArchetype.SIGNAL_TICKER -> true
          RtaArchetype.SEQUENCE -> config.maxModules >= 2
          RtaArchetype.LOGIC_SWITCHES -> config.maxModules >= 3
          RtaArchetype.LINKED_SWITCHES -> config.maxModules >= 3
          RtaArchetype.ARITHMETIC_DIAL_VALVE -> config.maxModules >= 3 && config.maxConditions >= 1
        }
      }.ifEmpty { listOf(RtaArchetype.STANDARD_CONDITION) }

    val pool = eligible.shuffled(rng)
    return List(count) { i -> pool[i % pool.size] }
  }

  private fun generateSingleLevel(
    levelNumber: Int,
    rng: Random,
    validWordIds: Set<String>,
    preferredArchetype: RtaArchetype,
    config: RtaGenConfig,
  ): GeneratedRtaLevel {
    val candidates = listOf(preferredArchetype) + RtaArchetype.entries.filter { it != preferredArchetype }
    for (attempt in 0 until 24) {
      val archetype = if (attempt < 16) preferredArchetype else candidates[(attempt - 16) % candidates.size]
      val candidate = buildCandidate(levelNumber, rng, archetype, config)
      if (candidate != null &&
        usesOnlyValidWords(candidate.levelDef, validWordIds) &&
        verifySolvable(candidate)
      ) {
        return candidate
      }
    }
    // Deterministic guaranteed fallback respecting bounds
    val fallback = buildDeterministicFallback(levelNumber, config)
    check(usesOnlyValidWords(fallback.levelDef, validWordIds) && verifySolvable(fallback)) {
      "Fallback RTA level failed validation at levelNumber=$levelNumber"
    }
    return fallback
  }

  private fun buildCandidate(
    levelNumber: Int,
    rng: Random,
    archetype: RtaArchetype,
    config: RtaGenConfig,
  ): GeneratedRtaLevel? {
    return when (archetype) {
      RtaArchetype.STANDARD_CONDITION -> buildStandardCondition(levelNumber, rng, config)
      RtaArchetype.LOGIC_SWITCHES -> buildLogicSwitches(levelNumber, rng, config)
      RtaArchetype.LINKED_SWITCHES -> buildLinkedSwitches(levelNumber, rng, config)
      RtaArchetype.ORDER_PAD -> buildOrderPad(levelNumber, rng, config)
      RtaArchetype.ARITHMETIC_DIAL_VALVE -> buildArithmeticDialValve(levelNumber, rng, config)
      RtaArchetype.SIGNAL_TICKER -> buildSignalTicker(levelNumber, rng, config)
      RtaArchetype.SEQUENCE -> buildSequence(levelNumber, rng, config)
    }
  }

  private fun buildStandardCondition(
    levelNumber: Int,
    rng: Random,
    config: RtaGenConfig,
  ): GeneratedRtaLevel {
    if (config.maxModules == 1) {
      return buildDeterministicFallback(levelNumber, config)
    }
    val auxBudget = (config.maxModules - 1).coerceIn(1, 5)
    val minAux = (config.minModules - 1).coerceIn(1, auxBudget)
    val condLimit = config.maxConditions.coerceAtMost(auxBudget)
    val condLow = config.minConditions.coerceAtMost(condLimit)
    val numAux = rng.nextInt(maxOf(minAux, condLow), condLimit + 1)

    val allAux = listOf("conduit", "lock", "key", "lever", "slider", "route", "dial", "valve")
    val picked = allAux.shuffled(rng).take(numAux)
    val submitType = listOf("button", "hold_button", "dual_button").random(rng)
    val unlocked = picked + submitType

    val plates = mutableListOf<List<List<String>>>()
    val triggers = mutableListOf<String>()

    var leverInit = "up"; var leverGoal: String? = null
    var keyInit = "out"; var keyGoal: String? = null
    var conduitInit = "cut"; var conduitGoal: String? = null
    var lockInit = "locked"; var lockGoal: String? = null
    var sliderInit = "left"; var sliderGoal: String? = null
    var routeInit = 1; var routeGoal: Int? = null
    var dialInit = 0; var dialGoal: Int? = null
    var valveInit = 0; var valveGoal: Int? = null

    picked.forEach { mod ->
      when (mod) {
        "conduit" -> {
          val goalConnected = rng.nextBoolean()
          if (goalConnected) {
            conduitInit = "cut"; conduitGoal = "connected"
            plates += listOf(listOf("n_conduit"), listOf("v_connect"))
            triggers += "conduit_connect"
          } else {
            conduitInit = "connected"; conduitGoal = "cut"
            plates += listOf(listOf("n_conduit"), listOf("v_cut"))
            triggers += "conduit_cut"
          }
        }
        "lock" -> {
          val goalUnlocked = rng.nextBoolean()
          if (goalUnlocked) {
            lockInit = "locked"; lockGoal = "unlocked"
            plates += listOf(listOf("n_lock"), listOf("v_unlock"))
            triggers += "lock_unlock"
          } else {
            lockInit = "unlocked"; lockGoal = "locked"
            plates += listOf(listOf("n_lock"), listOf("v_lock"))
            triggers += "lock_lock"
          }
        }
        "key" -> {
          val goalIn = rng.nextBoolean()
          if (goalIn) {
            keyInit = "out"; keyGoal = "in"
            plates += listOf(listOf("n_key"), listOf("v_insert"))
            triggers += "key_insert"
          } else {
            keyInit = "in"; keyGoal = "out"
            plates += listOf(listOf("n_key"), listOf("v_remove"))
            triggers += "key_remove"
          }
        }
        "lever" -> {
          val goalDown = rng.nextBoolean()
          if (goalDown) {
            leverInit = "up"; leverGoal = "down"
            plates += listOf(listOf("n_lever"), listOf("v_down"))
            triggers += "lever_pull"
          } else {
            leverInit = "down"; leverGoal = "up"
            plates += listOf(listOf("n_lever"), listOf("v_up"))
            triggers += "lever_push"
          }
        }
        "slider" -> {
          val goalRight = rng.nextBoolean()
          if (goalRight) {
            sliderInit = "left"; sliderGoal = "right"
            plates += listOf(listOf("n_slider"), listOf("v_swipe"), listOf("v_right"))
            triggers += "slider_right"
          } else {
            sliderInit = "right"; sliderGoal = "left"
            plates += listOf(listOf("n_slider"), listOf("v_swipe"), listOf("v_left"))
            triggers += "slider_left"
          }
        }
        "route" -> {
          val g = rng.nextInt(1, 4)
          val init = ((g % 3) + 1)
          routeInit = init; routeGoal = g
          plates += listOf(listOf("n_route"), Base5.toDigitWordIds(g), listOf("v_match"))
          triggers += "route_$g"
        }
        "dial" -> {
          val g = rng.nextInt(3, 21)
          val init = if (rng.nextBoolean()) (g - rng.nextInt(2, 6)).coerceAtLeast(0) else (g + rng.nextInt(2, 6)).coerceAtMost(24)
          val safeInit = if (init == g) (g + 2).coerceAtMost(24) else init
          dialInit = safeInit; dialGoal = g
          plates += listOf(listOf("n_dial"), listOf("v_match"), Base5.toDigitWordIds(g))
          addNumericAdjustTriggers(triggers, "dial", safeInit, g)
        }
        "valve" -> {
          val g = rng.nextInt(3, 21)
          val init = if (rng.nextBoolean()) (g - rng.nextInt(2, 6)).coerceAtLeast(0) else (g + rng.nextInt(2, 6)).coerceAtMost(24)
          val safeInit = if (init == g) (g + 2).coerceAtMost(24) else init
          valveInit = safeInit; valveGoal = g
          plates += listOf(listOf("n_valve"), listOf("v_match"), Base5.toDigitWordIds(g))
          addNumericAdjustTriggers(triggers, "valve", safeInit, g)
        }
      }
    }

    val req =
      when (submitType) {
        "hold_button" -> {
          plates += listOf(listOf("n_button"), listOf("v_hold"))
          triggers += "button_hold"
          ConditionRequirement(
            leverEq = leverGoal, keyEq = keyGoal, conduitEq = conduitGoal, lockEq = lockGoal,
            dialEq = dialGoal, valveEq = valveGoal, sliderEq = sliderGoal, routeEq = routeGoal,
            requireHold = true,
          )
        }
        "dual_button" -> {
          val presses = rng.nextInt(1, 3)
          plates += listOf(listOf("n_button", "d_2"), listOf("m_together"), listOf("v_push"), Base5.toDigitWordIds(presses) + "m_times")
          repeat(presses) { triggers += "dual_push" }
          ConditionRequirement(
            leverEq = leverGoal, keyEq = keyGoal, conduitEq = conduitGoal, lockEq = lockGoal,
            dialEq = dialGoal, valveEq = valveGoal, sliderEq = sliderGoal, routeEq = routeGoal,
            dualPressesEq = presses,
          )
        }
        else -> {
          val presses = rng.nextInt(1, 3)
          plates += listOf(listOf("n_button"), listOf("v_push"), Base5.toDigitWordIds(presses) + "m_times")
          repeat(presses) { triggers += "button_push" }
          ConditionRequirement(
            leverEq = leverGoal, keyEq = keyGoal, conduitEq = conduitGoal, lockEq = lockGoal,
            dialEq = dialGoal, valveEq = valveGoal, sliderEq = sliderGoal, routeEq = routeGoal,
            buttonPressesEq = presses,
          )
        }
      }

    val def =
      LevelDef(
        levelNumber = levelNumber,
        puzzleType = PuzzleType.CONDITION,
        unlockedControls = unlocked,
        plates = plates,
        initialState =
          LevelInitialState(
            lever = leverInit, key = keyInit, conduit = conduitInit, lock = lockInit,
            dial = dialInit, valve = valveInit, slider = sliderInit, route = routeInit,
            lamp = "amber",
            primaryMessage = listOf(listOf("n_machine"), listOf("v_stop")),
          ),
        requirements = req,
        completionPrimary = listOf(listOf("n_machine"), listOf("v_activate")),
        completionSecondary = listOf(listOf("adj_complete")),
      )
    return GeneratedRtaLevel(def, triggers, RtaArchetype.STANDARD_CONDITION)
  }

  private fun buildLogicSwitches(
    levelNumber: Int,
    rng: Random,
    config: RtaGenConfig,
  ): GeneratedRtaLevel? {
    val maxSwitches = (config.maxModules - 1).coerceIn(0, 4)
    if (maxSwitches < 2) return null
    val numSw = rng.nextInt(maxOf(2, (config.minModules - 1).coerceIn(2, maxSwitches)), maxSwitches + 1)
    val keys = listOf("sw_a", "sw_b", "sw_c", "sw_d").take(numSw)

    // Choose target booleans first, with XOR between sw_a and sw_b guaranteed
    val goalSwA = rng.nextBoolean()
    val goalSwB = !goalSwA
    val goalSwC = if (numSw >= 3) true else false
    val goalSwD = if (numSw >= 4) rng.nextBoolean() else false

    val goalTuple = listOf(goalSwA, goalSwB, goalSwC, goalSwD).take(numSw)
    val initTuple = goalTuple.mapIndexed { idx, g -> if (idx % 2 == 0) !g else g }.toMutableList()
    if (initTuple == goalTuple) initTuple[0] = !initTuple[0]

    val clauses = mutableListOf<LogicClause>()
    val plates = mutableListOf<List<List<String>>>()
    plates += listOf(listOf("n_switch", "d_1"), listOf("op_or"), listOf("n_switch", "d_2"), listOf("v_activate"))
    plates += listOf(listOf("n_switch", "d_1"), listOf("op_and"), listOf("n_switch", "d_2"), listOf("m_not"))
    clauses +=
      LogicClause(
        operator = "XOR",
        checks = listOf(ModuleCheck("sw_a", "true"), ModuleCheck("sw_b", "true")),
      )

    if (goalSwA) {
      plates += listOf(listOf("n_switch", "d_1"), listOf("v_activate"))
    } else {
      plates += listOf(listOf("n_switch", "d_2"), listOf("v_activate"))
    }
    if (numSw >= 3) {
      plates += listOf(listOf("n_switch", "d_3"), listOf("v_activate"))
    }
    if (numSw >= 4) {
      plates +=
        if (goalSwD) listOf(listOf("n_switch", "d_4"), listOf("v_activate"))
        else listOf(listOf("n_switch", "d_4"), listOf("v_activate", "m_not"))
    }

    val triggers = mutableListOf<String>()
    keys.forEachIndexed { idx, k ->
      if (initTuple[idx] != goalTuple[idx]) triggers += "${k}_toggle"
    }

    val useHold = rng.nextBoolean()
    val submitControl = if (useHold) "hold_button" else "button"
    plates += listOf(listOf("n_button"), listOf(if (useHold) "v_hold" else "v_push"))
    triggers += if (useHold) "button_hold" else "button_push"

    val def =
      LevelDef(
        levelNumber = levelNumber,
        puzzleType = PuzzleType.CONDITION,
        unlockedControls = keys + submitControl,
        plates = plates,
        initialState =
          LevelInitialState(
            swA = initTuple.getOrElse(0) { false },
            swB = initTuple.getOrElse(1) { false },
            swC = initTuple.getOrElse(2) { false },
            swD = initTuple.getOrElse(3) { false },
            lamp = "amber",
            primaryMessage = listOf(listOf("n_switch"), listOf("v_stop")),
          ),
        requirements =
          ConditionRequirement(
            swAEq = goalSwA,
            swBEq = goalSwB,
            swCEq = if (numSw >= 3) goalSwC else null,
            swDEq = if (numSw >= 4) goalSwD else null,
            logicClauses = clauses,
            requireHold = if (useHold) true else null,
            buttonPressesEq = if (useHold) null else 1,
          ),
        completionPrimary = listOf(listOf("n_switch"), listOf("v_match")),
        completionSecondary = listOf(listOf("adj_complete")),
      )
    return GeneratedRtaLevel(def, triggers, RtaArchetype.LOGIC_SWITCHES)
  }

  private fun buildLinkedSwitches(
    levelNumber: Int,
    rng: Random,
    config: RtaGenConfig,
  ): GeneratedRtaLevel? {
    val maxSwitches = (config.maxModules - 1).coerceIn(0, 4)
    if (maxSwitches < 2) return null
    val numSw = rng.nextInt(maxOf(2, (config.minModules - 1).coerceIn(2, maxSwitches)), maxSwitches + 1)
    val keys = listOf("sw_a", "sw_b", "sw_c", "sw_d").take(numSw)

    // Construct coupled links network
    val links = LinkedHashMap<String, List<String>>()
    for (i in 0 until numSw) {
      val nextKey = keys.getOrNull(i + 1)
      links[keys[i]] = if (nextKey != null) listOf(nextKey) else emptyList()
    }

    // Decide target state first (all ON or specific explicit target)
    val allOn = numSw >= 3 || rng.nextBoolean()
    val goal = BooleanArray(numSw) { idx -> if (allOn) true else (idx % 2 == 0) }

    // Reverse-walk 2..3 distinct clicks from goal to generate guaranteed solvable initial state
    val cur = goal.copyOf()
    val chosenClicks = mutableListOf<String>()
    val stepCount = rng.nextInt(2, minOf(4, numSw + 1))
    val candidateKeys = keys.shuffled(rng)
    for (i in 0 until stepCount) {
      val k = candidateKeys[i % candidateKeys.size]
      chosenClicks += k
      val flipSet = setOf(k) + links[k].orEmpty()
      keys.forEachIndexed { idx, swKey ->
        if (swKey in flipSet) cur[idx] = !cur[idx]
      }
    }
    if (cur.contentEquals(goal)) {
      val k = keys.first()
      chosenClicks += k
      val flipSet = setOf(k) + links[k].orEmpty()
      keys.forEachIndexed { idx, swKey ->
        if (swKey in flipSet) cur[idx] = !cur[idx]
      }
    }

    val plates = mutableListOf<List<List<String>>>()
    if (allOn) {
      plates += listOf(listOf("n_switch"), listOf("m_all"), listOf("v_activate"))
    } else {
      keys.forEachIndexed { idx, _ ->
        plates +=
          if (goal[idx]) listOf(listOf("n_switch", "d_${idx + 1}"), listOf("v_activate"))
          else listOf(listOf("n_switch", "d_${idx + 1}"), listOf("v_activate", "m_not"))
      }
    }
    plates += listOf(listOf("n_button"), listOf("v_push"))

    val triggers = chosenClicks.reversed().map { "${it}_toggle" } + "button_push"

    val def =
      LevelDef(
        levelNumber = levelNumber,
        puzzleType = PuzzleType.CONDITION,
        unlockedControls = keys + "button",
        switchLinks = links,
        plates = plates,
        initialState =
          LevelInitialState(
            swA = cur.getOrElse(0) { false },
            swB = cur.getOrElse(1) { false },
            swC = cur.getOrElse(2) { false },
            swD = cur.getOrElse(3) { false },
            lamp = "amber",
            primaryMessage = listOf(listOf("n_switch"), listOf("v_match", "m_not")),
          ),
        requirements =
          ConditionRequirement(
            swAEq = goal[0],
            swBEq = goal[1],
            swCEq = if (numSw >= 3) goal[2] else null,
            swDEq = if (numSw >= 4) goal[3] else null,
            buttonPressesEq = 1,
          ),
        completionPrimary = listOf(listOf("n_switch"), listOf("m_all"), listOf("v_activate")),
        completionSecondary = listOf(listOf("adj_complete")),
      )
    return GeneratedRtaLevel(def, triggers, RtaArchetype.LINKED_SWITCHES)
  }

  private fun buildOrderPad(
    levelNumber: Int,
    rng: Random,
    config: RtaGenConfig,
  ): GeneratedRtaLevel {
    val seq = listOf(1, 2, 3, 4).shuffled(rng)
    val maxPre = minOf(config.maxModules - 1, config.maxConditions - 1, 2).coerceAtLeast(0)
    val minPre = maxOf(config.minModules - 1, config.minConditions - 1).coerceIn(0, maxPre)
    val preCount = if (maxPre > minPre) rng.nextInt(minPre, maxPre + 1) else maxPre

    val prePool = listOf("conduit", "lock", "key", "route", "slider").shuffled(rng).take(preCount)
    val plates = mutableListOf<List<List<String>>>()
    val triggers = mutableListOf<String>()

    var conduitInit = "cut"; var conduitGoal: String? = null
    var lockInit = "locked"; var lockGoal: String? = null
    var keyInit = "out"; var keyGoal: String? = null
    var sliderInit = "left"; var sliderGoal: String? = null
    var routeInit = 1; var routeGoal: Int? = null

    prePool.forEach { mod ->
      when (mod) {
        "conduit" -> {
          conduitInit = "cut"; conduitGoal = "connected"
          plates += listOf(listOf("n_conduit"), listOf("v_connect"))
          triggers += "conduit_connect"
        }
        "lock" -> {
          lockInit = "locked"; lockGoal = "unlocked"
          plates += listOf(listOf("n_lock"), listOf("v_unlock"))
          triggers += "lock_unlock"
        }
        "key" -> {
          keyInit = "out"; keyGoal = "in"
          plates += listOf(listOf("n_key"), listOf("v_insert"))
          triggers += "key_insert"
        }
        "slider" -> {
          sliderInit = "left"; sliderGoal = "right"
          plates += listOf(listOf("n_slider"), listOf("v_swipe"), listOf("v_right"))
          triggers += "slider_right"
        }
        "route" -> {
          val g = rng.nextInt(2, 4)
          routeInit = 1; routeGoal = g
          plates += listOf(listOf("n_route"), Base5.toDigitWordIds(g), listOf("v_match"))
          triggers += "route_$g"
        }
      }
    }

    val useRelative = rng.nextBoolean()
    if (useRelative) {
      plates += listOf(listOf("n_button", "d_${seq[0]}"), listOf("m_first"), listOf("v_push"))
      plates += listOf(listOf("n_button", "d_${seq[1]}"), listOf("d_${seq[0]}", "m_after"), listOf("v_push"))
      plates += listOf(listOf("n_button", "d_${seq[2]}"), listOf("d_${seq[1]}", "m_after"), listOf("v_push"))
      plates += listOf(listOf("n_button", "d_${seq[3]}"), listOf("m_last"), listOf("v_push"))
    } else {
      seq.forEachIndexed { idx, btnDigit ->
        plates += listOf(listOf("d_${idx + 1}", "m_ord"), listOf("n_button", "d_$btnDigit"), listOf("v_push"))
      }
    }
    seq.forEach { d -> triggers += "order_$d" }

    val def =
      LevelDef(
        levelNumber = levelNumber,
        puzzleType = PuzzleType.CONDITION,
        unlockedControls = prePool + "order_pad",
        plates = plates,
        initialState =
          LevelInitialState(
            conduit = conduitInit, lock = lockInit, key = keyInit, slider = sliderInit, route = routeInit,
            lamp = if (prePool.isEmpty()) "off" else "amber",
            primaryMessage = listOf(listOf("n_button"), listOf("v_stop")),
          ),
        requirements =
          ConditionRequirement(
            conduitEq = conduitGoal,
            lockEq = lockGoal,
            keyEq = keyGoal,
            sliderEq = sliderGoal,
            routeEq = routeGoal,
            orderSequence = seq,
          ),
        completionPrimary = listOf(listOf("n_button"), listOf("m_all"), listOf("adj_complete")),
        completionSecondary = listOf(listOf("n_machine"), listOf("v_activate")),
      )
    return GeneratedRtaLevel(def, triggers, RtaArchetype.ORDER_PAD)
  }

  private fun buildArithmeticDialValve(
    levelNumber: Int,
    rng: Random,
    config: RtaGenConfig,
  ): GeneratedRtaLevel? {
    if (config.maxModules < 3) return null
    val equalMode = rng.nextBoolean()
    val plates = mutableListOf<List<List<String>>>()
    val triggers = mutableListOf<String>()

    val targetDial: Int
    val targetValve: Int
    val dialEquals: Boolean?
    val sumEq: Int?
    val dialEqReq: Int?

    if (equalMode) {
      val v = rng.nextInt(6, 20)
      targetDial = v; targetValve = v
      dialEquals = true; sumEq = v * 2; dialEqReq = null
      plates += listOf(listOf("n_dial"), listOf("n_valve"), listOf("m_same"))
      plates += listOf(listOf("n_dial"), listOf("op_and"), listOf("n_valve"), listOf("v_match"), Base5.toDigitWordIds(v * 2))
    } else {
      val d = rng.nextInt(4, 16)
      val sum = d + rng.nextInt(4, 16)
      targetDial = d; targetValve = sum - d
      dialEquals = null; sumEq = sum; dialEqReq = d
      plates += listOf(listOf("n_dial"), listOf("v_match"), Base5.toDigitWordIds(d))
      plates += listOf(listOf("n_dial"), listOf("op_and"), listOf("n_valve"), listOf("v_match"), Base5.toDigitWordIds(sum))
    }

    val initDial = ((targetDial + 3) % 21).let { if (it == targetDial) 0 else it }
    val initValve = ((targetValve + 5) % 22).let { if (it == targetValve) 1 else it }
    if (initDial < targetDial) plates += listOf(listOf("n_dial"), listOf("v_increase"))
    else plates += listOf(listOf("n_dial"), listOf("v_decrease"))
    if (initValve < targetValve) plates += listOf(listOf("n_valve"), listOf("v_increase"))
    else plates += listOf(listOf("n_valve"), listOf("v_decrease"))

    addNumericAdjustTriggers(triggers, "dial", initDial, targetDial)
    addNumericAdjustTriggers(triggers, "valve", initValve, targetValve)

    val useHold = rng.nextBoolean()
    val submitCtrl = if (useHold) "hold_button" else "button"
    plates += listOf(listOf("n_button"), listOf(if (useHold) "v_hold" else "v_push"))
    triggers += if (useHold) "button_hold" else "button_push"

    val def =
      LevelDef(
        levelNumber = levelNumber,
        puzzleType = PuzzleType.CONDITION,
        unlockedControls = listOf("dial", "valve", submitCtrl),
        plates = plates,
        initialState =
          LevelInitialState(
            dial = initDial,
            valve = initValve,
            lamp = "amber",
            primaryMessage = listOf(listOf("n_dial"), Base5.toDigitWordIds(initDial)),
            secondaryMessage = listOf(listOf("n_valve"), Base5.toDigitWordIds(initValve)),
          ),
        requirements =
          ConditionRequirement(
            dialEq = dialEqReq,
            dialEqualsValve = dialEquals,
            dialPlusValveEq = sumEq,
            requireHold = if (useHold) true else null,
            buttonPressesEq = if (useHold) null else 1,
          ),
        completionPrimary = listOf(listOf("n_valve"), listOf("v_flow")),
        completionSecondary = listOf(listOf("adj_complete")),
      )
    return GeneratedRtaLevel(def, triggers, RtaArchetype.ARITHMETIC_DIAL_VALVE)
  }

  private fun buildSignalTicker(
    levelNumber: Int,
    rng: Random,
    config: RtaGenConfig,
  ): GeneratedRtaLevel {
    val sequence = listOf(0, 1, 2, 3, 4).shuffled(rng)
    val maxTargets = minOf(3, config.maxConditions).coerceAtLeast(1)
    val minTargets = 1.coerceAtMost(maxTargets)
    val targetCount = rng.nextInt(minTargets, maxTargets + 1)
    val targets = (0 until targetCount).map { sequence.random(rng) }

    val maxPre = minOf(config.maxModules - 1, config.maxConditions - 1, 1).coerceAtLeast(0)
    val minPre = (config.minModules - 1).coerceIn(0, maxPre)
    val useRoutePre = maxPre >= 1 && (minPre >= 1 || rng.nextBoolean())

    val plates = mutableListOf<List<List<String>>>()
    val triggers = mutableListOf<String>()
    val unlocked = mutableListOf<String>()

    var routeGoal: Int? = null
    if (useRoutePre) {
      val r = rng.nextInt(2, 4)
      routeGoal = r
      unlocked += "route"
      plates += listOf(listOf("n_route"), Base5.toDigitWordIds(r), listOf("v_match"))
      triggers += "route_$r"
    }

    val useHold = rng.nextBoolean()
    unlocked += if (useHold) "hold_button" else "button"
    val verbId = if (useHold) "v_hold" else "v_push"
    val pushTrigger = if (useHold) "button_hold" else "button_push"

    targets.forEachIndexed { idx, tgt ->
      if (targets.size == 1) {
        plates += listOf(listOf("n_signal"), Base5.toDigitWordIds(tgt), listOf("m_if"), listOf(verbId))
      } else {
        plates += listOf(listOf("d_${idx + 1}", "m_ord"), listOf("n_signal"), Base5.toDigitWordIds(tgt), listOf("m_if"), listOf(verbId))
      }
      triggers += pushTrigger
    }

    val def =
      LevelDef(
        levelNumber = levelNumber,
        puzzleType = PuzzleType.CONDITION,
        unlockedControls = unlocked,
        tickerEnabled = true,
        tickerIntervalMs = if (useHold) 1800L else 1050L,
        tickerSequence = sequence,
        plates = plates,
        initialState =
          LevelInitialState(
            route = 1,
            lamp = "amber",
            primaryMessage = listOf(listOf("n_signal"), listOf("v_flow")),
          ),
        requirements =
          ConditionRequirement(
            routeEq = routeGoal,
            requireHold = if (useHold) true else null,
            tickerTargets = targets,
          ),
        completionPrimary = listOf(listOf("n_signal"), listOf("v_match")),
        completionSecondary = listOf(listOf("adj_complete")),
      )
    return GeneratedRtaLevel(def, triggers, RtaArchetype.SIGNAL_TICKER)
  }

  private fun buildSequence(
    levelNumber: Int,
    rng: Random,
    config: RtaGenConfig,
  ): GeneratedRtaLevel? {
    if (config.maxModules < 2) return null
    val stepCap = config.maxModules.coerceIn(2, 5)
    val stepFloor = config.minModules.coerceIn(2, stepCap)
    val numSteps = rng.nextInt(stepFloor, stepCap + 1)

    val middlePool = listOf("conduit", "lock", "key", "lever", "slider", "route").shuffled(rng)
    val selected = middlePool.take(numSteps - 1)
    val unlocked = selected + "button"

    val steps = mutableListOf<SequenceStepDef>()
    val plates = mutableListOf<List<List<String>>>()
    val triggers = mutableListOf<String>()

    var conduitInit = "cut"
    var lockInit = "locked"
    var keyInit = "out"
    var leverInit = "up"
    var sliderInit = "left"
    var routeInit = 1

    selected.forEachIndexed { idx, mod ->
      val ordSlot = Base5.toDigitWordIds(idx + 1) + "m_ord"
      when (mod) {
        "conduit" -> {
          conduitInit = "cut"
          plates += listOf(ordSlot, listOf("n_conduit"), listOf("v_connect"))
          steps += SequenceStepDef("conduit_connect", 1, listOf(listOf("n_conduit"), listOf("v_connect", "m_not")))
          triggers += "conduit_connect"
        }
        "lock" -> {
          lockInit = "locked"
          plates += listOf(ordSlot, listOf("n_lock"), listOf("v_unlock"))
          steps += SequenceStepDef("lock_unlock", 1, listOf(listOf("n_lock"), listOf("v_unlock", "m_not")))
          triggers += "lock_unlock"
        }
        "key" -> {
          keyInit = "out"
          plates += listOf(ordSlot, listOf("n_key"), listOf("v_insert"))
          steps += SequenceStepDef("key_insert", 1, listOf(listOf("n_key"), listOf("v_insert", "m_not")))
          triggers += "key_insert"
        }
        "lever" -> {
          leverInit = "up"
          plates += listOf(ordSlot, listOf("n_lever"), listOf("v_pull"))
          steps += SequenceStepDef("lever_pull", 1, listOf(listOf("n_lever"), listOf("v_pull", "m_not")))
          triggers += "lever_pull"
        }
        "slider" -> {
          sliderInit = "left"
          plates += listOf(ordSlot, listOf("n_slider"), listOf("v_swipe"), listOf("v_right"))
          steps += SequenceStepDef("slider_right", 1, listOf(listOf("n_slider"), listOf("v_right", "m_not")))
          triggers += "slider_right"
        }
        "route" -> {
          routeInit = 1
          val targetRoute = rng.nextInt(2, 4)
          plates += listOf(ordSlot, listOf("n_route"), Base5.toDigitWordIds(targetRoute))
          steps += SequenceStepDef("route_$targetRoute", 1, listOf(listOf("n_route"), listOf("d_$targetRoute", "m_not")))
          triggers += "route_$targetRoute"
        }
      }
    }

    val lastOrd = Base5.toDigitWordIds(numSteps) + "m_ord"
    val buttonRepeat = rng.nextInt(1, 3)
    plates += listOf(lastOrd, listOf("n_button"), listOf("v_push"), Base5.toDigitWordIds(buttonRepeat) + "m_times")
    steps += SequenceStepDef("button_push", buttonRepeat, listOf(listOf("n_button"), listOf("v_push", "m_not")))
    repeat(buttonRepeat) { triggers += "button_push" }

    val def =
      LevelDef(
        levelNumber = levelNumber,
        puzzleType = PuzzleType.SEQUENCE,
        unlockedControls = unlocked,
        plates = plates,
        initialState =
          LevelInitialState(
            conduit = conduitInit, lock = lockInit, key = keyInit,
            lever = leverInit, slider = sliderInit, route = routeInit,
            lamp = "amber",
            primaryMessage = listOf(listOf("n_machine"), listOf("v_stop")),
          ),
        sequenceSteps = steps,
        completionPrimary = listOf(listOf("n_machine"), listOf("v_activate")),
        completionSecondary = listOf(listOf("adj_complete")),
      )
    return GeneratedRtaLevel(def, triggers, RtaArchetype.SEQUENCE)
  }

  private fun buildDeterministicFallback(
    levelNumber: Int,
    config: RtaGenConfig,
  ): GeneratedRtaLevel {
    if (config.maxModules <= 1) {
      val presses = maxOf(2, config.minConditions)
      val def =
        LevelDef(
          levelNumber = levelNumber,
          puzzleType = PuzzleType.CONDITION,
          unlockedControls = listOf("button"),
          plates = listOf(listOf(listOf("n_button"), listOf("v_push"), Base5.toDigitWordIds(presses) + "m_times")),
          initialState = LevelInitialState(lamp = "off", primaryMessage = listOf(listOf("n_machine"), listOf("v_stop"))),
          requirements = ConditionRequirement(buttonPressesEq = presses),
          completionPrimary = listOf(listOf("n_machine"), listOf("v_activate")),
          completionSecondary = listOf(listOf("adj_complete")),
        )
      return GeneratedRtaLevel(def, List(presses) { "button_push" }, RtaArchetype.STANDARD_CONDITION)
    }

    val def =
      LevelDef(
        levelNumber = levelNumber,
        puzzleType = PuzzleType.CONDITION,
        unlockedControls = listOf("conduit", "button"),
        plates =
          listOf(
            listOf(listOf("n_conduit"), listOf("v_connect")),
            listOf(listOf("n_button"), listOf("v_push")),
          ),
        initialState =
          LevelInitialState(
            conduit = "cut",
            lamp = "amber",
            primaryMessage = listOf(listOf("n_conduit"), listOf("v_cut")),
          ),
        requirements = ConditionRequirement(conduitEq = "connected", buttonPressesEq = 1),
        completionPrimary = listOf(listOf("n_machine"), listOf("v_activate")),
        completionSecondary = listOf(listOf("adj_complete")),
      )
    return GeneratedRtaLevel(def, listOf("conduit_connect", "button_push"), RtaArchetype.STANDARD_CONDITION)
  }

  private fun addNumericAdjustTriggers(
    triggers: MutableList<String>,
    prefix: String,
    fromVal: Int,
    toVal: Int,
  ) {
    var cur = fromVal
    while (cur < toVal) {
      if (toVal - cur >= 5) {
        triggers += "${prefix}_inc5"
        cur += 5
      } else {
        triggers += "${prefix}_inc"
        cur += 1
      }
    }
    while (cur > toVal) {
      triggers += "${prefix}_dec"
      cur -= 1
    }
  }

  fun usesOnlyValidWords(levelDef: LevelDef, validWordIds: Set<String>): Boolean {
    fun slotsOk(slots: List<List<String>>): Boolean =
      slots.all { slot -> slot.all { it in validWordIds } }

    if (!slotsOk(levelDef.initialState.primaryMessage)) return false
    if (!slotsOk(levelDef.initialState.secondaryMessage)) return false
    if (!slotsOk(levelDef.completionPrimary)) return false
    if (!slotsOk(levelDef.completionSecondary)) return false
    if (levelDef.plates.any { !slotsOk(it) }) return false
    if (levelDef.sequenceSteps.any { !slotsOk(it.hintPrimary) }) return false
    levelDef.requirements?.logicClauses?.forEach {
      if (!slotsOk(it.hintPrimary) || !slotsOk(it.hintSecondary)) return false
    }
    return true
  }

  fun verifySolvable(generated: GeneratedRtaLevel): Boolean {
    val def = generated.levelDef
    var state = LevelEngine.createInitialState(0, def)
    if (state.isCleared) return false
    val submitTriggers = setOf("button_push", "button_hold", "dual_push")

    for ((idx, trig) in generated.solutionTriggers.withIndex()) {
      if (def.tickerEnabled && trig in submitTriggers) {
        val target = def.requirements?.tickerTargets?.getOrNull(state.tickerCaptures) ?: return false
        var guard = 0
        while (state.tickerDigit != target && guard < 25) {
          state = LevelEngine.advanceTicker(def, state)
          guard++
        }
        if (state.tickerDigit != target) return false
      }
      val res = LevelEngine.step(def, state, trig, 1000L + idx, 1000L + idx)
      state = res.newState
    }
    return state.isCleared && state.lamp == LampState.GREEN
  }
}
