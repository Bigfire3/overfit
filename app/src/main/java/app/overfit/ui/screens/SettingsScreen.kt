package app.overfit.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
            SettingsItem(stringResource(R.string.settings_offline_images), soon)
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

/** Einstellungszeile; ohne [onClick] ausgegraut (Funktion folgt später). */
@Composable
private fun SettingsItem(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.alpha(0.5f),
    )
}
