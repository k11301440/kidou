package com.kidou.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kidou.game.ui.MainScreen
import com.kidou.game.ui.theme.KIDOUTheme
import com.kidou.game.ui.theme.KidouColors
import com.kidou.game.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0B0D0E")),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0B0D0E")),
    )

    setContent {
      KIDOUTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = KidouColors.Background,
        ) {
          val vm: GameViewModel = viewModel()
          MainScreen(viewModel = vm)
        }
      }
    }
  }
}
