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
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.datetime.LocalDate

@Composable
fun StartScreen(state: AppState) {
    val t = LocalStrings.current
    val latest = latestWeight(state.weights)
    Screen(
        scroll = true,
        // Workout mode follows in a later phase.
        footer = { PrimaryButton(t.startWorkout, onClick = {}, enabled = false) },
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
        Text(t.templatesEmpty, tone = Tone.Muted)
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
fun ExercisesScreen() = LocalStrings.current.let { EmptyTab(it.exercisesTitle, it.exercisesEmpty) }

@Composable
fun PhotosScreen() = LocalStrings.current.let { EmptyTab(it.photosTitle, it.photosEmpty) }
