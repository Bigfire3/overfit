package app.overfit

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.overfit.platform.AndroidRestAlerts
import app.overfit.platform.AppVisibility
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.overfit.ui.OverfitApp
import app.overfit.ui.theme.isDark

class MainActivity : ComponentActivity() {
    // Notification permission (Android 13+), asked when the first workout starts.
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val deps = (application as OverfitApp).dependencies
        (deps.restAlerts as? AndroidRestAlerts)?.permissionRequester = { permissionLauncher.launch(it) }
        setContent {
            val state by deps.store.state.collectAsState()
            val dark = state.settings.theme.isDark()
            // System bar icons follow the in-app theme (dark by default).
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            OverfitApp(deps)
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.visible = true
    }

    override fun onStop() {
        AppVisibility.visible = false
        super.onStop()
    }

    override fun onDestroy() {
        // The next activity instance (e.g. after rotation) registers its own launcher in onCreate.
        ((application as OverfitApp).dependencies.restAlerts as? AndroidRestAlerts)?.permissionRequester = null
        super.onDestroy()
    }
}
