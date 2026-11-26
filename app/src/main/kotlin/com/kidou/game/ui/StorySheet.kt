package com.kidou.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.engine.Base5
import com.kidou.game.ui.components.GlyphSentenceRow
import com.kidou.game.ui.components.PixelCrtDisplay
import com.kidou.game.ui.components.PixelGlyph
import com.kidou.game.ui.i18n.stringsFor
import com.kidou.game.ui.theme.DotGothic16FontFamily
import com.kidou.game.ui.theme.KidouTheme
import com.kidou.game.viewmodel.GameUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorySheet(
  uiState: GameUiState,
  onEditWord: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val data = uiState.gameData ?: return
  val palette = KidouTheme.palette
  val boardDef = data.boardCollection.boardById(uiState.selectedBoardId)
  val strings = stringsFor(uiState.language)
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = palette.panel,
    contentColor = palette.glyph,
    shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Glyph-only board badge (§5: removed plain decorative heading & meta note)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier =
            Modifier
              .background(palette.tile, RectangleShape)
              .border(1.5.dp, palette.accentSecondary.copy(alpha = 0.7f), RectangleShape)
              .semantics { contentDescription = "story_board_glyph_${boardDef.id}" }
              .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
          PixelGlyph(grid = data.glyphs["n_board"], size = 16.dp, color = palette.glyph)
          Base5.toDigitWordIds(boardDef.id).forEach { dId ->
            PixelGlyph(grid = data.glyphs[dId], size = 16.dp, color = palette.accentSecondary)
          }
        }
      }

      PixelCrtDisplay(borderColor = palette.accentSecondary.copy(alpha = 0.7f)) {
        boardDef.storyLines.forEach { lineSlots ->
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
    }
  }
}
