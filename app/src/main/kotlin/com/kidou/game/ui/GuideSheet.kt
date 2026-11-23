package com.kidou.game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.ui.components.GlyphSentenceRow
import com.kidou.game.ui.components.PixelCrtDisplay
import com.kidou.game.ui.i18n.stringsFor
import com.kidou.game.ui.theme.KidouColors
import com.kidou.game.viewmodel.GameUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideSheet(
  uiState: GameUiState,
  onEditWord: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val data = uiState.gameData ?: return
  val strings = stringsFor(uiState.language)
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = KidouColors.Panel,
    contentColor = KidouColors.Glyph,
    shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = strings.guideTitle,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = KidouColors.Green,
      )

      PixelCrtDisplay(borderColor = KidouColors.Green.copy(alpha = 0.7f)) {
        data.boardCollection.guideLines.forEach { lineSlots ->
          GlyphSentenceRow(
            slots = lineSlots,
            glyphs = data.glyphs,
            words = data.words,
            dictionary = uiState.dictionary,
            language = uiState.language,
            onWordClick = onEditWord,
          )
        }
      }

      Text(
        text = strings.guideNote,
        color = KidouColors.Muted,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
      )
    }
  }
}
