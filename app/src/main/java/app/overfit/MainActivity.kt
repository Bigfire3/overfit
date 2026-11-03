package app.overfit

import android.graphics.Color
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.overfit.platform.AndroidRestAlerts
import app.overfit.platform.AppVisibility
import app.overfit.platform.EXTRA_OPEN_WORKOUT
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.overfit.ui.OverfitApp
import app.overfit.ui.theme.isDark

class MainActivity : ComponentActivity() {
    // Notification permission (Android 13+), asked when the first workout starts.
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ((application as OverfitApp).dependencies.restAlerts as? AndroidRestAlerts)?.onPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val deps = (application as OverfitApp).dependencies
        (deps.restAlerts as? AndroidRestAlerts)?.permissionRequester = { permissionLauncher.launch(it) }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    /** Opened link (https or overfit://) or `.overfit` file → text for the import screen. */
    private fun handleIncoming(intent: Intent?) {
        // Tap on the rest/workout notification: always the maximized workout, consumed once.
        if (intent?.getBooleanExtra(EXTRA_OPEN_WORKOUT, false) == true) {
            intent.removeExtra(EXTRA_OPEN_WORKOUT)
            (application as OverfitApp).dependencies.requestOpenWorkout()
        }
        val text = IncomingShare.textOf(this, intent)
        if (text != null) (application as OverfitApp).dependencies.inbox.offer(text)
        // Handled once: a rotation or recreate must not import the same link again.
        intent?.data = null
        intent?.removeExtra(Intent.EXTRA_STREAM)
        intent?.removeExtra(Intent.EXTRA_TEXT)
    }
}

/** What an opened link, a tapped `.overfit` file (VIEW) or a file shared to Overfit (SEND, e.g. from WhatsApp) carries. */
object IncomingShare {
    private const val MAX_FILE_BYTES = 300 * 1024

    fun textOf(context: Context, intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data?.let { read(context, it) }
        Intent.ACTION_SEND -> {
            val stream = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            stream?.let { read(context, it) } ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        }
        else -> null
    }

    private fun read(context: Context, uri: Uri): String? = when (uri.scheme) {
        "content", "file" -> runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.takeIf { it.size <= MAX_FILE_BYTES }?.decodeToString()
        }.getOrNull()
        else -> uri.toString()
    }
}
