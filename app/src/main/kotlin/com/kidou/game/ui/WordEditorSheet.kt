package com.kidou.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.model.DictionaryEntry
import com.kidou.game.model.GlyphGrid
import com.kidou.game.model.WordColorTag
import com.kidou.game.ui.components.GlyphSentenceRow
import com.kidou.game.ui.components.PixelGlyph
import com.kidou.game.ui.theme.KidouColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordEditorSheet(
  wordId: String,
  glyphs: Map<String, GlyphGrid>,
  dictionary: Map<String, DictionaryEntry>,
  contexts: List<List<List<String>>>,
  onSave: (String, WordColorTag, String) -> Unit,
  onSwitchWord: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val existing = dictionary[wordId]
  var translation by remember(wordId) { mutableStateOf(existing?.translation ?: "") }
  var selectedTag by remember(wordId) {
    mutableStateOf(existing?.colorTag ?: WordColorTag.UNCLASSIFIED)
  }
  var memo by remember(wordId) { mutableStateOf(existing?.memo ?: "") }

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp)
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier
              .size(76.dp)
              .background(KidouColors.PanelSunken, RoundedCornerShape(2.dp))
              .border(1.dp, Color(selectedTag.colorHex).copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
        ) {
          PixelGlyph(
            grid = glyphs[wordId],
            size = 56.dp,
            color = KidouColors.Glyph,
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "語彙メモ",
            color = KidouColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
          )
          Text(
            text = if (translation.isNotBlank()) translation.trim() else "未登録",
            color = if (translation.isNotBlank()) Color(selectedTag.colorHex) else KidouColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
          )
          Text(
            text = "自分なりの訳を登録できる（空欄可）",
            color = KidouColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
          )
        }
      }

      OutlinedTextField(
        value = translation,
        onValueChange = { translation = it },
        label = { Text("訳語（ルビ表示）", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = KidouColors.Amber,
            unfocusedBorderColor = KidouColors.Border,
            focusedTextColor = KidouColors.Glyph,
            unfocusedTextColor = KidouColors.Glyph,
            cursorColor = KidouColors.Amber,
          ),
      )

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "分類・確信度",
          color = KidouColors.Muted,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          WordColorTag.entries.forEach { tag ->
            val isSelected = tag == selectedTag
            val tagColor = Color(tag.colorHex)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier =
                Modifier
                  .clip(RoundedCornerShape(2.dp))
                  .background(if (isSelected) tagColor.copy(alpha = 0.2f) else KidouColors.Tile)
                  .border(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) tagColor else KidouColors.Border,
                    shape = RoundedCornerShape(2.dp),
                  )
                  .clickable { selectedTag = tag }
                  .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
              Box(
                modifier =
                  Modifier
                    .size(8.dp)
                    .background(tagColor),
              )
              Text(
                text = tag.label,
                color = if (isSelected) tagColor else KidouColors.Glyph,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              )
            }
          }
        }
      }

      OutlinedTextField(
        value = memo,
        onValueChange = { memo = it },
        label = { Text("メモ・仮説", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = KidouColors.Amber,
            unfocusedBorderColor = KidouColors.Border,
            focusedTextColor = KidouColors.Glyph,
            unfocusedTextColor = KidouColors.Glyph,
            cursorColor = KidouColors.Amber,
          ),
      )

      if (contexts.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "出現文脈",
            color = KidouColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
          )
          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .background(KidouColors.PanelSunken, RoundedCornerShape(2.dp))
                .border(1.dp, KidouColors.Border, RoundedCornerShape(2.dp))
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            contexts.forEach { sentence ->
              GlyphSentenceRow(
                slots = sentence,
                glyphs = glyphs,
                dictionary = dictionary,
                onWordClick = onSwitchWord,
                compact = true,
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = {
            translation = ""
            selectedTag = WordColorTag.UNCLASSIFIED
            memo = ""
          },
          shape = RoundedCornerShape(2.dp),
          modifier = Modifier.weight(1f),
        ) {
          Text("クリア", fontFamily = FontFamily.Monospace, color = KidouColors.Muted)
        }
        Button(
          onClick = { onSave(translation, selectedTag, memo) },
          shape = RoundedCornerShape(2.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = KidouColors.Amber,
              contentColor = KidouColors.Background,
            ),
          modifier = Modifier.weight(2f),
        ) {
          Text("保存", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
    }
  }
}
