package app.overfit.ui.main

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.overfit.data.AppState
import app.overfit.domain.formatWeight
import app.overfit.domain.latestWeight
import app.overfit.i18n.LocalStrings
import app.overfit.domain.TemplateSummary
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.exercises.TemplateRow
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.datetime.LocalDate

@Composable
fun StartScreen(
    state: AppState,
    templates: List<TemplateSummary>?,
    activeWorkout: Boolean,
    onStartWorkout: () -> Unit,
    onOpenTemplate: (Long) -> Unit,
    onNewTemplate: () -> Unit,
) {
    val t = LocalStrings.current
    val latest = latestWeight(state.weights)
    Screen(
        scroll = true,
        footer = {
            PrimaryButton(if (activeWorkout) t.continueWorkout else t.startWorkout, onClick = onStartWorkout)
        },
    ) {
        Text(t.greeting(state.profile.name), style = Theme.type.label, tone = Tone.Muted)
        Spacer(Modifier.height(Space.xl))
        if (latest != null) {
            Text(
                formatWeight(latest.kg, state.profile.unit, state.language),
                Modifier.semantics { heading() },
                style = Theme.type.hero,
            )
            Spacer(Modifier.height(Space.s))
            Text(t.asOf(t.date(LocalDate.parse(latest.date))), style = Theme.type.caption, tone = Tone.Muted)
        } else {
            Text(t.noWeight, style = Theme.type.title, tone = Tone.Muted)
        }

        SectionTitle(t.templates)
        if (templates != null && templates.isEmpty()) Text(t.templatesEmpty, tone = Tone.Muted)
        templates?.forEach { TemplateRow(it) { onOpenTemplate(it.id) } }
        if (templates != null) ListRow(t.newTemplate, onClick = onNewTemplate) { LineIcon(Icon.Plus) }
        SectionTitle(t.coachTips)
        Text(t.coachTipsEmpty, tone = Tone.Muted)
    }
}

@Composable
private fun EmptyTab(title: String, body: String) {
    Screen {
        Title(title)
        Spacer(Modifier.height(Space.l))
        Text(body, tone = Tone.Muted)
    }
}

@Composable
fun HistoryScreen() = LocalStrings.current.let { EmptyTab(it.historyTitle, it.historyEmpty) }

@Composable
fun PhotosScreen() = LocalStrings.current.let { EmptyTab(it.photosTitle, it.photosEmpty) }
