package app.overfit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.overfit.domain.Language
import app.overfit.domain.RestTimer
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Text
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressableRow
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import app.overfit.workout.WorkoutFormat

/**
 * Rest timer at the bottom of the workout screen. Never full screen: the list above stays
 * scrollable and editable. Collapsed = one compact block, expanded = larger numbers.
 */
@Composable
fun RestBar(
    rest: RestTimer,
    now: Long,
    next: Workout.SetRef?,
    unit: WeightUnit,
    language: Language,
    onAdjust: (deltaSec: Int) -> Unit,
    onSkip: () -> Unit,
    onJumpToNext: (exerciseId: String) -> Unit,
) {
    val t = LocalStrings.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val countdown = WorkoutFormat.countdown(rest.remainingMs(now))
    val nextLine = next?.let { t.nextUp(WorkoutFormat.next(it, unit, language, t)) } ?: t.restOverBody
    val toggleLabel = (if (expanded) t.restCollapse else t.restExpand) + ", " + countdown

    @Composable
    fun Actions(modifier: Modifier = Modifier) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.l), verticalAlignment = Alignment.CenterVertically) {
            SmallButton(t.restMinus, { onAdjust(-15) })
            SmallButton(t.restPlus, { onAdjust(15) })
            SmallButton(t.restSkip, onSkip, tone = Tone.Default)
        }
    }

    @Composable
    fun NextLine(style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
        val target = next?.exercise?.id
        Text(
            nextLine,
            modifier.then(if (target != null) Modifier.pressableRow { onJumpToNext(target) } else Modifier),
            style = style,
            tone = Tone.Muted,
            maxLines = 1,
        )
    }

    Column(Modifier.fillMaxWidth()) {
        Hairline()
        if (!expanded) {
            Row(
                Modifier.fillMaxWidth().padding(top = Space.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    countdown,
                    Modifier.pressableRow { expanded = true }.padding(vertical = Space.xs).semantics { contentDescription = toggleLabel },
                    style = Theme.type.label,
                )
                Actions()
            }
            NextLine(Theme.type.caption, Modifier.fillMaxWidth().padding(vertical = Space.xs))
        } else {
            Text(
                countdown,
                Modifier.fillMaxWidth().pressableRow { expanded = false }.padding(top = Space.s).semantics { contentDescription = toggleLabel },
                style = Theme.type.hero,
            )
            NextLine(Theme.type.body, Modifier.fillMaxWidth().padding(vertical = Space.s))
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Theme.colors.hairline)) {
            Box(Modifier.fillMaxWidth(rest.progress(now)).fillMaxHeight().background(Theme.colors.foreground))
        }
        if (expanded) {
            Spacer(Modifier.height(Space.s))
            Actions()
        }
    }
}
