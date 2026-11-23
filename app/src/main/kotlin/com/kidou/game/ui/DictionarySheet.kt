package com.kidou.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.model.AppLanguage
import com.kidou.game.model.DictionaryEntry
import com.kidou.game.model.EntrySource
import com.kidou.game.model.GlyphGrid
import com.kidou.game.model.WordColorTag
import com.kidou.game.model.WordDef
import com.kidou.game.ui.components.PixelGlyph
import com.kidou.game.ui.i18n.UiStrings
import com.kidou.game.ui.i18n.stringsFor
import com.kidou.game.ui.theme.KidouTheme
import com.kidou.game.viewmodel.DictionaryTab
import com.kidou.game.viewmodel.GameUiState

private enum class DictFilter {
  ALL,
  UNREGISTERED,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySheet(
  uiState: GameUiState,
  onSelectTab: (DictionaryTab) -> Unit,
  onEditWord: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val data = uiState.gameData ?: return
  val palette = KidouTheme.palette
  val strings = stringsFor(uiState.language)
  val stats = uiState.translationStats
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var filterMode by remember { mutableStateOf(DictFilter.ALL) }
  var tagFilter by remember { mutableStateOf<WordColorTag?>(null) }

  val baseWordIds: List<String> =
    when (uiState.dictionaryTab) {
      DictionaryTab.SCREEN -> uiState.screenWordIds
      DictionaryTab.ALL -> data.wordOrder.filter { it in uiState.discoveredWordIds }
    }

  val displayedWordIds =
    baseWordIds.filter { wordId ->
      val entry = uiState.dictionary[wordId]
      val wordDef = data.words[wordId]
      val resolved = entry?.resolvedTranslation(wordDef, uiState.language).orEmpty()
      val matchesUnreg = filterMode == DictFilter.ALL || resolved.isBlank()
      val matchesTag =
        tagFilter == null || (entry?.colorTag ?: WordColorTag.UNCLASSIFIED) == tagFilter
      matchesUnreg && matchesTag
    }

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
          .fillMaxHeight(0.84f)
          .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = strings.dictionary,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = palette.glyph,
        )
        Text(
          text = strings.registeredStats(stats.translatedCount, stats.totalCount, stats.percent),
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp,
          color = palette.accentPrimary,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TabPill(
          label = "${strings.tabThisScreen} (${uiState.screenWordIds.size})",
          selected = uiState.dictionaryTab == DictionaryTab.SCREEN,
          onClick = { onSelectTab(DictionaryTab.SCREEN) },
          modifier = Modifier.weight(1f),
        )
        TabPill(
          label = "${strings.tabAllWords} (${uiState.discoveredWordIds.size})",
          selected = uiState.dictionaryTab == DictionaryTab.ALL,
          onClick = { onSelectTab(DictionaryTab.ALL) },
          modifier = Modifier.weight(1f),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilterChipBox(
          label = strings.filterAll,
          selected = filterMode == DictFilter.ALL && tagFilter == null,
          color = palette.glyph,
          onClick = {
            filterMode = DictFilter.ALL
            tagFilter = null
          },
        )
        FilterChipBox(
          label = strings.filterUnregistered,
          selected = filterMode == DictFilter.UNREGISTERED,
          color = palette.accentPrimary,
          onClick = {
            filterMode =
              if (filterMode == DictFilter.UNREGISTERED) DictFilter.ALL else DictFilter.UNREGISTERED
          },
        )
        WordColorTag.entries.forEach { tag ->
          FilterChipBox(
            label = tag.label(uiState.language),
            selected = tagFilter == tag,
            color = palette.tagColor(tag),
            onClick = { tagFilter = if (tagFilter == tag) null else tag },
          )
        }
      }

      LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(displayedWordIds, key = { it }) { wordId ->
          DictionaryWordRow(
            grid = data.glyphs[wordId],
            wordDef = data.words[wordId],
            entry = uiState.dictionary[wordId],
            language = uiState.language,
            strings = strings,
            onClick = { onEditWord(wordId) },
          )
        }
      }
    }
  }
}

@Composable
private fun TabPill(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = KidouTheme.palette
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .background(
          if (selected) palette.accentPrimary.copy(alpha = 0.16f) else palette.tile,
          RectangleShape,
        )
        .border(
          1.5.dp,
          if (selected) palette.accentPrimary else palette.border,
          RectangleShape,
        )
        .clickable(onClick = onClick)
        .padding(vertical = 8.dp),
  ) {
    Text(
      text = label,
      color = if (selected) palette.accentPrimary else palette.muted,
      fontFamily = FontFamily.Monospace,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
      fontSize = 12.sp,
    )
  }
}

@Composable
private fun FilterChipBox(
  label: String,
  selected: Boolean,
  color: Color,
  onClick: () -> Unit,
) {
  val palette = KidouTheme.palette
  Box(
    modifier =
      Modifier
        .background(if (selected) color.copy(alpha = 0.18f) else palette.panelSunken, RectangleShape)
        .border(
          1.dp,
          if (selected) color else palette.border,
          RectangleShape,
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    Text(
      text = label,
      color = if (selected) color else palette.muted,
      fontFamily = FontFamily.Monospace,
      fontSize = 11.sp,
    )
  }
}

@Composable
private fun DictionaryWordRow(
  grid: GlyphGrid?,
  wordDef: WordDef?,
  entry: DictionaryEntry?,
  language: AppLanguage,
  strings: UiStrings,
  onClick: () -> Unit,
) {
  val palette = KidouTheme.palette
  val tag = entry?.colorTag ?: WordColorTag.UNCLASSIFIED
  val tagColor = palette.tagColor(tag)
  val translation = entry?.resolvedTranslation(wordDef, language)?.trim().orEmpty()
  val memo = entry?.memo?.trim().orEmpty()
  val sourceBadge =
    when (entry?.source) {
      EntrySource.DEFAULT_SET -> strings.defaultSetBadge
      EntrySource.AD_REVEALED -> strings.adRevealedBadge
      EntrySource.DEBUG_BULK -> strings.debugBulkBadge
      else -> null
    }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.tile, RectangleShape)
        .border(1.dp, palette.border, RectangleShape)
        .clickable(onClick = onClick)
        .padding(10.dp),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier
          .size(44.dp)
          .background(palette.panelSunken, RectangleShape)
          .border(1.dp, tagColor.copy(alpha = 0.55f), RectangleShape),
    ) {
      PixelGlyph(grid = grid, size = 30.dp, color = palette.glyph)
    }

    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = translation.ifEmpty { strings.unregistered },
          color = if (translation.isNotEmpty()) palette.glyph else palette.muted,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (tag != WordColorTag.UNCLASSIFIED) {
          Text(
            text = tag.label(language),
            color = tagColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier =
              Modifier
                .border(1.dp, tagColor.copy(alpha = 0.6f), RectangleShape)
                .padding(horizontal = 4.dp, vertical = 1.dp),
          )
        }
        if (sourceBadge != null) {
          Text(
            text = sourceBadge,
            color = palette.accentSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier =
              Modifier
                .border(1.dp, palette.accentSecondary.copy(alpha = 0.55f), RectangleShape)
                .padding(horizontal = 4.dp, vertical = 1.dp),
          )
        }
      }
      if (memo.isNotEmpty()) {
        Text(
          text = memo,
          color = palette.muted,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Text(
      text = strings.edit,
      color = palette.accentPrimary,
      fontFamily = FontFamily.Monospace,
      fontSize = 11.sp,
    )
  }
}
