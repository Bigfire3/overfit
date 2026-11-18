package app.overfit.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import app.overfit.ui.components.pressScale
import app.overfit.ui.components.textInputArea
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import app.overfit.ui.components.Reveal
import app.overfit.ui.components.Swap
import app.overfit.ui.components.motionColor
import app.overfit.ui.components.rememberHaptics
import app.overfit.ui.theme.Motion
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import app.overfit.coach.AthleteProfile
import app.overfit.coach.CoachChatPrefsStore
import app.overfit.coach.CoachService
import app.overfit.coach.PromptEntry
import app.overfit.coach.TrainingExperience
import app.overfit.coach.TrainingGoal
import app.overfit.data.AppState
import app.overfit.domain.formatWeight
import app.overfit.domain.latestWeight
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.coachSettingsStringsFor
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Chip
import app.overfit.ui.components.ConfirmButton
import app.overfit.ui.components.Hint
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.MultilineInput
import app.overfit.ui.components.Panel
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.PromptView
import app.overfit.ui.components.RowInput
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressableRow
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme

/** Lines of the base instruction shown while it is collapsed. */
private const val BASE_PREVIEW_LINES = 6

/** Long compound titles ("Coach-Anweisungen") may break after the hyphen instead of inside the word at 360 dp. */
internal fun breakableTitle(title: String) = title.replace("-", "-\u200B")

// ---------------------------------------------------------------------------------------------
// Coach profile

/** Settings → "Coach profile": the [CoachProfileForm] as a page; body weight comes from the calendar. */
@Composable
fun CoachProfileScreen(state: AppState, prefs: CoachChatPrefsStore, onBack: () -> Unit) {
    val s = coachSettingsStringsFor(LocalFuelStrings.current.language)
    val current by prefs.state.collectAsState()
    Screen(scroll = true, header = { BackHeader(LocalStrings.current.back, onBack) }) {
        Title(breakableTitle(s.profile))
        Spacer(Modifier.height(Space.s))
        Text(s.profileIntro, tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        CoachProfileForm(
            profile = current.profile,
            latestWeight = latestWeight(state.weights)?.let { formatWeight(it.kg, state.profile.unit, state.language) },
            onSave = prefs::setProfile,
        )
    }
}

/**
 * The coach profile as a form (goal, experience, days per week, height, weight read-only, equipment, limitations) with
 * its own "Save". Pure content: the settings page shows it as is, the chat's popup with [popup] = true (controls one
 * step lighter, because the popup itself is the surface colour). The height must be 100–250 cm or empty: outside that
 * range a hint shows at the field and "Save" waits.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachProfileForm(
    profile: AthleteProfile,
    latestWeight: String?,
    onSave: (AthleteProfile) -> Unit,
    modifier: Modifier = Modifier,
    popup: Boolean = false,
) {
    val language = LocalFuelStrings.current.language
    val s = coachSettingsStringsFor(language)
    var goal by rememberSaveable(profile) { mutableStateOf(profile.goal) }
    var experience by rememberSaveable(profile) { mutableStateOf(profile.experience) }
    var days by rememberSaveable(profile) { mutableStateOf(profile.daysPerWeek) }
    var height by rememberSaveable(profile) { mutableStateOf(profile.heightCm?.toString().orEmpty()) }
    var equipment by rememberSaveable(profile) { mutableStateOf(profile.equipment) }
    var limitations by rememberSaveable(profile) { mutableStateOf(profile.limitations) }
    var saved by remember { mutableStateOf(false) }
    // The height field was left: from then on an invalid value shows its hint even if digits could still follow.
    var heightLeft by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val heightCm = height.toIntOrNull()
    val heightValid = height.isEmpty() || (heightCm != null && heightCm in AthleteProfile.HEIGHT_MIN..AthleteProfile.HEIGHT_MAX)
    // While typing "182" the hint does not flash after "1": it shows once no further digit can make the value valid.
    val showHeightHint = !heightValid && (heightLeft || !heightCompletable(height))
    val edited = AthleteProfile(goal, experience, days, heightCm, equipment.trim(), limitations.trim())
    val onHeight: (String) -> Unit = { height = it.filter(Char::isDigit).take(3); saved = false; heightLeft = false }

    Column(modifier.fillMaxWidth()) {
        if (popup) {
            val cs = app.overfit.i18n.coachChatStringsFor(language)
            FieldLabel(cs.goal)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                TrainingGoal.entries.forEach { g ->
                    SelectChip(cs.goalLabel(g), goal == g, "profile:goal:${g.name}") { goal = if (goal == g) null else g }
                }
            }
            FieldLabel(cs.experience)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                TrainingExperience.entries.forEach { e ->
                    SelectChip(cs.experienceLabel(e), experience == e, "profile:experience:${e.name}") { experience = if (experience == e) null else e }
                }
            }
            FieldLabel(cs.days)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (AthleteProfile.DAYS_MIN..AthleteProfile.DAYS_MAX).forEach { n ->
                    SelectChip(n.toString(), days == n, "profile:days:$n", Modifier.weight(1f), compact = true) { days = if (days == n) null else n }
                }
            }
            FieldLabel(cs.height)
            PopupField(height, onHeight, "175", "profile:height", KeyboardType.Number, suffix = cs.heightUnit, onLeave = { heightLeft = true })
            Reveal(showHeightHint) { HeightHint(s.heightRange, Modifier.padding(top = Space.xs)) }
            FieldLabel(cs.weight)
            Text(latestWeight ?: cs.weightMissing, tone = if (latestWeight == null) Tone.Muted else Tone.Default)
            FieldLabel(cs.equipment)
            PopupField(equipment, { equipment = it.take(AthleteProfile.TEXT_MAX) }, cs.equipmentPlaceholder, "profile:equipment")
            FieldLabel(cs.limitations)
            PopupField(limitations, { limitations = it.take(AthleteProfile.TEXT_MAX) }, cs.limitationsPlaceholder, "profile:limitations")
        } else {
            SectionTitle(s.goal)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf(TrainingGoal.MUSCLE to s.goalMuscle, TrainingGoal.STRENGTH to s.goalStrength, TrainingGoal.FAT_LOSS to s.goalFatLoss, TrainingGoal.FITNESS to s.goalFitness)
                    .forEach { (g, label) -> Chip(label, goal == g, { goal = if (goal == g) null else g; saved = false }, Modifier.testTag("profile:goal:${g.name}")) }
            }
            SectionTitle(s.experience)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf(TrainingExperience.BEGINNER to s.expBeginner, TrainingExperience.INTERMEDIATE to s.expIntermediate, TrainingExperience.ADVANCED to s.expAdvanced)
                    .forEach { (e, label) -> Chip(label, experience == e, { experience = if (experience == e) null else e; saved = false }) }
            }
            SectionTitle(s.daysPerWeek)
            DaysPicker(days) { days = if (days == it) null else it; saved = false }

            Spacer(Modifier.height(Space.l))
            Panel {
                ListRow(s.height) {
                    RowInput(height, onHeight, onCommit = { heightLeft = true }, keyboardType = KeyboardType.Number)
                }
                ListRow(s.weight, latestWeight ?: s.weightNone)
            }
            Reveal(showHeightHint) { HeightHint(s.heightRange, Modifier.padding(start = Space.m, top = Space.s)) }
            Hint(s.weightHint)

            SectionTitle(s.equipment)
            MultilineInput(equipment, { equipment = it.take(AthleteProfile.TEXT_MAX); saved = false }, s.equipmentPlaceholder, Modifier.testTag("profile:equipment"), minHeight = 56.dp)
            SectionTitle(s.limitations)
            MultilineInput(limitations, { limitations = it.take(AthleteProfile.TEXT_MAX); saved = false }, s.limitationsPlaceholder, Modifier.testTag("profile:limitations"), minHeight = 56.dp)
        }

        Spacer(Modifier.height(Space.l))
        // The popup closes on "Save", so it may save unchanged; the page only saves a change. On the page the button
        // itself confirms ("Saved") until the next edit, instead of an extra line popping in below it.
        PrimaryButton(
            if (saved && !popup) s.saved else s.save,
            onClick = { onSave(edited); saved = true; haptics.confirm() },
            enabled = heightValid && (popup || edited != profile),
            modifier = Modifier.fillMaxWidth().testTag("profile:save"),
        )
    }
}

/** True while more digits could still turn [height] into a valid value ("1" or "18" on the way to "182"). */
private fun heightCompletable(height: String): Boolean =
    height.length < 3 && (AthleteProfile.HEIGHT_MIN..AthleteProfile.HEIGHT_MAX).any { it.toString().startsWith(height) }

/** Hint at the height field while its value is outside 100–250 cm. */
@Composable
private fun HeightHint(text: String, modifier: Modifier) {
    Text(text, modifier.testTag("profile:height-hint"), style = Theme.type.caption, tone = Tone.Muted)
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
    val haptics = rememberHaptics()
    // Same soft fill as the central Chip.
    val fill by motionColor(if (selected) c.foreground else c.surfacePressed, "selectChipFill")
    val tone by motionColor(if (selected) c.inverseForeground else c.foreground, "selectChipLabel")
    Box(
        modifier
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.RadioButton) { haptics.tick(); onClick() }
            .semantics { this.selected = selected }
            .testTag(tag)
            .padding(vertical = (app.overfit.ui.theme.TouchTarget.min - app.overfit.ui.theme.Sizes.chip) / 2)
            .heightIn(min = app.overfit.ui.theme.Sizes.chip)
            .clip(app.overfit.ui.components.ChipShape)
            .drawBehind { drawRect(fill) }
            .padding(horizontal = if (compact) 0.dp else 14.dp, vertical = Space.s),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Theme.type.caption, color = { tone }, maxLines = 1)
    }
}

/** Text field in the popup: [Colors.surfacePressed] area, no border, optional unit at the end. */
@Composable
private fun PopupField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    suffix: String? = null,
    onLeave: () -> Unit = {},
) {
    val c = Theme.colors
    val style = Theme.type.body.copy(color = c.foreground)
    val focused = remember { booleanArrayOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfacePressed).testTag(tag)
            .onFocusChanged { if (focused[0] && !it.hasFocus) onLeave(); focused[0] = it.hasFocus },
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
        if (suffix != null) Text(suffix, Modifier.padding(end = Space.m), style = Theme.type.caption, tone = Tone.Muted)
    }
}

/**
 * 1–7 as one segmented row (fits 360 dp); tapping the selected day clears it. Like [app.overfit.ui.components.Segmented]
 * one white thumb slides to the chosen day; clearing fades it out in place instead of sliding.
 */
@Composable
private fun DaysPicker(value: Int?, onChange: (Int) -> Unit) {
    val c = Theme.colors
    val haptics = rememberHaptics()
    val count = AthleteProfile.DAYS_MAX - AthleteProfile.DAYS_MIN + 1
    val index = value?.let { it - AthleteProfile.DAYS_MIN }
    // Thumb position in segments and its opacity (0 = no day chosen); both read while drawing.
    val thumb = remember { Animatable((index ?: 0).toFloat()) }
    val thumbAlpha = remember { Animatable(if (index != null) 1f else 0f) }
    LaunchedEffect(index) {
        if (index == null) { thumbAlpha.animateTo(0f, Motion.snappy()); return@LaunchedEffect }
        // From "nothing chosen" the thumb appears at its day instead of sliding there from the last one.
        if (thumbAlpha.value == 0f) thumb.snapTo(index.toFloat())
        launch { thumbAlpha.animateTo(1f, Motion.snappy()) }
        thumb.animateTo(index.toFloat(), Motion.snappy())
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.surface).padding(3.dp)
            .drawBehind {
                val w = size.width / count
                if (thumbAlpha.value > 0f) {
                    drawRoundRect(c.inverseBackground, Offset(thumb.value * w, 0f), Size(w, size.height), CornerRadius(size.height / 2), alpha = thumbAlpha.value)
                }
            },
    ) {
        (AthleteProfile.DAYS_MIN..AthleteProfile.DAYS_MAX).forEach { d ->
            val active = d == value
            val tone by motionColor(if (active) c.inverseForeground else c.muted, "daysLabel")
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(50))
                    .semantics { selected = active }
                    .pressableRow { haptics.tick(); onChange(d) }
                    .testTag("profile:days:$d"),
                contentAlignment = Alignment.Center,
            ) { BasicText(d.toString(), style = Theme.type.label, color = { tone }) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Coach instructions

/**
 * Settings → "Coach instructions": the Overfit base instruction (read only, collapsed) and the user's addition,
 * which goes along with every request (every model, own key too). "Back to default" (two taps) clears it.
 */
@Composable
fun CoachInstructionsScreen(coach: CoachService, prefs: CoachChatPrefsStore, onBack: () -> Unit) {
    val language = LocalFuelStrings.current.language
    val s = coachSettingsStringsFor(language)
    val current by prefs.state.collectAsState()
    var text by remember(current.addition) { mutableStateOf(current.addition) }
    var saved by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val base = remember(language) { coach.baseInstructions(language).trim() }
    val long = base.lines().size > 3 || base.length > 400
    Screen(scroll = true, header = { BackHeader(LocalStrings.current.back, onBack) }) {
        Title(breakableTitle(s.instructions))

        SectionTitle(s.baseTitle)
        // Expanding grows the box smoothly (it only clips) instead of in one frame.
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Theme.colors.surface).animateContentSize(Motion.smooth()).padding(Space.m).testTag("coach:base")) {
            Text(base, style = Theme.type.caption, tone = Tone.Muted, maxLines = if (expanded || !long) Int.MAX_VALUE else BASE_PREVIEW_LINES)
        }
        Hint(s.baseHint)
        if (long) {
            Spacer(Modifier.height(Space.s))
            SmallButton(if (expanded) s.showLess else s.showAll, { expanded = !expanded }, Modifier.testTag("coach:base-toggle"))
        }

        SectionTitle(s.additionTitle)
        MultilineInput(text, { text = it.take(app.overfit.coach.CoachChatPrefs.ADDITION_MAX); saved = false }, s.additionPlaceholder, Modifier.testTag("coach:addition"), minHeight = 140.dp)
        Hint(s.additionHint)

        // Save right under the field (no floating footer that could cover the reset below it).
        Spacer(Modifier.height(Space.l))
        // The button itself confirms ("Saved") until the next edit, instead of a line popping in and pushing the reset.
        PrimaryButton(
            if (saved) s.saved else s.save,
            onClick = { prefs.setAddition(text); saved = true; haptics.confirm() },
            enabled = text.trim() != current.addition,
            modifier = Modifier.fillMaxWidth().testTag("coach:addition-save"),
        )
        Spacer(Modifier.height(Space.m))
        ConfirmButton(s.resetDefault, onConfirm = {
            prefs.setAddition("")
            coach.settings.setInstructions(null)
            text = ""
            saved = true
        })
    }
}

// ---------------------------------------------------------------------------------------------
// Prompts

/** Settings → "Copy prompts": the ready-made prompts with their full text; "Use in chat" opens a new chat with it. */
@Composable
fun CoachPromptsScreen(
    prompts: suspend () -> List<PromptEntry>,
    copy: (String) -> Unit,
    onUseInChat: (String) -> Unit,
    onBack: () -> Unit,
) {
    val s = coachSettingsStringsFor(LocalFuelStrings.current.language)
    val h = app.overfit.i18n.LocalCoachStrings.current.hub
    val list by produceState<List<PromptEntry>?>(null) { value = prompts() }
    Screen(scroll = true, header = { BackHeader(LocalStrings.current.back, onBack) }) {
        Title(s.copyPrompts)
        Spacer(Modifier.height(Space.s))
        Text(s.promptsIntro, tone = Tone.Muted)
        Spacer(Modifier.height(Space.m))
        // Loading → prompts fades over instead of the list popping in.
        Swap(list, Modifier.fillMaxWidth(), contentKey = { it == null }) { loaded ->
            Column(Modifier.fillMaxWidth()) {
                if (loaded == null) Text(h.promptLoading, Modifier.padding(vertical = Space.m), tone = Tone.Muted)
                loaded?.forEach { p ->
                    PromptView(
                        title = p.title,
                        hint = p.hint,
                        text = p.text,
                        onCopy = copy,
                        onUseInChat = p.chatAsk?.let { ask -> { onUseInChat(ask) } },
                    )
                }
            }
        }
    }
}
