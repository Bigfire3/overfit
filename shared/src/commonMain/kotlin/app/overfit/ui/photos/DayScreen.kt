package app.overfit.ui.photos

import androidx.compose.foundation.layout.Column
import app.overfit.ui.components.ConfirmTags
import app.overfit.ui.components.rememberConfirmArm
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.overfit.data.DiaryPhoto
import app.overfit.data.db.HistoryRow
import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import app.overfit.i18n.LocalStrings
import app.overfit.platform.rememberPhotoPicker
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Panel
import app.overfit.ui.components.Hint
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.history.PhotoThumb
import app.overfit.ui.history.TrainingRow
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.datetime.LocalDate
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import app.overfit.domain.WEIGHT_KG_MAX
import app.overfit.domain.WEIGHT_KG_MIN
import app.overfit.domain.WeightEntry
import app.overfit.domain.displayWeight
import app.overfit.domain.formatNumber
import app.overfit.domain.formatWeight
import app.overfit.domain.parseDecimal
import app.overfit.domain.lbToKg
import app.overfit.ui.components.HeaderDeleteButton
import app.overfit.ui.components.SetField

/**
 * One calendar day (tap on a day in the calendar): its photos (view, add with camera or library,
 * remove with a second tap) and its trainings (tap = training detail, where it can be edited).
 * Works for days without a training too, to add photos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayScreen(
    date: LocalDate,
    photos: List<DiaryPhoto>?,
    history: List<HistoryRow>?,
    unit: WeightUnit,
    language: Language,
    onBack: () -> Unit,
    onOpenPhoto: (photoId: String) -> Unit,
    onOpenWorkout: (sessionId: String) -> Unit,
    /** Picked or taken image, still unprocessed; the caller prepares and stores it for this day. */
    onAddPhoto: (bytes: ByteArray) -> Unit,
    onRemovePhoto: (photoId: String) -> Unit,
    /** Body-weight log (null while loading); the day's entry is set (replaces) or deleted from here. */
    weights: List<WeightEntry>? = emptyList(),
    onSetWeight: (kg: Double) -> Unit = {},
    onDeleteWeight: () -> Unit = {},
    /** Opened from the weight card: the field gets the focus right away. */
    focusWeight: Boolean = false,
    /** Full trainings of the day by id (exercises and sets for the opened cards); missing ones show only the head. */
    workouts: Map<String, app.overfit.domain.Workout> = emptyMap(),
) {
    val t = LocalStrings.current
    val p = t.photos
    val h = t.history
    val dayPhotos = remember(photos, date) { photos.orEmpty().filter { it.photo.date == date }.sortedBy { it.photo.takenAt } }
    val trainings = remember(history, date) { history.orEmpty().filter { dateOf(it.startedAt) == date }.sortedBy { it.startedAt } }

    Screen(scroll = true, header = { BackHeader(t.back, onBack) }) {
        Title(t.date(date))

        if (weights != null) DayWeight(date, weights, unit, language, focusWeight, onSetWeight, onDeleteWeight)

        SectionTitle(p.dayTrainings)
        if (history != null && trainings.isEmpty()) Text(p.dayNoTraining, tone = Tone.Muted)
        if (trainings.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            trainings.forEach { TrainingRow(it, unit, language, onOpenWorkout, workouts[it.id]) }
        }

        SectionTitle(p.dayPhotos)
        DayPhotoGrid(dayPhotos.map { it.photo }, onAdd = onAddPhoto, onOpen = onOpenPhoto, onRemove = onRemovePhoto)
        Hint(h.photosPrivate)
    }
}

/**
 * Body weight of the day: number field like the set fields (saved on every valid input, a new value replaces the old),
 * the previous value as grey placeholder, delete with two taps (red trash).
 */
@Composable
private fun DayWeight(
    date: LocalDate,
    weights: List<WeightEntry>,
    unit: WeightUnit,
    language: Language,
    focus: Boolean,
    onSet: (Double) -> Unit,
    onDelete: () -> Unit,
) {
    val t = LocalStrings.current
    val w = t.bodyWeight
    val key = date.toString()
    val entry = weights.firstOrNull { it.date == key }
    val previous = weights.filter { it.date < key }.maxByOrNull { it.date }
    fun shown(kg: Double) = formatNumber(displayWeight(kg, unit), language)
    // Text follows the stored value only while the field is not focused: saving on every keystroke must not
    // overwrite what is being typed (lb: "180" is stored as kg and would come back as "179,9").
    var focused by remember { mutableStateOf(false) }
    var text by remember(key) { mutableStateOf(entry?.let { shown(it.kg) } ?: "") }
    LaunchedEffect(key, entry?.kg, unit) { if (!focused) text = entry?.let { shown(it.kg) } ?: "" }
    var error by remember(key) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focus) { if (focus) runCatching { focusRequester.requestFocus() } }

    SectionTitle(w.title)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SetField(
            value = text,
            onValueChange = { input ->
                val clean = input.filter { it.isDigit() || it == ',' || it == '.' }.take(6)
                text = clean
                val kg = bodyWeightKg(clean, unit)
                // Too low is normal while typing ("8" on the way to "82"); only a value above the maximum is flagged.
                error = kg == null && (parseDecimal(clean)?.let { if (unit == WeightUnit.KG) it else lbToKg(it) } ?: 0.0) > WEIGHT_KG_MAX
                if (kg != null && kg != entry?.kg) onSet(kg)
            },
            description = w.fieldDescription(unit.symbol),
            modifier = Modifier.width(120.dp).testTag(WEIGHT_FIELD_TAG).onFocusChanged { state ->
                // Leaving an empty or invalid field shows the stored value again (deleting is the trash's job).
                focused = state.isFocused
                if (!state.isFocused) {
                    text = entry?.let { shown(it.kg) } ?: if (bodyWeightKg(text, unit) == null) "" else text
                    error = false
                }
            },
            keyboardType = KeyboardType.Decimal,
            placeholder = previous?.let { shown(it.kg) } ?: "–",
            focusRequester = focusRequester,
        )
        Spacer(Modifier.width(Space.s))
        Text(unit.symbol, tone = Tone.Muted)
        Spacer(Modifier.weight(1f))
        if (entry != null) HeaderDeleteButton(w.deleteEntry, onConfirm = { text = ""; onDelete() })
    }
    if (error) {
        val min = formatWeight(WEIGHT_KG_MIN, unit, language)
        val max = formatWeight(WEIGHT_KG_MAX, unit, language)
        Hint(w.invalid(min, max))
    } else {
        Hint(w.dayHint)
    }
}

/** Input in [unit] → kg (two decimals, so a pound value comes back unchanged), or null outside 25–350 kg. */
internal fun bodyWeightKg(input: String, unit: WeightUnit): Double? {
    val v = parseDecimal(input) ?: return null
    val kg = if (unit == WeightUnit.KG) v else lbToKg(v)
    if (kg < WEIGHT_KG_MIN || kg > WEIGHT_KG_MAX) return null
    return kotlin.math.round(kg * 100) / 100
}

/** Test tag of the body-weight field in the day view. */
const val WEIGHT_FIELD_TAG = "day-weight-field"
