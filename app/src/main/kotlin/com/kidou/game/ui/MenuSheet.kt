package com.kidou.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.model.AppLanguage
import com.kidou.game.model.LampState
import com.kidou.game.ui.components.GlyphSentenceRow
import com.kidou.game.ui.components.PixelDivider
import com.kidou.game.ui.i18n.stringsFor
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
  onToggleBgm: (Boolean) -> Unit,
  onToggleSound: (Boolean) -> Unit,
  onSelectLanguage: (AppLanguage) -> Unit,
  onDebugBulkApply: () -> Unit,
  onDebugResetTranslations: () -> Unit,
  onDebugFill19Clears: () -> Unit,
  onResetAll: () -> Unit,
  onDismiss: () -> Unit,
) {
  val data = uiState.gameData ?: return
  val strings = stringsFor(uiState.language)
  val stats = uiState.translationStats
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showResetConfirm by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = KidouColors.Panel,
    contentColor = KidouColors.Glyph,
    shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .fillMaxHeight(0.86f)
          .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = strings.menu,
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
          label = strings.menuLogTab,
          selected = uiState.menuTab == MenuTab.LOG,
          onClick = { onSelectTab(MenuTab.LOG) },
          modifier = Modifier.weight(1f),
        )
        MenuTabButton(
          label = strings.menuProgressTab,
          selected = uiState.menuTab == MenuTab.PROGRESS,
          onClick = { onSelectTab(MenuTab.PROGRESS) },
          modifier = Modifier.weight(1f),
        )
        MenuTabButton(
          label = strings.menuSettingsTab,
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
                text = strings.noLogsYet,
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
                      .background(KidouColors.PanelSunken, RectangleShape)
                      .border(1.dp, KidouColors.Border, RectangleShape)
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
                        text = "B${entry.boardId}-${entry.levelNumber}",
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
                      words = data.words,
                      dictionary = uiState.dictionary,
                      language = uiState.language,
                      onWordClick = onEditWord,
                      compact = true,
                    )
                  }
                  if (entry.resultPrimarySlots.isNotEmpty()) {
                    GlyphSentenceRow(
                      slots = entry.resultPrimarySlots,
                      glyphs = data.glyphs,
                      words = data.words,
                      dictionary = uiState.dictionary,
                      language = uiState.language,
                      onWordClick = onEditWord,
                      compact = true,
                      glyphColor =
                        if (entry.lamp == LampState.RED) KidouColors.Red else KidouColors.Glyph,
                    )
                  }
                  if (entry.resultSecondarySlots.isNotEmpty()) {
                    GlyphSentenceRow(
                      slots = entry.resultSecondarySlots,
                      glyphs = data.glyphs,
                      words = data.words,
                      dictionary = uiState.dictionary,
                      language = uiState.language,
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
          Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            StatRow(strings.clearedLevelsLabel, "${uiState.board1Clears} / 25")
            StatRow(
              strings.board2StatusLabel,
              if (uiState.isBoard2Unlocked) strings.unlocked else strings.locked,
            )
            StatRow(
              strings.discoveredWordsLabel,
              "${uiState.discoveredWordIds.size} / ${data.wordOrder.size}",
            )
            StatRow(
              strings.translatedWordsLabel,
              "${stats.translatedCount} / ${stats.totalCount}",
            )
            StatRow(strings.translationRateLabel, "${stats.percent}%")
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(KidouColors.PanelSunken, RectangleShape)
                  .border(1.dp, KidouColors.Border, RectangleShape)
                  .padding(12.dp),
            ) {
              Text(
                text = strings.progressNote,
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
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            // BGM Switch
            ToggleSettingRow(
              label = strings.bgmLabel,
              checked = uiState.bgmEnabled,
              onCheckedChange = onToggleBgm,
              contentDesc = "switch_bgm",
            )

            // SFX Switch
            ToggleSettingRow(
              label = strings.sfxLabel,
              checked = uiState.soundEnabled,
              onCheckedChange = onToggleSound,
              contentDesc = "switch_sfx",
            )

            // Language Switch (JA / EN)
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(KidouColors.Tile, RectangleShape)
                  .border(1.dp, KidouColors.Border, RectangleShape)
                  .padding(horizontal = 14.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = strings.languageLabel,
                fontFamily = FontFamily.Monospace,
                color = KidouColors.Glyph,
                fontSize = 13.sp,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuTabButton(
                  label = "日本語",
                  selected = uiState.language == AppLanguage.JA,
                  onClick = { onSelectLanguage(AppLanguage.JA) },
                  modifier = Modifier.weight(1f).semantics { contentDescription = "lang_ja" },
                )
                MenuTabButton(
                  label = "English",
                  selected = uiState.language == AppLanguage.EN,
                  onClick = { onSelectLanguage(AppLanguage.EN) },
                  modifier = Modifier.weight(1f).semantics { contentDescription = "lang_en" },
                )
              }
            }

            // Credits (CC BY 4.0 required attribution)
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(KidouColors.PanelSunken, RectangleShape)
                  .border(1.dp, KidouColors.Border, RectangleShape)
                  .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = strings.creditsHeader,
                color = KidouColors.Amber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
              )
              Text(
                text = strings.creditsBody,
                color = KidouColors.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
              )
            }

            Button(
              onClick = { showResetConfirm = true },
              shape = RectangleShape,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = KidouColors.Red.copy(alpha = 0.18f),
                  contentColor = KidouColors.Red,
                ),
              modifier =
                Modifier
                  .fillMaxWidth()
                  .border(1.dp, KidouColors.Red.copy(alpha = 0.6f), RectangleShape),
            ) {
              Text(strings.resetAllButton, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))
            PixelDivider()

            // Non-game Developer Debug Section (§9)
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(Color(0xFF141118), RectangleShape)
                  .border(1.dp, KidouColors.Muted.copy(alpha = 0.5f), RectangleShape)
                  .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = strings.debugSectionHeader,
                color = KidouColors.Muted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
              )
              OutlinedButton(
                onClick = onDebugBulkApply,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "debug_bulk_apply" },
              ) {
                Text(strings.debugBulkApply, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = KidouColors.Cyan)
              }
              OutlinedButton(
                onClick = onDebugResetTranslations,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "debug_reset_trans" },
              ) {
                Text(strings.debugResetTranslations, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = KidouColors.Amber)
              }
              OutlinedButton(
                onClick = onDebugFill19Clears,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "debug_fill_19" },
              ) {
                Text(strings.debugFill19Clears, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = KidouColors.Green)
              }
            }
            Spacer(modifier = Modifier.height(12.dp))
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
      shape = RectangleShape,
      title = { Text(strings.resetConfirmTitle, fontFamily = FontFamily.Monospace) },
      text = {
        Text(strings.resetConfirmBody, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
      },
      confirmButton = {
        TextButton(
          onClick = {
            showResetConfirm = false
            onResetAll()
          },
        ) {
          Text(strings.confirmReset, color = KidouColors.Red, fontFamily = FontFamily.Monospace)
        }
      },
      dismissButton = {
        TextButton(onClick = { showResetConfirm = false }) {
          Text(strings.cancel, color = KidouColors.Muted, fontFamily = FontFamily.Monospace)
        }
      },
    )
  }
}

@Composable
private fun ToggleSettingRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  contentDesc: String,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(KidouColors.Tile, RectangleShape)
        .border(1.dp, KidouColors.Border, RectangleShape)
        .padding(horizontal = 14.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      color = KidouColors.Glyph,
      fontSize = 13.sp,
    )
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.semantics { contentDescription = contentDesc },
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = KidouColors.Background,
          checkedTrackColor = KidouColors.Amber,
        ),
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
        .background(if (selected) KidouColors.Cyan.copy(alpha = 0.15f) else KidouColors.Tile, RectangleShape)
        .border(
          1.5.dp,
          if (selected) KidouColors.Cyan else KidouColors.Border,
          RectangleShape,
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
        .background(KidouColors.Tile, RectangleShape)
        .border(1.dp, KidouColors.Border, RectangleShape)
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
