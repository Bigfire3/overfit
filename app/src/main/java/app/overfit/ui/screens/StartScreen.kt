package app.overfit.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.overfit.R
import app.overfit.ui.components.OverfitScreen
import app.overfit.ui.components.ScreenContentSpacing

@Composable
fun StartScreen() {
    OverfitScreen(title = stringResource(R.string.start_title)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = ScreenContentSpacing,
        ) {
            // Workout-Modus folgt in Phase 2.
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    stringResource(R.string.start_button_workout),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            SectionCard(
                title = stringResource(R.string.start_templates),
                body = stringResource(R.string.start_templates_empty),
            )
            SectionCard(
                title = stringResource(R.string.start_coach_tips),
                body = stringResource(R.string.start_coach_tips_empty),
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
