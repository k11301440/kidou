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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.model.DictionaryEntry
import com.kidou.game.model.GlyphGrid
import com.kidou.game.model.WordColorTag
import com.kidou.game.ui.components.PixelGlyph
import com.kidou.game.ui.theme.KidouColors
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
      val matchesUnreg =
        filterMode == DictFilter.ALL || entry == null || entry.translation.isBlank()
      val matchesTag =
        tagFilter == null || (entry?.colorTag ?: WordColorTag.UNCLASSIFIED) == tagFilter
      matchesUnreg && matchesTag
    }

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
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "辞書",
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = KidouColors.Glyph,
        )
        Text(
          text =
            "登録 ${uiState.registeredCount} / ${uiState.discoveredWordIds.size}語 (${uiState.decodeRatePercent}%)",
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp,
          color = KidouColors.Amber,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TabPill(
          label = "この画面 (${uiState.screenWordIds.size})",
          selected = uiState.dictionaryTab == DictionaryTab.SCREEN,
          onClick = { onSelectTab(DictionaryTab.SCREEN) },
          modifier = Modifier.weight(1f),
        )
        TabPill(
          label = "全単語 (${uiState.discoveredWordIds.size})",
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
          label = "すべて",
          selected = filterMode == DictFilter.ALL && tagFilter == null,
          color = KidouColors.Glyph,
          onClick = {
            filterMode = DictFilter.ALL
            tagFilter = null
          },
        )
        FilterChipBox(
          label = "未登録",
          selected = filterMode == DictFilter.UNREGISTERED,
          color = KidouColors.Amber,
          onClick = {
            filterMode =
              if (filterMode == DictFilter.UNREGISTERED) DictFilter.ALL else DictFilter.UNREGISTERED
          },
        )
        WordColorTag.entries.forEach { tag ->
          FilterChipBox(
            label = tag.label,
            selected = tagFilter == tag,
            color = Color(tag.colorHex),
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
            entry = uiState.dictionary[wordId],
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
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .clip(RoundedCornerShape(2.dp))
        .background(if (selected) KidouColors.Amber.copy(alpha = 0.16f) else KidouColors.Tile)
        .border(
          1.dp,
          if (selected) KidouColors.Amber else KidouColors.Border,
          RoundedCornerShape(2.dp),
        )
        .clickable(onClick = onClick)
        .padding(vertical = 8.dp),
  ) {
    Text(
      text = label,
      color = if (selected) KidouColors.Amber else KidouColors.Muted,
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
  Box(
    modifier =
      Modifier
        .clip(RoundedCornerShape(2.dp))
        .background(if (selected) color.copy(alpha = 0.18f) else KidouColors.PanelSunken)
        .border(
          1.dp,
          if (selected) color else KidouColors.Border,
          RoundedCornerShape(2.dp),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    Text(
      text = label,
      color = if (selected) color else KidouColors.Muted,
      fontFamily = FontFamily.Monospace,
      fontSize = 11.sp,
    )
  }
}

@Composable
private fun DictionaryWordRow(
  grid: GlyphGrid?,
  entry: DictionaryEntry?,
  onClick: () -> Unit,
) {
  val tag = entry?.colorTag ?: WordColorTag.UNCLASSIFIED
  val tagColor = Color(tag.colorHex)
  val translation = entry?.translation?.trim().orEmpty()
  val memo = entry?.memo?.trim().orEmpty()

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(2.dp))
        .background(KidouColors.Tile)
        .border(1.dp, KidouColors.Border, RoundedCornerShape(2.dp))
        .clickable(onClick = onClick)
        .padding(10.dp),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier
          .size(44.dp)
          .background(KidouColors.PanelSunken, RoundedCornerShape(2.dp))
          .border(1.dp, tagColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
    ) {
      PixelGlyph(grid = grid, size = 30.dp, color = KidouColors.Glyph)
    }

    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = if (translation.isNotEmpty()) translation else "未登録",
          color = if (translation.isNotEmpty()) KidouColors.Glyph else KidouColors.Muted,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (tag != WordColorTag.UNCLASSIFIED) {
          Text(
            text = tag.label,
            color = tagColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier =
              Modifier
                .border(1.dp, tagColor.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
          )
        }
      }
      if (memo.isNotEmpty()) {
        Text(
          text = memo,
          color = KidouColors.Muted,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Text(
      text = "編集",
      color = KidouColors.Amber,
      fontFamily = FontFamily.Monospace,
      fontSize = 11.sp,
    )
  }
}
