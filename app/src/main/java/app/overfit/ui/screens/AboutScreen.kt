package app.overfit.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.overfit.BuildConfig
import app.overfit.R
import app.overfit.ui.components.OverfitScreen
import app.overfit.ui.components.ScreenContentSpacing

@Composable
fun AboutScreen(onBack: () -> Unit) {
    OverfitScreen(title = stringResource(R.string.about_title), onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = ScreenContentSpacing,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Heading(stringResource(R.string.about_section_attribution))
            Body(stringResource(R.string.about_workout_guide))
            Body(stringResource(R.string.about_free_exercise_db))

            Heading(stringResource(R.string.about_section_libraries))
            Body(stringResource(R.string.about_libraries))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}
