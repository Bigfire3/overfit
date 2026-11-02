package app.overfit.ui.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overfit.domain.TemplateSummary
import app.overfit.ui.appViewModelFactory
import app.overfit.ui.components.EmptyState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TemplateListViewModel(templates: kotlinx.coroutines.flow.Flow<List<TemplateSummary>>) : ViewModel() {
    /** null = lädt noch. */
    val templates: StateFlow<List<TemplateSummary>?> =
        templates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = appViewModelFactory { c, _ -> TemplateListViewModel(c.templateRepository.templates) }
    }
}

@Composable
fun TemplateList(
    templates: List<TemplateSummary>?,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        templates == null -> Unit
        templates.isEmpty() -> EmptyState(
            "Noch keine Vorlagen. Lege mit „Neue Vorlage“ deine erste an, z. B. „Push“ oder „Ganzkörper A“.",
            modifier,
        )
        else -> LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(templates, key = { it.id }) { TemplateCard(it, onClick = { onOpen(it.id) }) }
        }
    }
}

@Composable
fun TemplateCard(template: TemplateSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(template.name, style = MaterialTheme.typography.titleMedium)
            val count = template.exerciseNames.size
            Text(
                if (count == 0) "Noch keine Übungen"
                else "$count ${if (count == 1) "Übung" else "Übungen"} · ${template.exerciseNames.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
