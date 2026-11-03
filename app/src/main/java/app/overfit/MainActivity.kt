package app.overfit

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.overfit.ui.OverfitApp
import app.overfit.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val deps = (application as OverfitApp).dependencies
        handleIncoming(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    /** Opened link (https or overfit://) or `.overfit` file → text for the import screen. */
    private fun handleIncoming(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val text = when (uri.scheme) {
            "content", "file" -> runCatching {
                contentResolver.openInputStream(uri)?.use { it.readBytes().takeIf { b -> b.size <= MAX_FILE_BYTES }?.decodeToString() }
            }.getOrNull()
            else -> uri.toString()
        }
        if (text != null) (application as OverfitApp).dependencies.inbox.offer(text)
        // Handled once: a rotation or recreate must not import the same link again.
        intent.data = null
    }

    private companion object {
        const val MAX_FILE_BYTES = 300 * 1024
    }
}
