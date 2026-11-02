package app.overfit.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.overfit.data.images.OfflineImagesWorker
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.overfit.R
import app.overfit.ui.components.OverfitScreen

@Composable
fun SettingsScreen(onOpenCoachMemory: () -> Unit, onOpenAbout: () -> Unit) {
    val soon = stringResource(R.string.coming_soon)
    OverfitScreen(title = stringResource(R.string.settings_title)) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_section_coach))
            SettingsItem(
                stringResource(R.string.settings_coach_memory),
                stringResource(R.string.settings_coach_memory_desc),
                onClick = onOpenCoachMemory,
            )
            SettingsItem(stringResource(R.string.settings_api_key), soon)

            SectionHeader(stringResource(R.string.settings_section_workout))
            SettingsItem(stringResource(R.string.settings_rest_default), soon)

            SectionHeader(stringResource(R.string.settings_section_data))
            OfflineImagesItem()
            SettingsItem(stringResource(R.string.settings_backup), soon)

            SectionHeader(stringResource(R.string.settings_section_app))
            SettingsItem(
                stringResource(R.string.settings_about),
                stringResource(R.string.settings_about_desc),
                onClick = onOpenAbout,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** „Alle Bilder offline laden“ mit Fortschritt aus WorkManager. */
@Composable
private fun OfflineImagesItem() {
    val context = LocalContext.current
    val info by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(OfflineImagesWorker.WORK_NAME) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val work = info.firstOrNull()
    // Anzahl gespeicherter Dateien bei jedem Statuswechsel neu zählen.
    val stored = remember(work?.state) { OfflineImagesWorker.countOffline(context) }
    val running = work?.state == WorkInfo.State.RUNNING || work?.state == WorkInfo.State.ENQUEUED
    val subtitle = when (work?.state) {
        WorkInfo.State.RUNNING -> stringResource(
            R.string.settings_offline_images_running,
            work.progress.getInt(OfflineImagesWorker.KEY_DONE, 0),
            work.progress.getInt(OfflineImagesWorker.KEY_TOTAL, 0),
        )
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> stringResource(R.string.settings_offline_images_waiting)
        WorkInfo.State.SUCCEEDED -> stringResource(R.string.settings_offline_images_done, stored)
        WorkInfo.State.FAILED -> stringResource(
            R.string.settings_offline_images_failed,
            work.outputData.getInt(OfflineImagesWorker.KEY_DONE, stored),
            work.outputData.getInt(OfflineImagesWorker.KEY_TOTAL, 0),
            work.outputData.getInt(OfflineImagesWorker.KEY_FAILED, 0),
        )
        else -> stringResource(R.string.settings_offline_images_idle, stored)
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_offline_images)) },
        supportingContent = {
            Column {
                Text(subtitle)
                if (work?.state == WorkInfo.State.RUNNING) {
                    val total = work.progress.getInt(OfflineImagesWorker.KEY_TOTAL, 0)
                    val done = work.progress.getInt(OfflineImagesWorker.KEY_DONE, 0)
                    LinearProgressIndicator(
                        progress = { if (total > 0) done / total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        modifier = Modifier.clickable(enabled = !running) { OfflineImagesWorker.start(context) },
    )
}

/** Einstellungszeile; ohne [onClick] ausgegraut (Funktion folgt später). */
@Composable
private fun SettingsItem(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.alpha(0.5f),
    )
}
