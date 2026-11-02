package app.overfit.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overfit.domain.Language
import app.overfit.domain.WeightEntry
import app.overfit.domain.WeightUnit
import app.overfit.domain.ageFromBirthYear
import app.overfit.domain.birthYearFromAge
import app.overfit.domain.displayWeight
import app.overfit.domain.formatNumber
import app.overfit.domain.latestWeight
import app.overfit.domain.parseAge
import app.overfit.domain.parseWeightToKg
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BigInput
import app.overfit.ui.components.Icon
import app.overfit.ui.components.IconButton
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Progress
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Segmented
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.datetime.LocalDate

const val ONBOARDING_STEPS = 4

@Composable
fun OnboardingHeader(step: Int, onBack: (() -> Unit)?) {
    Column {
        Box(Modifier.height(40.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            if (onBack != null && step > 1) IconButton(Icon.Back, LocalStrings.current.back, { onBack() })
        }
        Spacer(Modifier.height(Space.m))
        Progress(step, ONBOARDING_STEPS)
    }
}

/** Requests focus once so the keyboard opens right away. */
@Composable
private fun rememberAutoFocus(): FocusRequester {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    return focus
}

@Composable
private fun Question(
    step: Int,
    title: String,
    canContinue: Boolean,
    onBack: (() -> Unit)?,
    onNext: () -> Unit,
    content: @Composable () -> Unit,
) {
    Screen(
        header = { OnboardingHeader(step, onBack) },
        footer = { PrimaryButton(LocalStrings.current.next, onNext, enabled = canContinue) },
    ) {
        Title(title)
        Spacer(Modifier.height(Space.xl))
        content()
    }
}

@Composable
fun NameStep(stored: String, onNext: (String) -> Unit) {
    val t = LocalStrings.current
    var name by rememberSaveable { mutableStateOf(stored) }
    val valid = name.isNotBlank()
    val next = { if (valid) onNext(name) }
    Question(1, t.nameTitle, valid, onBack = null, onNext = next) {
        BigInput(
            value = name,
            onValueChange = { name = it.take(40) },
            placeholder = t.namePlaceholder,
            modifier = Modifier.focusRequester(rememberAutoFocus()),
            onDone = next,
        )
    }
}

@Composable
fun AgeStep(birthYear: Int?, today: LocalDate, onBack: () -> Unit, onNext: (Int) -> Unit) {
    val t = LocalStrings.current
    var input by rememberSaveable { mutableStateOf(birthYear?.let { ageFromBirthYear(it, today).toString() } ?: "") }
    val age = parseAge(input)
    val next = { if (age != null) onNext(birthYearFromAge(age, today)) }
    Question(2, t.ageTitle, age != null, onBack, next) {
        BigInput(
            value = input,
            onValueChange = { input = it.filter(Char::isDigit).take(3) },
            placeholder = "30",
            suffix = t.ageUnit,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.focusRequester(rememberAutoFocus()),
            onDone = next,
        )
        if (input.length >= 2 && age == null) Hint(t.ageError)
    }
}

@Composable
fun WeightStep(
    weights: List<WeightEntry>,
    unit: WeightUnit,
    language: Language,
    onUnitChange: (WeightUnit) -> Unit,
    onBack: () -> Unit,
    onNext: (Double) -> Unit,
) {
    val t = LocalStrings.current
    var input by rememberSaveable {
        mutableStateOf(latestWeight(weights)?.let { formatNumber(displayWeight(it.kg, unit), language) } ?: "")
    }
    val kg = parseWeightToKg(input, unit)
    val next = { if (kg != null) onNext(kg) }
    Question(3, t.weightTitle, kg != null, onBack, next) {
        BigInput(
            value = input,
            onValueChange = { v -> input = v.filter { it.isDigit() || it == ',' || it == '.' }.take(5) },
            placeholder = if (unit == WeightUnit.KG) "75" else "165",
            suffix = unit.symbol,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.focusRequester(rememberAutoFocus()),
            onDone = next,
        )
        Spacer(Modifier.height(Space.l))
        Segmented(unit, listOf(WeightUnit.KG to "kg", WeightUnit.LB to "lb")) { newUnit ->
            // Convert what was typed so switching units keeps the meaning.
            kg?.let { input = formatNumber(displayWeight(it, newUnit), language) }
            onUnitChange(newUnit)
        }
        if (input.length >= 2 && kg == null) Hint(t.weightError)
    }
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(Space.m))
    Text(text, style = Theme.type.caption, tone = Tone.Muted)
}
