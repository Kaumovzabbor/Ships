package com.friends.ships

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.friends.ships.ui.ShipsApp
import com.friends.ships.ui.ShipsViewModel
import com.friends.ships.ui.theme.ShipsTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 27, 45)
        window.navigationBarColor = android.graphics.Color.rgb(7, 27, 45)
        setContent {
            val vm: ShipsViewModel = viewModel()
            val ui by vm.ui.collectAsStateWithLifecycle()
            val dark = when (ui.settings.theme) {
                "light" -> false
                "dark" -> true
                else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            val activity = LocalContext.current as Activity
            SideEffect {
                if (ui.screen == com.friends.ships.ui.AppScreen.BATTLE) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            ShipsTheme(darkTheme = dark) { ShipsApp(ui = ui, viewModel = vm) }
        }
    }
}
