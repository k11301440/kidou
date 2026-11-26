package com.kidou.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.ui.components.PixelActionButton
import com.kidou.game.ui.components.PixelBevelPad
import com.kidou.game.ui.components.PixelStonePanel
import com.kidou.game.ui.i18n.stringsFor
import com.kidou.game.ui.theme.DotGothic16FontFamily
import com.kidou.game.ui.theme.KidouTheme
import com.kidou.game.viewmodel.GameUiState
import kotlinx.coroutines.delay

internal fun formatRtaTime(ms: Long): String {
  val safeMs = ms.coerceAtLeast(0L)
  val totalTenths = safeMs / 100L
  val tenths = totalTenths % 10L
  val totalSeconds = totalTenths / 10L
  val seconds = totalSeconds % 60L
  val minutes = totalSeconds / 60L
  return "%02d:%02d.%d".format(minutes, seconds, tenths)
}

@Composable
fun RtaScreen(
  uiState: GameUiState,
  onTickTimer: () -> Unit,
  onTickTicker: () -> Unit,
  onTrigger: (String) -> Unit,
  onRetry: () -> Unit,
  onExitToTitle: () -> Unit,
  onWordClick: (String) -> Unit,
  onOpenDictionary: () -> Unit,
  onOpenMenu: () -> Unit,
) {
  val data = uiState.gameData ?: return
  val run = uiState.rtaRun ?: return
  val palette = KidouTheme.palette
  val strings = stringsFor(uiState.language)

  // Live elapsed timer tick (record only, never a time limit failure)
  LaunchedEffect(run.finished, run.startElapsedMs) {
    if (run.finished) return@LaunchedEffect
    while (true) {
      delay(100L)
      onTickTimer()
    }
  }

  // Cycling digit ticker for RTA signal machines
  LaunchedEffect(run.currentIndex, run.finished, run.currentLevelDef.tickerEnabled) {
    if (run.finished || !run.currentLevelDef.tickerEnabled) return@LaunchedEffect
    val interval = run.currentLevelDef.tickerIntervalMs.coerceAtLeast(450L)
    while (true) {
      delay(interval)
      onTickTicker()
    }
  }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .background(palette.background)
        .safeDrawingPadding()
        .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Top bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PixelActionButton(
        label = "← ${strings.rtaBackTitle}",
        accent = palette.accentSecondary,
        onClick = onExitToTitle,
        modifier = Modifier.semantics { contentDescription = "rta_exit_title" },
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelActionButton(
          label = strings.dictionary,
          accent = palette.accentPrimary,
          onClick = onOpenDictionary,
        )
        PixelActionButton(
          label = strings.menu,
          accent = palette.muted,
          onClick = onOpenMenu,
        )
      }
    }

    // RTA HUD Strip: 10-machine progress pips + Monotonic Clock
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.panel, RectangleShape)
          .border(1.5.dp, palette.accentPrimary.copy(alpha = 0.7f), RectangleShape)
          .padding(horizontal = 10.dp, vertical = 7.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val completedCount = if (run.finished) run.levels.size else run.currentIndex
        Text(
          text = "RTA  $completedCount / ${run.levels.size}",
          color = palette.accentPrimary,
          fontFamily = DotGothic16FontFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 12.sp,
          modifier = Modifier.semantics { contentDescription = "rta_progress_counter" },
        )
        Row(
          modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          for (i in run.levels.indices) {
            val done = run.finished || i < run.currentIndex
            val active = !run.finished && i == run.currentIndex
            Box(
              modifier =
                Modifier
                  .weight(1f)
                  .height(6.dp)
                  .background(
                    when {
                      done -> palette.statusSuccess
                      active -> palette.accentPrimary
                      else -> palette.lampOff
                    },
                  ),
            )
          }
        }
      }

      Box(
        modifier =
          Modifier
            .background(palette.panelSunken, RectangleShape)
            .border(1.5.dp, palette.borderBright, RectangleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics { contentDescription = "rta_timer_text" },
      ) {
        Text(
          text = formatRtaTime(run.elapsedMs),
          color = if (run.finished) palette.statusSuccess else palette.glyph,
          fontFamily = DotGothic16FontFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 17.sp,
        )
      }
    }

    if (run.finished) {
      PixelStonePanel(
        borderColor = palette.statusSuccess,
        modifier = Modifier.fillMaxWidth().weight(1f),
      ) {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = strings.rtaClearBanner,
            color = palette.statusSuccess,
            fontFamily = DotGothic16FontFamily,
            fontSize = 24.sp,
            modifier = Modifier.semantics { contentDescription = "rta_complete_banner" },
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = formatRtaTime(run.elapsedMs),
            color = palette.glyph,
            fontFamily = DotGothic16FontFamily,
            fontSize = 36.sp,
          )
          if (run.isNewBest) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
              modifier =
                Modifier
                  .background(palette.accentPrimary.copy(alpha = 0.18f), RectangleShape)
                  .border(1.5.dp, palette.accentPrimary, RectangleShape)
                  .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
              Text(
                text = strings.rtaNewBest,
                color = palette.accentPrimary,
                fontFamily = DotGothic16FontFamily,
                fontSize = 13.sp,
              )
            }
          }
          Spacer(modifier = Modifier.height(14.dp))
          val bestText = uiState.rtaBestTimeMs?.let { formatRtaTime(it) } ?: strings.rtaNoneYet
          Text(
            text = "${strings.rtaBestTimeLabel}: $bestText",
            color = palette.muted,
            fontFamily = DotGothic16FontFamily,
            fontSize = 14.sp,
          )
          Spacer(modifier = Modifier.height(24.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            PixelBevelPad(
              borderColor = palette.accentSecondary,
              fillColor = palette.accentSecondary.copy(alpha = 0.16f),
              onClick = onExitToTitle,
              modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = "rta_back_title_btn" },
            ) {
              Text(strings.rtaBackTitle, color = palette.accentSecondary, fontFamily = DotGothic16FontFamily, fontSize = 14.sp)
            }
            PixelBevelPad(
              borderColor = palette.statusSuccess,
              fillColor = palette.statusSuccess.copy(alpha = 0.2f),
              onClick = onRetry,
              modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = "rta_retry_btn" },
            ) {
              Text(strings.rtaRetry, color = palette.statusSuccess, fontFamily = DotGothic16FontFamily, fontSize = 14.sp)
            }
          }
        }
      }
    } else {
      MachineDisplayPanel(
        levelDef = run.currentLevelDef,
        levelState = run.activeLevelState,
        glyphs = data.glyphs,
        words = data.words,
        dictionary = uiState.dictionary,
        language = uiState.language,
        revealLabel = strings.memoryRevealButton,
        onWordClick = onWordClick,
        onResetMemory = { onTrigger("memory_reset") },
        modifier = Modifier.fillMaxWidth().weight(1f),
      )

      ControlDeckPanel(
        levelDef = run.currentLevelDef,
        levelState = run.activeLevelState,
        glyphs = data.glyphs,
        words = data.words,
        dictionary = uiState.dictionary,
        language = uiState.language,
        nextLabel = strings.nextLevel,
        onWordClick = onWordClick,
        onTrigger = onTrigger,
        onNextAction = {},
      )
    }
  }
}
