package app.overfit.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressableRow
import app.overfit.ui.share.ShareDependencies
import app.overfit.ui.share.TemplateImportRows
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme

/**
 * "+" on the Training tab: create a workout (saved, not started) or start empty (becomes a saved
 * workout when it ends). Import and the sample workouts sit below, quietly. The rows are the actions.
 */
@Composable
fun NewWorkoutScreen(
    workoutRunning: Boolean,
    share: ShareDependencies,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onStartEmpty: () -> Unit,
    onImport: () -> Unit,
) {
    val t = LocalStrings.current
    Screen(scroll = true, header = { BackHeader(t.back, onBack) }) {
        Title(t.newWorkout)
        Column(Modifier.padding(top = Space.l)) {
            Hairline()
            ChoiceRow(t.createWorkout, t.createWorkoutHint, onCreate)
            // Only one workout at a time: while one runs, "start empty" says so instead of starting.
            if (workoutRunning) ChoiceRow(t.emptyWorkout, t.otherWorkoutRunning, null)
            else ChoiceRow(t.emptyWorkout, t.startEmptyHint, onStartEmpty)
        }
        SectionTitle(t.moreWays)
        TemplateImportRows(share, onImport)
    }
}

@Composable
private fun ChoiceRow(title: String, hint: String, onClick: (() -> Unit)?) {
    Column(if (onClick != null) Modifier.fillMaxWidth().pressableRow(onClick) else Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = Space.m)) {
            Text(title, style = Theme.type.label, tone = if (onClick != null) Tone.Default else Tone.Muted)
            Text(hint, style = Theme.type.caption, tone = Tone.Muted)
        }
        Hairline()
    }
}
