package app.overfit.ui.workout

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
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
        // Tight spacing, "skip" takes the rest of the row: on a 360 dp phone it stays readable next to the countdown.
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
            SmallButton(t.restMinus, { onAdjust(-15) })
            SmallButton(t.restPlus, { onAdjust(15) })
            SmallButton(t.restSkip, onSkip, Modifier.weight(1f, fill = false), tone = Tone.Default)
        }
    }

    @Composable
    fun NextLine(style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, maxLines: Int = 1) {
        val target = next?.exercise?.id
        Text(
            nextLine,
            modifier.then(if (target != null) Modifier.pressableRow { onJumpToNext(target) } else Modifier),
            style = style,
            tone = Tone.Muted,
            maxLines = maxLines,
        )
    }

    // Expanding/collapsing grows the bar smoothly instead of jumping (no bounce); the list above keeps its place.
    Column(Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))) {
        Hairline()
        if (!expanded) {
            Row(
                Modifier.fillMaxWidth().padding(top = Space.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    countdown,
                    // 48 dp high target (the digits alone are 24 dp), text centred in it.
                    Modifier.pressableRow { expanded = true }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically)
                        .semantics { contentDescription = toggleLabel },
                    style = Theme.type.label,
                )
                Spacer(Modifier.width(Space.s))
                Actions(Modifier.weight(1f, fill = false))
            }
            NextLine(Theme.type.caption, Modifier.fillMaxWidth().padding(vertical = Space.xs))
        } else {
            Text(
                countdown,
                Modifier.fillMaxWidth().pressableRow { expanded = false }.padding(top = Space.s).semantics { contentDescription = toggleLabel },
                style = Theme.type.hero,
            )
            NextLine(Theme.type.body, Modifier.fillMaxWidth().padding(vertical = Space.s), maxLines = 2)
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
