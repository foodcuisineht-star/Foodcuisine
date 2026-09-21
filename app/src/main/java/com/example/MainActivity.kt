package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.db.RestaurantDatabase
import com.example.data.repository.RestaurantRepository
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.RestaurantViewModel
import com.example.ui.viewmodel.RestaurantViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val database = RestaurantDatabase.getDatabase(applicationContext)
    val repository = RestaurantRepository(database.restaurantDao())
    val factory = RestaurantViewModelFactory(repository)

    setContent {
      val viewModel: RestaurantViewModel = viewModel(factory = factory)
      // Proactively trigger loadSettings on start
      viewModel.loadSettings(applicationContext)
      val screenMode by viewModel.screenMode.collectAsStateWithLifecycle()
      val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()
      val hideSystemBars by viewModel.hideSystemBars.collectAsStateWithLifecycle()

      // Dynamically toggle Immersive Fullscreen Mode to hide the 3-button system bottom bar
      androidx.compose.runtime.LaunchedEffect(hideSystemBars) {
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (hideSystemBars) {
          windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
          windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
          windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
      }

      MyApplicationTheme(screenMode = screenMode, colorTheme = colorTheme) {
        MainScreen(viewModel = viewModel)
      }
    }
  }
}

