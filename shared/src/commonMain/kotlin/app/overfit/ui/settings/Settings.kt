package app.overfit.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overfit.platform.RestAlerts
import app.overfit.ui.components.ConfirmButton
import app.overfit.ui.components.Hairline
import app.overfit.ui.share.ShareDependencies
import app.overfit.ui.components.pressableRow

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
import app.overfit.fuel.FuelAccess
import app.overfit.fuel.FuelSession
import app.overfit.fuel.formatEuro
import app.overfit.i18n.LocalFuelStrings
import app.overfit.ui.fuel.Loaded
import app.overfit.ui.fuel.rememberLoaded
import app.overfit.data.AppState
import app.overfit.data.AppStore
import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import app.overfit.domain.ageFromBirthYear
import app.overfit.domain.birthYearFromAge
import app.overfit.domain.parseAge
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.PanelSection
import app.overfit.ui.components.Hint
import app.overfit.ui.components.RowInput
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Segmented
import app.overfit.ui.components.SwitchRow
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
    onCoach: () -> Unit = {},
    onAbout: () -> Unit,
    fuelSession: FuelSession,
    onFuel: () -> Unit,
    onAiCoach: () -> Unit = {},
    onChatNotes: () -> Unit = {},
    restAlerts: RestAlerts,
    share: ShareDependencies,
    onReset: () -> Unit,
    exportingBackup: Boolean,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    val t = LocalStrings.current
    val storedAge = state.profile.birthYear?.let { ageFromBirthYear(it, today).toString() } ?: ""
    var name by remember(state.profile.name) { mutableStateOf(state.profile.name) }
    var age by remember(storedAge) { mutableStateOf(storedAge) }
    val storedRest = state.settings.defaultRestSec.toString()
    var restText by remember(storedRest) { mutableStateOf(storedRest) }
    var exactAllowed by remember { mutableStateOf(restAlerts.exactAlarmsAllowed()) }
    var notificationsAllowed by remember { mutableStateOf<Boolean?>(null) }
    var resumes by remember { mutableStateOf(0) }
    // Back from the system dialog or the system settings: show the current permissions.
    LifecycleResumeEffect(restAlerts) {
        exactAllowed = restAlerts.exactAlarmsAllowed()
        resumes++
        onPauseOrDispose { }
    }
    LaunchedEffect(restAlerts, resumes) { notificationsAllowed = restAlerts.notificationsAllowed() }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(confirmReset) {
        if (confirmReset) { delay(4000); confirmReset = false }
    }

    Screen(scroll = true) {
        Title(t.settingsTitle)

        PanelSection(t.general) {
            ListRow(t.language) {
                Segmented(state.language, listOf(Language.DE to "Deutsch", Language.EN to "English"), store::setLanguage)
            }
        }

        PanelSection(t.profile) {
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
        }

        PanelSection(t.workoutSettings) {
            ListRow(t.defaultRest) {
                RowInput(restText, { restText = it.filter(Char::isDigit).take(4) }, keyboardType = KeyboardType.Number, onCommit = {
                    restText.toIntOrNull()?.let(store::setDefaultRest) ?: run { restText = storedRest }
                })
            }
        }

        PanelSection(t.notifications) {
            when (notificationsAllowed) {
                true -> ListRow(t.notificationsPermission, t.notificationsAllowed, onClick = restAlerts::openNotificationSettings)
                false -> ListRow(t.notificationsPermission, t.exactAlarmAllow, onClick = restAlerts::allowNotifications)
                null -> Unit
            }
            if (exactAllowed == false) {
                ListRow(t.exactAlarm, t.exactAlarmAllow, onClick = restAlerts::openExactAlarmSettings)
            }
            SwitchRow(t.vibration, state.settings.vibration, onCheckedChange = store::setVibration)
            SwitchRow(t.restTicks, state.settings.restTicks, onCheckedChange = store::setRestTicks)
        }
        if (notificationsAllowed == false) Hint(t.notificationsDeniedHint)
        if (exactAllowed == false) Hint(t.exactAlarmHint)

        // Everything about the coach and AI access lives in the Coach tab; here only the link (and Fuel, the wallet).
        val hub = app.overfit.i18n.LocalCoachStrings.current.hub
        PanelSection(t.ai) {
            ListRow(hub.profileLink, onClick = onCoach)
            FuelRow(fuelSession, fuelActive = state.providerId == app.overfit.ai.ProviderId.FUEL, onClick = onFuel)
        }
        Hint(hub.profileLinkHint)

        PanelSection(t.app) {
            ListRow(t.about, onClick = onAbout)
        }

        PanelSection(t.photos.backupSection) {
            ListRow(if (exportingBackup) t.photos.backupExporting else t.photos.backupExport, onClick = { if (!exportingBackup) onExportBackup() })
            ListRow(t.photos.backupImport, onClick = onImportBackup)
        }
        Hint(t.photos.backupExportHint)

        SectionTitle(t.data)
        Spacer(Modifier.height(Space.s))
        ConfirmButton(t.reset, onConfirm = onReset)
    }
}

@Composable
fun AboutScreen(versionName: String, onBack: () -> Unit, fuel: FuelAccess? = null) {
    val t = LocalStrings.current
    var taps by remember { mutableStateOf(0) }
    val devUnlocked = fuel != null && taps >= DEV_TAPS
    Screen(scroll = true, header = { BackHeader(t.back, onBack) }) {
        Title(t.appName)
        Spacer(Modifier.height(Space.s))
        // Hidden developer option: tap the version seven times.
        Text(t.aboutVersion(versionName), tone = Tone.Muted, modifier = Modifier.pressableRow { taps++ })
        if (devUnlocked && fuel != null) DeveloperSection(fuel)
        Spacer(Modifier.height(Space.m))
        Text(t.aboutTagline)
        Spacer(Modifier.height(Space.m))
        Text(t.aboutLicense)
        val uriHandler = LocalUriHandler.current
        ListRow(t.aboutRepo, onClick = { uriHandler.openUri(REPO_URL) })

        SectionTitle(t.aboutAttribution)
        Text(t.aboutWorkoutGuide, style = Theme.type.caption.copy(lineHeight = Theme.type.body.lineHeight))

        SectionTitle(t.aboutLibraries)
        Text(t.aboutFont, style = Theme.type.caption)
        Spacer(Modifier.height(Space.m))
        Text(t.aboutLibrariesText, style = Theme.type.caption)
    }
}

private const val DEV_TAPS = 7
private const val REPO_URL = "https://github.com/Bigfire3/overfit"

/** Hidden developer settings: override of the credit/invite backend URL. */
@Composable
private fun DeveloperSection(fuel: FuelAccess) {
    val f = LocalFuelStrings.current
    val active by fuel.url.collectAsStateWithLifecycle()
    val override by fuel.overrideUrl.collectAsStateWithLifecycle()
    var input by remember(override) { mutableStateOf(override) }
    SectionTitle(f.devTitle)
    Text(f.devUnlocked, style = Theme.type.caption, tone = Tone.Muted)
    ListRow(f.devBackendUrl) {
        RowInput(input, { input = it }, onCommit = { fuel.setOverride(input) })
    }
    Text(f.devBackendHint, style = Theme.type.caption, tone = Tone.Muted)
    Spacer(Modifier.height(Space.s))
    Text(if (active.isEmpty()) f.devNotConfigured else f.devActive(active), style = Theme.type.caption)
}

/** Profile → "Fuel · 4,20 €" (or "coming soon" / "Demo"). */
@Composable
private fun FuelRow(session: FuelSession, fuelActive: Boolean, onClick: () -> Unit) {
    val f = LocalFuelStrings.current
    val demo by session.demo.collectAsStateWithLifecycle()
    val service = session.current()
    // Only ask the server for the balance when the user actually uses Fuel (or looks at the demo): own key = no request.
    val load = service != null && (fuelActive || demo)
    val (account, _) = rememberLoaded(service to load) { if (load) service?.account() else null }
    val value = when {
        service == null -> f.fuelRowSoon
        !load -> ""
        else -> ((account as? Loaded.Ready)?.value?.let { app.overfit.ui.fuel.creditSummary(it, f) } ?: "") +
            if (demo) " · ${f.fuelRowDemo}" else ""
    }
    ListRow(f.fuel, value.trim(), onClick = onClick)
}

