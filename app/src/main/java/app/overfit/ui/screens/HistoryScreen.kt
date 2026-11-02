package app.overfit.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.overfit.R
import app.overfit.ui.components.EmptyState
import app.overfit.ui.components.OverfitScreen

@Composable
fun HistoryScreen() {
    OverfitScreen(title = stringResource(R.string.history_title)) { padding ->
        EmptyState(stringResource(R.string.history_empty), Modifier.padding(padding))
    }
}
