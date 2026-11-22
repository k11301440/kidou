package com.kidou.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.model.LampState
import com.kidou.game.ui.components.GlyphSentenceRow
import com.kidou.game.ui.theme.KidouColors
import com.kidou.game.viewmodel.GameUiState
import com.kidou.game.viewmodel.MenuTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
  uiState: GameUiState,
  onSelectTab: (MenuTab) -> Unit,
  onEditWord: (String) -> Unit,
  onToggleSound: (Boolean) -> Unit,
  onResetAll: () -> Unit,
  onDismiss: () -> Unit,
) {
  val data = uiState.gameData ?: return
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showResetConfirm by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = KidouColors.Panel,
    contentColor = KidouColors.Glyph,
    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .fillMaxHeight(0.84f)
          .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "メニュー",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = KidouColors.Glyph,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        MenuTabButton(
          label = "操作ログ",
          selected = uiState.menuTab == MenuTab.LOG,
          onClick = { onSelectTab(MenuTab.LOG) },
          modifier = Modifier.weight(1f),
        )
        MenuTabButton(
          label = "進捗",
          selected = uiState.menuTab == MenuTab.PROGRESS,
          onClick = { onSelectTab(MenuTab.PROGRESS) },
          modifier = Modifier.weight(1f),
        )
        MenuTabButton(
          label = "設定",
          selected = uiState.menuTab == MenuTab.SETTINGS,
          onClick = { onSelectTab(MenuTab.SETTINGS) },
          modifier = Modifier.weight(1f),
        )
      }

      when (uiState.menuTab) {
        MenuTab.LOG -> {
          if (uiState.operationLog.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxWidth().weight(1f),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "まだ操作記録がない",
                color = KidouColors.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
              )
            }
          } else {
            val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
            LazyColumn(
              modifier = Modifier.fillMaxWidth().weight(1f),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(uiState.operationLog, key = { it.id }) { entry ->
                val lampColor = lampColorOf(entry.lamp)
                Column(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .background(KidouColors.PanelSunken, RoundedCornerShape(2.dp))
                      .border(1.dp, KidouColors.Border, RoundedCornerShape(2.dp))
                      .padding(10.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Box(modifier = Modifier.size(8.dp).background(lampColor))
                      Text(
                        text = "PHASE ${entry.phase}",
                        color = KidouColors.Muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                      )
                    }
                    Text(
                      text = timeFormat.format(Date(entry.timestampMs)),
                      color = KidouColors.Muted,
                      fontFamily = FontFamily.Monospace,
                      fontSize = 11.sp,
                    )
                  }

                  if (entry.actionLabelSlots.isNotEmpty()) {
                    GlyphSentenceRow(
                      slots = entry.actionLabelSlots,
                      glyphs = data.glyphs,
                      dictionary = uiState.dictionary,
                      onWordClick = onEditWord,
                      compact = true,
                    )
                  }
                  if (entry.resultPrimarySlots.isNotEmpty()) {
                    GlyphSentenceRow(
                      slots = entry.resultPrimarySlots,
                      glyphs = data.glyphs,
                      dictionary = uiState.dictionary,
                      onWordClick = onEditWord,
                      compact = true,
                      glyphColor = if (entry.lamp == LampState.RED) KidouColors.Red else KidouColors.Glyph,
                    )
                  }
                  if (entry.resultSecondarySlots.isNotEmpty()) {
                    GlyphSentenceRow(
                      slots = entry.resultSecondarySlots,
                      glyphs = data.glyphs,
                      dictionary = uiState.dictionary,
                      onWordClick = onEditWord,
                      compact = true,
                    )
                  }
                }
              }
            }
          }
        }

        MenuTab.PROGRESS -> {
          val completedPhases =
            if (uiState.machineState.isFullyActivated) 5
            else (uiState.machineState.maxUnlockedPhase - 1).coerceAtLeast(0)
          Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            StatRow("完了フェーズ", "$completedPhases / 5")
            StatRow("遭遇した単語", "${uiState.discoveredWordIds.size} / ${uiState.totalWordsCount}")
            StatRow("辞書登録済み", "${uiState.registeredCount} 語")
            StatRow("解読率", "${uiState.decodeRatePercent}%")
            StatRow(
              "装置状態",
              if (uiState.machineState.isFullyActivated) "完全起動" else "起動中",
            )
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(KidouColors.PanelSunken, RoundedCornerShape(2.dp))
                  .border(1.dp, KidouColors.Border, RoundedCornerShape(2.dp))
                  .padding(12.dp),
            ) {
              Text(
                text = "翻訳に正誤判定はない。違和感や反応の一貫性から、自分の辞書を更新していく。",
                color = KidouColors.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
              )
            }
          }
        }

        MenuTab.SETTINGS -> {
          Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(KidouColors.Tile, RoundedCornerShape(2.dp))
                  .border(1.dp, KidouColors.Border, RoundedCornerShape(2.dp))
                  .padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "効果音（シンセ音）",
                fontFamily = FontFamily.Monospace,
                color = KidouColors.Glyph,
                fontSize = 13.sp,
              )
              Switch(
                checked = uiState.soundEnabled,
                onCheckedChange = onToggleSound,
                colors =
                  SwitchDefaults.colors(
                    checkedThumbColor = KidouColors.Background,
                    checkedTrackColor = KidouColors.Amber,
                  ),
              )
            }

            Button(
              onClick = { showResetConfirm = true },
              shape = RoundedCornerShape(2.dp),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = KidouColors.Red.copy(alpha = 0.18f),
                  contentColor = KidouColors.Red,
                ),
              modifier =
                Modifier
                  .fillMaxWidth()
                  .border(1.dp, KidouColors.Red.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
            ) {
              Text("装置と辞書をリセット", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }

  if (showResetConfirm) {
    AlertDialog(
      onDismissRequest = { showResetConfirm = false },
      containerColor = KidouColors.Panel,
      titleContentColor = KidouColors.Glyph,
      textContentColor = KidouColors.Muted,
      title = { Text("初期化の確認", fontFamily = FontFamily.Monospace) },
      text = {
        Text(
          "装置の進行状況・操作ログ・辞書の登録内容をすべて削除する。",
          fontFamily = FontFamily.Monospace,
          fontSize = 13.sp,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showResetConfirm = false
            onResetAll()
          },
        ) {
          Text("リセット", color = KidouColors.Red, fontFamily = FontFamily.Monospace)
        }
      },
      dismissButton = {
        TextButton(onClick = { showResetConfirm = false }) {
          Text("戻る", color = KidouColors.Muted, fontFamily = FontFamily.Monospace)
        }
      },
    )
  }
}

@Composable
private fun MenuTabButton(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .clip(RoundedCornerShape(2.dp))
        .background(if (selected) KidouColors.Cyan.copy(alpha = 0.15f) else KidouColors.Tile)
        .border(
          1.dp,
          if (selected) KidouColors.Cyan else KidouColors.Border,
          RoundedCornerShape(2.dp),
        )
        .clickable(onClick = onClick)
        .padding(vertical = 8.dp),
  ) {
    Text(
      text = label,
      color = if (selected) KidouColors.Cyan else KidouColors.Muted,
      fontFamily = FontFamily.Monospace,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
      fontSize = 12.sp,
    )
  }
}

@Composable
private fun StatRow(label: String, value: String) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(KidouColors.Tile, RoundedCornerShape(2.dp))
        .border(1.dp, KidouColors.Border, RoundedCornerShape(2.dp))
        .padding(horizontal = 14.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = label, color = KidouColors.Muted, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    Text(
      text = value,
      color = KidouColors.Glyph,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      fontSize = 13.sp,
    )
  }
}

fun lampColorOf(lamp: LampState): Color =
  when (lamp) {
    LampState.OFF -> KidouColors.LampOff
    LampState.AMBER -> KidouColors.Amber
    LampState.GREEN -> KidouColors.Green
    LampState.RED -> KidouColors.Red
  }
