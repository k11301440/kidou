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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.ads.AdLoadState
import com.kidou.game.model.AppLanguage
import com.kidou.game.model.DictionaryEntry
import com.kidou.game.model.EntrySource
import com.kidou.game.model.GlyphGrid
import com.kidou.game.model.WordColorTag
import com.kidou.game.model.WordDef
import com.kidou.game.ui.components.GlyphSentenceRow
import com.kidou.game.ui.components.PixelGlyph
import com.kidou.game.ui.i18n.stringsFor
import com.kidou.game.ui.theme.KidouColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordEditorSheet(
  wordId: String,
  glyphs: Map<String, GlyphGrid>,
  words: Map<String, WordDef>,
  dictionary: Map<String, DictionaryEntry>,
  language: AppLanguage,
  contexts: List<List<List<String>>>,
  adLoadState: AdLoadState,
  adErrorToast: Boolean,
  onWatchAdReveal: () -> Unit,
  onSave: (String, WordColorTag, String) -> Unit,
  onSwitchWord: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = stringsFor(language)
  val wordDef = words[wordId]
  val existing = dictionary[wordId]
  val initialText = existing?.resolvedTranslation(wordDef, language).orEmpty()

  var translationField by remember(wordId) {
    mutableStateOf(TextFieldValue(text = initialText, selection = TextRange(initialText.length)))
  }
  var selectedTag by remember(wordId) {
    mutableStateOf(existing?.colorTag ?: WordColorTag.UNCLASSIFIED)
  }
  var memo by remember(wordId) { mutableStateOf(existing?.memo ?: "") }

  // Sync if ad reveal updates the entry while sheet is open
  val latestResolved = existing?.resolvedTranslation(wordDef, language).orEmpty()
  LaunchedEffect(existing?.source, latestResolved) {
    if (existing?.source == EntrySource.AD_REVEALED && latestResolved.isNotEmpty()) {
      translationField = TextFieldValue(latestResolved, TextRange(latestResolved.length))
      selectedTag = existing.colorTag
    }
  }

  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  LaunchedEffect(wordId) {
    delay(150)
    runCatching {
      focusRequester.requestFocus()
      keyboardController?.show()
    }
  }

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
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp)
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
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
              .size(72.dp)
              .background(KidouColors.PanelSunken, RectangleShape)
              .border(2.dp, Color(selectedTag.colorHex).copy(alpha = 0.75f), RectangleShape),
        ) {
          PixelGlyph(
            grid = glyphs[wordId],
            size = 52.dp,
            color = KidouColors.Glyph,
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = strings.wordMemoTitle,
            color = KidouColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
          )
          Text(
            text = translationField.text.trim().ifEmpty { strings.unregistered },
            color =
              if (translationField.text.isNotBlank()) Color(selectedTag.colorHex)
              else KidouColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
          )
          Text(
            text = strings.wordMemoHint,
            color = KidouColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
          )
        }
      }

      OutlinedTextField(
        value = translationField,
        onValueChange = { translationField = it },
        label = {
          Text(strings.translationFieldLabel, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        },
        singleLine = true,
        modifier =
          Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { contentDescription = "word_translation_input" },
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = KidouColors.Amber,
            unfocusedBorderColor = KidouColors.Border,
            focusedTextColor = KidouColors.Glyph,
            unfocusedTextColor = KidouColors.Glyph,
            cursorColor = KidouColors.Amber,
          ),
      )

      // Rewarded Ad button to reveal expected translation (player-facing feature, §7)
      val isDefaultWord = wordDef?.isDefaultSet == true
      val isAdRevealed = existing?.source == EntrySource.AD_REVEALED
      if (!isDefaultWord) {
        val adButtonLabel =
          when {
            isAdRevealed -> strings.adAlreadyRevealed
            adLoadState == AdLoadState.READY -> strings.watchAdForMeaning
            adLoadState == AdLoadState.LOADING -> strings.adLoading
            else -> strings.adUnavailableTapRetry
          }
        OutlinedButton(
          onClick = onWatchAdReveal,
          enabled = !isAdRevealed && adLoadState != AdLoadState.LOADING,
          shape = RectangleShape,
          modifier =
            Modifier
              .fillMaxWidth()
              .semantics { contentDescription = "watch_ad_reveal_btn" },
        ) {
          Text(
            text = adButtonLabel,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (isAdRevealed) KidouColors.Green else KidouColors.Cyan,
          )
        }
        if (adErrorToast) {
          Text(
            text = strings.adUnavailableTapRetry,
            color = KidouColors.Red,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
          )
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = strings.tagSectionLabel,
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
                  .background(if (isSelected) tagColor.copy(alpha = 0.2f) else KidouColors.Tile, RectangleShape)
                  .border(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) tagColor else KidouColors.Border,
                    shape = RectangleShape,
                  )
                  .clickable { selectedTag = tag }
                  .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
              Box(modifier = Modifier.size(8.dp).background(tagColor))
              Text(
                text = tag.label(language),
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
        label = { Text(strings.memoFieldLabel, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
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
            text = strings.contextsLabel,
            color = KidouColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
          )
          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
                .background(KidouColors.PanelSunken, RectangleShape)
                .border(1.dp, KidouColors.Border, RectangleShape)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            contexts.forEach { sentence ->
              GlyphSentenceRow(
                slots = sentence,
                glyphs = glyphs,
                words = words,
                dictionary = dictionary,
                language = language,
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
            translationField = TextFieldValue("")
            selectedTag = WordColorTag.UNCLASSIFIED
            memo = ""
          },
          shape = RectangleShape,
          modifier = Modifier.weight(1f),
        ) {
          Text(strings.clear, fontFamily = FontFamily.Monospace, color = KidouColors.Muted)
        }
        Button(
          onClick = { onSave(translationField.text, selectedTag, memo) },
          shape = RectangleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = KidouColors.Amber,
              contentColor = KidouColors.Background,
            ),
          modifier = Modifier.weight(2f).semantics { contentDescription = "word_save_btn" },
        ) {
          Text(strings.save, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
    }
  }
}
