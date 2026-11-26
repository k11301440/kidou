package com.kidou.game.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.R
import com.kidou.game.config.AppConfig
import com.kidou.game.config.PlayGamesConfig
import com.kidou.game.engine.Base5
import com.kidou.game.engine.LevelEngine
import com.kidou.game.model.AppLanguage
import com.kidou.game.model.GlyphGrid
import com.kidou.game.model.LampState
import com.kidou.game.ui.components.PixelDivider
import com.kidou.game.ui.components.PixelGlyph
import com.kidou.game.ui.components.PixelLed
import com.kidou.game.ui.components.rememberKidouHapticClick
import com.kidou.game.ui.i18n.stringsFor
import com.kidou.game.ui.theme.ColorTheme
import com.kidou.game.ui.theme.DotGothic16FontFamily
import com.kidou.game.ui.theme.KidouPalette
import com.kidou.game.ui.theme.KidouTheme
import com.kidou.game.ui.theme.ThemeMode
import com.kidou.game.viewmodel.GameUiState
import com.kidou.game.viewmodel.MenuTab

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MenuSheet(
  uiState: GameUiState,
  onSelectTab: (MenuTab) -> Unit,
  onToggleBgm: (Boolean) -> Unit,
  onToggleSound: (Boolean) -> Unit,
  onToggleHaptics: (Boolean) -> Unit,
  onSelectLanguage: (AppLanguage) -> Unit,
  onSelectThemeMode: (ThemeMode) -> Unit,
  onSelectColorTheme: (ColorTheme) -> Unit,
  onPlayGamesConnectOrAchievements: () -> Unit,
  onRestoreCloudSave: () -> Unit,
  onDisconnectPlayGames: () -> Unit,
  onDebugBulkApply: () -> Unit,
  onDebugResetTranslations: () -> Unit,
  onDebugFill19ClearsBoard1: () -> Unit,
  onDebugFill19ClearsBoard2: () -> Unit,
  onDebugFill19ClearsBoard3: () -> Unit,
  onDebugFill19ClearsBoard4: () -> Unit,
  onDebugFill24ClearsBoard5: () -> Unit,
  onResetAll: () -> Unit,
  onDismiss: () -> Unit,
) {
  val data = uiState.gameData ?: return
  val palette = KidouTheme.palette
  val strings = stringsFor(uiState.language)
  val stats = uiState.translationStats
  val uriHandler = LocalUriHandler.current
  val context = LocalContext.current
  val hapticClick = rememberKidouHapticClick()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showResetConfirm by remember { mutableStateOf(false) }
  var showDisconnectGuide by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = palette.panel,
    contentColor = palette.glyph,
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
        fontFamily = DotGothic16FontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        color = palette.glyph,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
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
        MenuTab.PROGRESS -> {
          val totalPlayable = data.boardCollection.boards.sumOf { it.totalSlots }.coerceAtLeast(1)
          Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            // Overall Progress Bar Card (§4)
            VisualBarProgressCard(
              title = strings.overallProgressLabel,
              valueLabel = "${uiState.totalClears} / $totalPlayable",
              fraction = uiState.totalClears.toFloat() / totalPlayable.toFloat(),
              barColor = palette.statusSuccess,
              contentDesc = "progress_overall_bar",
            )

            // Per-board 25-pip visual grids (Boards 1..5) (§4)
            data.boardCollection.boards.forEach { board ->
              val cleared = LevelEngine.countBoardClears(board.id, uiState.clearedLevelKeys)
              val unlocked = LevelEngine.isBoardUnlocked(board, uiState.clearedLevelKeys)
              VisualBoardProgressRow(
                boardId = board.id,
                clearedCount = cleared,
                totalSlots = board.totalSlots,
                threshold = if (board.id < 5) 20 else null,
                unlocked = unlocked,
                statusText = if (unlocked) strings.unlocked else strings.locked,
                glyphs = data.glyphs,
              )
            }

            // Lexicon & Translation Rate Visual Bars (§4)
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(palette.tile, RectangleShape)
                  .border(1.dp, palette.border, RectangleShape)
                  .padding(horizontal = 12.dp, vertical = 10.dp)
                  .semantics { contentDescription = "progress_translation_bar" },
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              val totalWords = data.wordOrder.size.coerceAtLeast(1)
              val discFraction = uiState.discoveredWordIds.size.toFloat() / totalWords.toFloat()
              LabeledMiniBar(
                label = strings.discoveredWordsLabel,
                valueLabel = "${uiState.discoveredWordIds.size} / ${data.wordOrder.size}",
                fraction = discFraction,
                barColor = palette.accentSecondary,
              )
              val transFraction = stats.percent.coerceIn(0, 100) / 100f
              LabeledMiniBar(
                label = strings.translatedWordsLabel,
                valueLabel = "${stats.translatedCount} / ${stats.totalCount} (${stats.percent}%)",
                fraction = transFraction,
                barColor = palette.accentPrimary,
              )
            }

            if (uiState.isFinalLevelCleared || uiState.rtaBestTimeMs != null) {
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .background(palette.tile, RectangleShape)
                    .border(1.dp, palette.statusSuccess.copy(alpha = 0.6f), RectangleShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = strings.rtaBestTimeLabel,
                  color = palette.muted,
                  fontFamily = DotGothic16FontFamily,
                  fontSize = 12.sp,
                )
                Text(
                  text = uiState.rtaBestTimeMs?.let { formatRtaTime(it) } ?: strings.rtaNoneYet,
                  color = palette.statusSuccess,
                  fontFamily = DotGothic16FontFamily,
                  fontSize = 13.sp,
                )
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
          }
        }

        MenuTab.SETTINGS -> {
          val isLinked = uiState.playGamesOptIn && uiState.playGamesStatus.isAuthenticated
          Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            ToggleSettingRow(
              label = strings.bgmLabel,
              checked = uiState.bgmEnabled,
              onCheckedChange = {
                hapticClick()
                onToggleBgm(it)
              },
              contentDesc = "switch_bgm",
            )

            ToggleSettingRow(
              label = strings.sfxLabel,
              checked = uiState.soundEnabled,
              onCheckedChange = {
                hapticClick()
                onToggleSound(it)
              },
              contentDesc = "switch_sfx",
            )

            ToggleSettingRow(
              label = strings.hapticsLabel,
              checked = uiState.hapticsEnabled,
              onCheckedChange = {
                hapticClick()
                onToggleHaptics(it)
              },
              contentDesc = "switch_haptics",
            )

            // Google Play Games Services v2 Section (§3)
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(palette.tile, RectangleShape)
                  .border(1.dp, palette.border, RectangleShape)
                  .padding(horizontal = 14.dp, vertical = 10.dp)
                  .semantics { contentDescription = "play_games_settings_section" },
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Image(
                    painter = painterResource(R.drawable.ic_play_games_controller),
                    contentDescription = null,
                    colorFilter =
                      ColorFilter.tint(
                        if (isLinked) palette.accentSecondary else palette.glyph,
                      ),
                    modifier = Modifier.size(20.dp),
                  )
                  Text(
                    text = strings.playGamesSectionTitle,
                    fontFamily = DotGothic16FontFamily,
                    color = palette.glyph,
                    fontSize = 13.sp,
                  )
                }
                Text(
                  text = if (isLinked) strings.playGamesConnected else strings.playGamesDisconnected,
                  fontFamily = DotGothic16FontFamily,
                  color = if (isLinked) palette.statusSuccess else palette.muted,
                  fontSize = 11.sp,
                )
              }

              Text(
                text = strings.playGamesOfflineNotice,
                fontFamily = DotGothic16FontFamily,
                color = palette.muted,
                fontSize = 10.sp,
              )

              if (uiState.playGamesToast != null) {
                val toastMsg =
                  when (uiState.playGamesToast) {
                    "restored" -> strings.playGamesRestoredToast
                    "no_cloud_save" -> strings.playGamesNoCloudToast
                    else -> strings.playGamesUnavailableToast
                  }
                Text(
                  text = toastMsg,
                  fontFamily = DotGothic16FontFamily,
                  color = palette.accentPrimary,
                  fontSize = 11.sp,
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedButton(
                  onClick = { hapticClick(); onPlayGamesConnectOrAchievements() },
                  shape = RectangleShape,
                  modifier =
                    Modifier
                      .weight(1f)
                      .semantics { contentDescription = "play_games_connect_or_achievements_btn" },
                ) {
                  Text(
                    text =
                      if (isLinked) strings.playGamesAchievementsBtn
                      else strings.playGamesConnectBtn,
                    fontFamily = DotGothic16FontFamily,
                    fontSize = 11.sp,
                    color = palette.accentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                OutlinedButton(
                  onClick = { hapticClick(); onRestoreCloudSave() },
                  shape = RectangleShape,
                  modifier =
                    Modifier
                      .weight(1f)
                      .semantics { contentDescription = "cloud_restore_btn" },
                ) {
                  Text(
                    text = strings.playGamesRestoreBtn,
                    fontFamily = DotGothic16FontFamily,
                    fontSize = 11.sp,
                    color = palette.accentPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }

              OutlinedButton(
                onClick = {
                  hapticClick()
                  onDisconnectPlayGames()
                  showDisconnectGuide = true
                },
                shape = RectangleShape,
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "play_games_disconnect_btn" },
              ) {
                Text(
                  text = strings.playGamesDisconnectBtn,
                  fontFamily = DotGothic16FontFamily,
                  fontSize = 11.sp,
                  color = palette.muted,
                )
              }
            }

            // Language Switch
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(palette.tile, RectangleShape)
                  .border(1.dp, palette.border, RectangleShape)
                  .padding(horizontal = 14.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = strings.languageLabel,
                fontFamily = DotGothic16FontFamily,
                color = palette.glyph,
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

            // Theme Mode & Simplified Color Theme Selector
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(palette.tile, RectangleShape)
                  .border(1.dp, palette.border, RectangleShape)
                  .padding(horizontal = 14.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = strings.themeModeLabel,
                fontFamily = DotGothic16FontFamily,
                color = palette.glyph,
                fontSize = 13.sp,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuTabButton(
                  label = strings.darkMode,
                  selected = uiState.themeMode == ThemeMode.DARK,
                  onClick = { onSelectThemeMode(ThemeMode.DARK) },
                  modifier = Modifier.weight(1f).semantics { contentDescription = "theme_dark" },
                )
                MenuTabButton(
                  label = strings.lightMode,
                  selected = uiState.themeMode == ThemeMode.LIGHT,
                  onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                  modifier = Modifier.weight(1f).semantics { contentDescription = "theme_light" },
                )
              }

              Text(
                text = strings.colorThemeLabel,
                fontFamily = DotGothic16FontFamily,
                color = palette.glyph,
                fontSize = 13.sp,
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ColorTheme.entries.forEach { ct ->
                  val selected = uiState.colorTheme == ct
                  val swatchColor = Color(ct.swatchHex)
                  val interaction = remember(ct) { MutableInteractionSource() }
                  val pressed by interaction.collectIsPressedAsState()
                  LaunchedEffect(pressed) {
                    if (pressed) hapticClick()
                  }
                  val invertedFg = if (palette.isDark) palette.background else palette.tile
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier =
                      Modifier
                        .offset(y = if (pressed) 1.dp else 0.dp)
                        .background(
                          when {
                            pressed -> palette.accentPrimary
                            selected -> palette.accentPrimary.copy(alpha = 0.16f)
                            else -> palette.panelSunken
                          },
                          RectangleShape,
                        )
                        .border(
                          if (selected || pressed) 1.5.dp else 1.dp,
                          if (selected || pressed) palette.accentPrimary else palette.border,
                          RectangleShape,
                        )
                        .semantics { contentDescription = "color_theme_${ct.name.lowercase()}" }
                        .clickable(
                          interactionSource = interaction,
                          indication = null,
                          onClick = { onSelectColorTheme(ct) },
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                  ) {
                    Box(modifier = Modifier.size(10.dp).background(swatchColor))
                    Text(
                      text = ct.label(uiState.language),
                      color =
                        when {
                          pressed -> invertedFg
                          selected -> palette.accentPrimary
                          else -> palette.glyph
                        },
                      fontFamily = DotGothic16FontFamily,
                      fontWeight = FontWeight.Normal,
                      fontSize = 12.sp,
                    )
                  }
                }
              }
            }

            // Privacy Policy Link
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(palette.tile, RectangleShape)
                  .border(1.dp, palette.border, RectangleShape)
                  .semantics { contentDescription = "privacy_policy_link" }
                  .clickable {
                    hapticClick()
                    runCatching { uriHandler.openUri(AppConfig.PRIVACY_POLICY_URL) }
                  }
                  .padding(horizontal = 14.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = strings.privacyPolicyLabel,
                  fontFamily = DotGothic16FontFamily,
                  color = palette.glyph,
                  fontWeight = FontWeight.Normal,
                  fontSize = 13.sp,
                )
                Text(
                  text = AppConfig.PRIVACY_POLICY_URL,
                  fontFamily = DotGothic16FontFamily,
                  color = palette.muted,
                  fontSize = 10.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Text(
                text = strings.privacyPolicyHint,
                fontFamily = DotGothic16FontFamily,
                color = palette.accentSecondary,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
              )
            }

            // Credits (CC BY 4.0 Audio & OFL 1.1 Font attribution)
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(palette.panelSunken, RectangleShape)
                  .border(1.dp, palette.border, RectangleShape)
                  .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = strings.creditsHeader,
                color = palette.accentPrimary,
                fontFamily = DotGothic16FontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
              )
              Text(
                text = strings.creditsBody,
                color = palette.muted,
                fontFamily = DotGothic16FontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                lineHeight = 16.sp,
              )
            }

            Button(
              onClick = {
                hapticClick()
                showResetConfirm = true
              },
              shape = RectangleShape,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = palette.statusDanger.copy(alpha = 0.16f),
                  contentColor = palette.statusDanger,
                ),
              modifier =
                Modifier
                  .fillMaxWidth()
                  .border(1.dp, palette.statusDanger.copy(alpha = 0.6f), RectangleShape),
            ) {
              Text(strings.resetAllButton, fontFamily = DotGothic16FontFamily, fontWeight = FontWeight.Normal)
            }

            Spacer(modifier = Modifier.height(4.dp))
            PixelDivider()

            // Non-game Developer Debug Section
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(palette.debugPanelBg, RectangleShape)
                  .border(1.dp, palette.muted.copy(alpha = 0.5f), RectangleShape)
                  .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = strings.debugSectionHeader,
                color = palette.muted,
                fontFamily = DotGothic16FontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
              )
              OutlinedButton(
                onClick = { hapticClick(); onDebugBulkApply() },
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "debug_bulk_apply" },
              ) {
                Text(strings.debugBulkApply, fontFamily = DotGothic16FontFamily, fontSize = 11.sp, color = palette.accentSecondary)
              }
              OutlinedButton(
                onClick = { hapticClick(); onDebugResetTranslations() },
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "debug_reset_trans" },
              ) {
                Text(strings.debugResetTranslations, fontFamily = DotGothic16FontFamily, fontSize = 11.sp, color = palette.accentPrimary)
              }
              OutlinedButton(
                onClick = { hapticClick(); onDebugFill19ClearsBoard1() },
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "debug_fill_19" },
              ) {
                Text(strings.debugFill19ClearsBoard1, fontFamily = DotGothic16FontFamily, fontSize = 11.sp, color = palette.statusSuccess)
              }
              OutlinedButton(
                onClick = { hapticClick(); onDebugFill19ClearsBoard2() },
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "debug_fill_19_b2" },
              ) {
                Text(strings.debugFill19ClearsBoard2, fontFamily = DotGothic16FontFamily, fontSize = 11.sp, color = palette.statusSuccess)
              }
              OutlinedButton(
                onClick = { hapticClick(); onDebugFill19ClearsBoard3() },
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "debug_fill_19_b3" },
              ) {
                Text(strings.debugFill19ClearsBoard3, fontFamily = DotGothic16FontFamily, fontSize = 11.sp, color = palette.statusSuccess)
              }
              OutlinedButton(
                onClick = { hapticClick(); onDebugFill19ClearsBoard4() },
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "debug_fill_19_b4" },
              ) {
                Text(strings.debugFill19ClearsBoard4, fontFamily = DotGothic16FontFamily, fontSize = 11.sp, color = palette.statusSuccess)
              }
              OutlinedButton(
                onClick = { hapticClick(); onDebugFill24ClearsBoard5() },
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "debug_fill_b5_24" },
              ) {
                Text(strings.debugFill24ClearsBoard5, fontFamily = DotGothic16FontFamily, fontSize = 11.sp, color = palette.statusSuccess)
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
      containerColor = palette.panel,
      titleContentColor = palette.glyph,
      textContentColor = palette.muted,
      shape = RectangleShape,
      title = { Text(strings.resetConfirmTitle, fontFamily = DotGothic16FontFamily) },
      text = {
        Text(strings.resetConfirmBody, fontFamily = DotGothic16FontFamily, fontSize = 13.sp)
      },
      confirmButton = {
        TextButton(
          onClick = {
            hapticClick()
            showResetConfirm = false
            onResetAll()
          },
        ) {
          Text(strings.confirmReset, color = palette.statusDanger, fontFamily = DotGothic16FontFamily)
        }
      },
      dismissButton = {
        TextButton(onClick = { hapticClick(); showResetConfirm = false }) {
          Text(strings.cancel, color = palette.muted, fontFamily = DotGothic16FontFamily)
        }
      },
    )
  }

  if (showDisconnectGuide) {
    AlertDialog(
      onDismissRequest = { showDisconnectGuide = false },
      containerColor = palette.panel,
      titleContentColor = palette.glyph,
      textContentColor = palette.muted,
      shape = RectangleShape,
      title = { Text(strings.playGamesDisconnectTitle, fontFamily = DotGothic16FontFamily) },
      text = {
        Text(strings.playGamesDisconnectBody, fontFamily = DotGothic16FontFamily, fontSize = 12.sp)
      },
      confirmButton = {
        TextButton(
          onClick = {
            hapticClick()
            showDisconnectGuide = false
            runCatching {
              val launchIntent =
                context.packageManager.getLaunchIntentForPackage(PlayGamesConfig.PLAY_GAMES_APP_PACKAGE)
                  ?: Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${PlayGamesConfig.PLAY_GAMES_APP_PACKAGE}"),
                  )
              context.startActivity(launchIntent)
            }
          },
        ) {
          Text(strings.playGamesOpenAppBtn, color = palette.accentSecondary, fontFamily = DotGothic16FontFamily)
        }
      },
      dismissButton = {
        TextButton(onClick = { hapticClick(); showDisconnectGuide = false }) {
          Text(strings.close, color = palette.muted, fontFamily = DotGothic16FontFamily)
        }
      },
    )
  }
}

@Composable
private fun VisualBarProgressCard(
  title: String,
  valueLabel: String,
  fraction: Float,
  barColor: Color,
  contentDesc: String,
) {
  val palette = KidouTheme.palette
  val safeFraction = fraction.coerceIn(0f, 1f)
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.tile, RectangleShape)
        .border(1.5.dp, palette.borderBright, RectangleShape)
        .padding(horizontal = 12.dp, vertical = 10.dp)
        .semantics { contentDescription = contentDesc },
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = title, color = palette.glyph, fontFamily = DotGothic16FontFamily, fontSize = 13.sp)
      Text(text = valueLabel, color = barColor, fontFamily = DotGothic16FontFamily, fontSize = 13.sp)
    }
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(10.dp)
          .background(palette.panelSunken, RectangleShape)
          .border(1.dp, palette.border, RectangleShape),
    ) {
      if (safeFraction > 0f) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth(safeFraction)
              .fillMaxHeight()
              .background(barColor),
        )
      }
    }
  }
}

@Composable
private fun VisualBoardProgressRow(
  boardId: Int,
  clearedCount: Int,
  totalSlots: Int,
  threshold: Int?,
  unlocked: Boolean,
  statusText: String,
  glyphs: Map<String, GlyphGrid>,
) {
  val palette = KidouTheme.palette
  val digits = Base5.toDigitWordIds(boardId)
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.tile, RectangleShape)
        .border(
          1.dp,
          if (clearedCount >= totalSlots) palette.statusSuccess.copy(alpha = 0.7f) else palette.border,
          RectangleShape,
        )
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .semantics { contentDescription = "progress_board_$boardId" },
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
        PixelLed(
          color =
            when {
              clearedCount >= totalSlots -> palette.statusSuccess
              unlocked -> palette.accentPrimary
              else -> palette.lampOff
            },
          size = 8.dp,
        )
        PixelGlyph(
          grid = glyphs["n_board"],
          size = 14.dp,
          color = if (unlocked) palette.glyph else palette.muted,
        )
        digits.forEach { dId ->
          PixelGlyph(
            grid = glyphs[dId],
            size = 13.dp,
            color = if (unlocked) palette.accentPrimary else palette.muted,
          )
        }
        Text(
          text = statusText,
          color = if (unlocked) palette.muted else palette.muted.copy(alpha = 0.7f),
          fontFamily = DotGothic16FontFamily,
          fontSize = 10.sp,
        )
      }
      Text(
        text = "$clearedCount / $totalSlots",
        color = if (clearedCount >= totalSlots) palette.statusSuccess else palette.glyph,
        fontFamily = DotGothic16FontFamily,
        fontSize = 12.sp,
      )
    }

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.panelSunken, RectangleShape)
          .border(1.dp, palette.border, RectangleShape)
          .padding(horizontal = 4.dp, vertical = 3.dp),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      for (i in 1..totalSlots) {
        val filled = i <= clearedCount
        val isThresh = threshold != null && i == threshold
        Box(
          modifier =
            Modifier
              .weight(1f)
              .height(if (isThresh) 9.dp else 6.dp)
              .background(
                when {
                  filled -> palette.statusSuccess
                  isThresh -> palette.accentPrimary.copy(alpha = 0.65f)
                  else -> palette.lampOff
                },
              ),
        )
      }
    }
  }
}

@Composable
private fun LabeledMiniBar(
  label: String,
  valueLabel: String,
  fraction: Float,
  barColor: Color,
) {
  val palette = KidouTheme.palette
  val safeFraction = fraction.coerceIn(0f, 1f)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = label, color = palette.muted, fontFamily = DotGothic16FontFamily, fontSize = 12.sp)
      Text(text = valueLabel, color = palette.glyph, fontFamily = DotGothic16FontFamily, fontSize = 12.sp)
    }
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(7.dp)
          .background(palette.panelSunken, RectangleShape)
          .border(1.dp, palette.border, RectangleShape),
    ) {
      if (safeFraction > 0f) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth(safeFraction)
              .fillMaxHeight()
              .background(barColor),
        )
      }
    }
  }
}

@Composable
private fun ToggleSettingRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  contentDesc: String,
) {
  val palette = KidouTheme.palette
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.tile, RectangleShape)
        .border(1.dp, palette.border, RectangleShape)
        .padding(horizontal = 14.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      fontFamily = DotGothic16FontFamily,
      color = palette.glyph,
      fontSize = 13.sp,
    )
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.semantics { contentDescription = contentDesc },
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = palette.tile,
          checkedTrackColor = palette.accentPrimary,
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
  val palette = KidouTheme.palette
  val hapticClick = rememberKidouHapticClick()
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  LaunchedEffect(pressed) {
    if (pressed) hapticClick()
  }
  val invertedFg = if (palette.isDark) palette.background else palette.tile

  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .offset(y = if (pressed) 1.dp else 0.dp)
        .background(
          when {
            pressed -> palette.accentSecondary
            selected -> palette.accentSecondary.copy(alpha = 0.16f)
            else -> palette.panelSunken
          },
          RectangleShape,
        )
        .border(
          1.5.dp,
          if (selected || pressed) palette.accentSecondary else palette.border,
          RectangleShape,
        )
        .clickable(
          interactionSource = interaction,
          indication = null,
          onClick = onClick,
        )
        .padding(vertical = 8.dp),
  ) {
    Text(
      text = label,
      color =
        when {
          pressed -> invertedFg
          selected -> palette.accentSecondary
          else -> palette.muted
        },
      fontFamily = DotGothic16FontFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 12.sp,
    )
  }
}

fun lampColorOf(lamp: LampState, palette: KidouPalette): Color =
  when (lamp) {
    LampState.OFF -> palette.lampOff
    LampState.AMBER -> palette.statusWarning
    LampState.GREEN -> palette.statusSuccess
    LampState.RED -> palette.statusDanger
  }
