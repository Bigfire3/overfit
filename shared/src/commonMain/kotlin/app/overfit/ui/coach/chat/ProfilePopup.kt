package app.overfit.ui.coach.chat

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overfit.coach.AthleteProfile
import app.overfit.coach.TrainingExperience
import app.overfit.coach.TrainingGoal
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Text
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressScale
import app.overfit.ui.components.textInputArea
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme

/**
 * "Dein Profil" (from the profile chip): goal, experience, days per week, height, body weight (read only, from the
 * calendar), equipment and limitations. "Speichern" stores it in [CoachChatEnv.prefs].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.ProfilePopup(env: CoachChatEnv, onSaved: () -> Unit) {
    val s = chatStrings()
    val start = remember { env.prefs.state.value.profile }
    var goal by rememberSaveable { mutableStateOf(start.goal) }
    var experience by rememberSaveable { mutableStateOf(start.experience) }
    var days by rememberSaveable { mutableStateOf(start.daysPerWeek) }
    var height by rememberSaveable { mutableStateOf(start.heightCm?.toString().orEmpty()) }
    var equipment by rememberSaveable { mutableStateOf(start.equipment) }
    var limitations by rememberSaveable { mutableStateOf(start.limitations) }
    val weight = remember { env.latestWeight() }

    Text(s.profileTitle, style = Theme.type.label.copy(fontWeight = FontWeight.SemiBold, fontSize = Theme.type.title.fontSize * 0.65f))

    FieldLabel(s.goal)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        TrainingGoal.entries.forEach { g ->
            SelectChip(s.goalLabel(g), goal == g, "profile:goal:${g.name}") { goal = if (goal == g) null else g }
        }
    }

    FieldLabel(s.experience)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        TrainingExperience.entries.forEach { e ->
            SelectChip(s.experienceLabel(e), experience == e, "profile:experience:${e.name}") { experience = if (experience == e) null else e }
        }
    }

    FieldLabel(s.days)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (AthleteProfile.DAYS_MIN..AthleteProfile.DAYS_MAX).forEach { n ->
            SelectChip(n.toString(), days == n, "profile:days:$n", Modifier.weight(1f), compact = true) { days = if (days == n) null else n }
        }
    }

    FieldLabel(s.height)
    PopupField(
        value = height,
        onValueChange = { v -> height = v.filter { it.isDigit() }.take(3) },
        placeholder = "175",
        tag = "profile:height",
        keyboardType = KeyboardType.Number,
        suffix = s.heightUnit,
    )

    FieldLabel(s.weight)
    Text(weight ?: s.weightMissing, tone = if (weight == null) Tone.Muted else Tone.Default)

    FieldLabel(s.equipment)
    PopupField(equipment, { equipment = it.take(AthleteProfile.TEXT_MAX) }, s.equipmentPlaceholder, "profile:equipment")

    FieldLabel(s.limitations)
    PopupField(limitations, { limitations = it.take(AthleteProfile.TEXT_MAX) }, s.limitationsPlaceholder, "profile:limitations")

    Spacer(Modifier.height(Space.l))
    PrimaryButton(
        s.save,
        {
            env.prefs.setProfile(
                AthleteProfile(
                    goal = goal,
                    experience = experience,
                    daysPerWeek = days,
                    heightCm = height.toIntOrNull(),
                    equipment = equipment,
                    limitations = limitations,
                ),
            )
            onSaved()
        },
        modifier = Modifier.fillMaxWidth().testTag("profile:save"),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, Modifier.padding(top = Space.l, bottom = Space.s), style = Theme.type.caption, tone = Tone.Muted)
}

/** Single-choice chip in the popup: chosen = white fill with black text, otherwise [Colors.surfacePressed]. */
@Composable
private fun SelectChip(label: String, selected: Boolean, tag: String, modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag(tag)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.foreground else c.surfacePressed)
            .padding(horizontal = if (compact) 0.dp else 14.dp, vertical = Space.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Theme.type.caption, tone = if (selected) Tone.Inverse else Tone.Default, maxLines = 1)
    }
}

@Composable
private fun PopupField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    suffix: String? = null,
) {
    val c = Theme.colors
    val style = Theme.type.body.copy(color = c.foreground)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfacePressed).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            singleLine = keyboardType == KeyboardType.Number,
            maxLines = 3,
            cursorBrush = SolidColor(c.foreground),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            modifier = Modifier.textInputArea().weight(1f).heightIn(min = 48.dp).padding(horizontal = Space.m, vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = c.muted))
                    inner()
                }
            },
        )
        if (suffix != null) {
            Text(suffix, Modifier.padding(end = Space.m), style = Theme.type.caption, tone = Tone.Muted)
            Spacer(Modifier.width(0.dp))
        }
    }
}
