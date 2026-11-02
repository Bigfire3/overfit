package app.overfit.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.overfit.R
import app.overfit.ui.components.EmptyState
import app.overfit.ui.components.OverfitScreen

@Composable
fun CoachMemoryScreen(onBack: () -> Unit) {
    OverfitScreen(title = stringResource(R.string.coach_memory_title), onBack = onBack) { padding ->
        EmptyState(stringResource(R.string.coach_memory_empty), Modifier.padding(padding))
    }
}
