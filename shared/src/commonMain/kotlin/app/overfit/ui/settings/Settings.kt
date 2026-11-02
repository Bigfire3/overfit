package app.overfit.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.overfit.ai.Providers
import app.overfit.data.AppState
import app.overfit.data.AppStore
import app.overfit.domain.Language
import app.overfit.domain.ThemeMode
import app.overfit.domain.WeightUnit
import app.overfit.domain.ageFromBirthYear
import app.overfit.domain.birthYearFromAge
import app.overfit.domain.parseAge
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.RowInput
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Segmented
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate

@Composable
fun SettingsScreen(
    state: AppState,
    store: AppStore,
    providers: Providers,
    today: LocalDate,
    onConnect: () -> Unit,
    onCoachMemory: () -> Unit,
    onAbout: () -> Unit,
    onReset: () -> Unit,
) {
    val t = LocalStrings.current
    val storedAge = state.profile.birthYear?.let { ageFromBirthYear(it, today).toString() } ?: ""
    var name by remember(state.profile.name) { mutableStateOf(state.profile.name) }
    var age by remember(storedAge) { mutableStateOf(storedAge) }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(confirmReset) {
        if (confirmReset) { delay(4000); confirmReset = false }
    }

    Screen(scroll = true) {
        Title(t.settingsTitle)

        SectionTitle(t.general)
        ListRow(t.language) {
            Segmented(state.language, listOf(Language.DE to "Deutsch", Language.EN to "English"), store::setLanguage)
        }
        ListRow(t.theme) {
            Segmented(
                state.settings.theme,
                listOf(ThemeMode.SYSTEM to t.themeSystem, ThemeMode.DARK to t.themeDark, ThemeMode.LIGHT to t.themeLight),
                store::setTheme,
            )
        }

        SectionTitle(t.profile)
        ListRow(t.name) {
            RowInput(name, { name = it.take(40) }, onCommit = {
                if (name.isNotBlank()) store.setName(name) else name = state.profile.name
            })
        }
        ListRow(t.age) {
            RowInput(age, { age = it.filter(Char::isDigit).take(3) }, keyboardType = KeyboardType.Number, onCommit = {
                parseAge(age)?.let { store.setBirthYear(birthYearFromAge(it, today)) } ?: run { age = storedAge }
            })
        }
        ListRow(t.unit) {
            Segmented(state.profile.unit, listOf(WeightUnit.KG to "kg", WeightUnit.LB to "lb"), store::setUnit)
        }

        SectionTitle(t.ai)
        ListRow(providers.get(state.providerId).label, if (state.hasApiKey) t.connected else t.notConnected)
        ListRow(if (state.hasApiKey) t.change else t.connect, onClick = onConnect)
        if (state.hasApiKey) ListRow(t.disconnect, tone = Tone.Muted, onClick = store::disconnectAi)

        SectionTitle(t.coach)
        ListRow(t.coachMemory, onClick = onCoachMemory)

        SectionTitle(t.app)
        ListRow(t.about, onClick = onAbout)

        SectionTitle(t.data)
        ListRow(
            if (confirmReset) t.resetConfirm else t.reset,
            tone = if (confirmReset) Tone.Default else Tone.Muted,
            onClick = { if (confirmReset) onReset() else confirmReset = true },
        )
    }
}

@Composable
fun CoachMemoryScreen(onBack: () -> Unit) {
    val t = LocalStrings.current
    Screen(header = { BackHeader(t.back, onBack) }) {
        Title(t.coachMemory)
        Spacer(Modifier.height(Space.l))
        Text(t.coachMemoryEmpty, tone = Tone.Muted)
    }
}

@Composable
fun AboutScreen(versionName: String, onBack: () -> Unit) {
    val t = LocalStrings.current
    Screen(scroll = true, header = { BackHeader(t.back, onBack) }) {
        Title(t.appName)
        Spacer(Modifier.height(Space.s))
        Text(t.aboutVersion(versionName), tone = Tone.Muted)
        Spacer(Modifier.height(Space.m))
        Text(t.aboutTagline)

        SectionTitle(t.aboutAttribution)
        Text(t.aboutWorkoutGuide, style = Theme.type.caption.copy(lineHeight = Theme.type.body.lineHeight))
        Spacer(Modifier.height(Space.m))
        Text(t.aboutFreeExerciseDb, style = Theme.type.caption)

        SectionTitle(t.aboutLibraries)
        Text(t.aboutFont, style = Theme.type.caption)
        Spacer(Modifier.height(Space.m))
        Text(t.aboutLibrariesText, style = Theme.type.caption)
    }
}
