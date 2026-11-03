package app.overfit.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overfit.domain.TemplateSummary
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressableRow
import app.overfit.ui.exercises.TemplateRow
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme

/** Choose how to start: free workout or from a template. The rows are the actions. */
@Composable
fun StartWorkoutScreen(
    templates: List<TemplateSummary>?,
    onBack: () -> Unit,
    onEmpty: () -> Unit,
    onTemplate: (Long) -> Unit,
) {
    val t = LocalStrings.current
    Screen(scroll = true, header = { BackHeader(t.back, onBack) }) {
        Title(t.startWorkout)
        Column(Modifier.fillMaxWidth().padding(top = Space.l).pressableRow(onEmpty)) {
            Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = Space.m)) {
                Text(t.emptyWorkout)
                Text(t.emptyWorkoutHint, style = Theme.type.caption, tone = Tone.Muted)
            }
            Hairline()
        }
        SectionTitle(t.chooseTemplate)
        if (templates != null && templates.isEmpty()) Text(t.templatesEmpty, tone = Tone.Muted)
        templates?.forEach { TemplateRow(it) { onTemplate(it.id) } }
    }
}
