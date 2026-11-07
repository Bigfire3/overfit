package app.overfit.ui.coach

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.overfit.ai.KeyTestResult
import app.overfit.ai.KeyTester
import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
import app.overfit.ai.TIER_LITE
import app.overfit.ai.TIER_PRO
import app.overfit.ai.TIER_STANDARD
import app.overfit.coach.CoachModels
import app.overfit.coach.CoachService
import app.overfit.coach.CoachSettings
import app.overfit.coach.modelOfCurrentTier
import app.overfit.data.AppState
import app.overfit.data.AppStore
import app.overfit.domain.Language
import app.overfit.fuel.CostEstimate
import app.overfit.fuel.FuelConfig
import app.overfit.fuel.FuelTask
import app.overfit.fuel.FuelTierInfo
import app.overfit.fuel.formatCents
import app.overfit.fuel.modelShortName
import app.overfit.i18n.CoachSettingsStrings
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.coachSettingsStringsFor
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Chip
import app.overfit.ui.components.Hint
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.Panel
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Segmented
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressableRow
import app.overfit.ui.components.textInputArea
import app.overfit.ui.fuel.Loaded
import app.overfit.ui.fuel.rememberLoaded
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.launch

/** Providers that work with an own key, in the order of the chips. Google = [ProviderId.GEMINI]. */
internal val OWN_KEY_PROVIDERS = listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.GEMINI, ProviderId.OPENROUTER)

/** Brand names of the own-key providers, the same in every language. */
fun ownKeyProviderLabel(id: ProviderId): String = when (id) {
    ProviderId.ANTHROPIC -> "Anthropic"
    ProviderId.OPENAI -> "OpenAI"
    ProviderId.GEMINI -> "Google"
    ProviderId.OPENROUTER -> "OpenRouter"
    ProviderId.FUEL -> "Overfit"
}

/** Credit tiers in the order shown: Haiku, Sonnet, Opus. */
private val TIERS = listOf(TIER_LITE, TIER_STANDARD, TIER_PRO)

/** Most suggestions shown under the model field (the rest is still reachable by typing). */
private const val MAX_SUGGESTIONS = 12

/**
 * Label of the model chip in the chat header: credit → the model of the chosen tier ("Claude Sonnet 5.5"), own key →
 * the model's name or its ID, no access → "No AI access".
 */
fun modelChipLabel(state: AppState, settings: CoachSettings, fuelAvailable: Boolean, strings: CoachSettingsStrings): String = when {
    state.providerId == ProviderId.FUEL ->
        if (fuelAvailable) modelShortName(modelOfCurrentTier(settings.model(ProviderId.FUEL))) else strings.accessNone
    state.hasApiKey -> modelShortName(settings.model(state.providerId))
    else -> strings.accessNone
}

/**
 * Estimated price of one chat message ("≈ 2,6 ct"), only with credit and a known price table; own key and no access
 * → null (the provider bills the user directly, Overfit does not know the price).
 */
fun chatCostEstimate(state: AppState, settings: CoachSettings, config: FuelConfig?, language: Language): String? {
    if (state.providerId != ProviderId.FUEL || config == null) return null
    val info = config.tiers[settings.model(ProviderId.FUEL)]?.takeIf { it.available } ?: return null
    return "≈ " + formatCents(CostEstimate.micro(info, config.markupBp, FuelTask.CHAT, config.usageProfiles), language)
}

/** Settings → "AI access" / "Default model": the [ModelPicker] as a page. */
@Composable
fun CoachAccessScreen(state: AppState, store: AppStore, providers: Providers, coach: CoachService, onBack: () -> Unit) {
    val s = coachSettingsStringsFor(LocalFuelStrings.current.language)
    Screen(scroll = true, header = { BackHeader(LocalStrings.current.back, onBack) }) {
        Title(s.accessTitle)
        Spacer(Modifier.height(Space.l))
        ModelPicker(state, store, providers, coach, onApplied = onBack)
    }
}

/**
 * Choice of the AI access and the model: "Overfit credit" (Claude Haiku/Sonnet/Opus with the price per message) or
 * "Own key" (provider, key with a free test straight from the device, model with suggestions). Pure content: the chat
 * shows it in a bottom sheet, the settings as a page ([CoachAccessScreen]).
 *
 * Privacy: with an own key as the current access, opening the picker never talks to the Overfit backend. The credit
 * prices are only loaded when the credit part is visible and credit is already the access or the user tapped it.
 * The key test goes straight to the provider ([KeyTester]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelPicker(
    state: AppState,
    store: AppStore,
    providers: Providers,
    coach: CoachService,
    onApplied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalFuelStrings.current.language
    val s = coachSettingsStringsFor(language)
    val settings by coach.settings.state.collectAsState()
    val fuelNow = state.providerId == ProviderId.FUEL
    var credit by rememberSaveable { mutableStateOf(fuelNow) }
    // Opt-in to talk to the backend: credit is already the access, or the user tapped "Overfit credit" here.
    var creditAsked by rememberSaveable { mutableStateOf(fuelNow) }
    var tier by rememberSaveable { mutableStateOf(settings.model(ProviderId.FUEL)) }

    var provider by rememberSaveable { mutableStateOf(if (!fuelNow && state.providerId in OWN_KEY_PROVIDERS) state.providerId else ProviderId.ANTHROPIC) }
    val savedKey = if (state.hasApiKey && state.providerId == provider) store.apiKey()?.takeIf { it.isNotBlank() } else null
    var key by remember(provider) { mutableStateOf("") }
    var model by rememberSaveable(provider) { mutableStateOf(coach.settings.state.value.model(provider)) }
    var testing by remember(provider) { mutableStateOf(false) }
    var result by remember(provider) { mutableStateOf<KeyTestResult?>(null) }
    val scope = rememberCoroutineScope()
    val tester = remember(providers) { KeyTester(providers.http) }

    fun runTest() {
        if (testing) return
        val k = key.trim().ifEmpty { savedKey.orEmpty() }
        testing = true
        result = null
        val p = provider
        scope.launch {
            val r = tester.test(p, k)
            if (p == provider) { result = r; testing = false }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Segmented(credit, listOf(true to s.segCredit, false to s.segOwnKey)) { on ->
            credit = on
            if (on) creditAsked = true
        }
        Spacer(Modifier.height(Space.l))

        if (credit) {
            CreditModels(coach, providers.fuel.available, creditAsked, tier, language, s) { tier = it }
        } else {
            Text(s.provider, style = Theme.type.caption, tone = Tone.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OWN_KEY_PROVIDERS.forEach { p ->
                    Chip(ownKeyProviderLabel(p), selected = p == provider, onClick = { provider = p }, modifier = Modifier.testTag("provider:${p.name}"))
                }
            }
            Spacer(Modifier.height(Space.m))
            Text(s.apiKey, style = Theme.type.caption, tone = Tone.Muted)
            Spacer(Modifier.height(Space.xs))
            FilledField(
                key, { key = it.trim(); result = null },
                placeholder = savedKey?.let { s.savedKey(it.takeLast(4)) } ?: s.keyPlaceholder,
                modifier = Modifier.fillMaxWidth().testTag("key:input"),
                secret = true,
                onDone = ::runTest,
            )
            Spacer(Modifier.height(Space.s))
            SmallButton(if (testing) s.testing else s.test, ::runTest, Modifier.testTag("key:test"))
            result?.let { r -> KeyResultLine(r, s) }
            if (!testing && result == null && key.isEmpty() && savedKey == null) Hint(s.needKey)

            Spacer(Modifier.height(Space.m))
            Text(s.model, style = Theme.type.caption, tone = Tone.Muted)
            Spacer(Modifier.height(Space.xs))
            FilledField(
                model, { model = it.trim().take(100) },
                placeholder = CoachModels.default(provider),
                modifier = Modifier.fillMaxWidth().testTag("model:input"),
            )
            val suggestions = (result as? KeyTestResult.Ok)?.models.orEmpty().take(MAX_SUGGESTIONS)
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(Space.s))
                Text(s.suggestions, style = Theme.type.caption, tone = Tone.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    suggestions.forEach { id -> Chip(id, selected = id == model, onClick = { model = id }, modifier = Modifier.testTag("model:suggestion:$id")) }
                }
            }
            Hint(s.runsDirect(ownKeyProviderLabel(provider)))
        }

        Spacer(Modifier.height(Space.l))
        val canApply = if (credit) providers.fuel.available else (key.isNotBlank() || savedKey != null) && model.isNotBlank()
        PrimaryButton(
            s.apply,
            onClick = {
                if (credit) {
                    store.useFuel()
                    coach.settings.setModel(ProviderId.FUEL, tier)
                } else {
                    val k = key.trim().ifEmpty { savedKey ?: return@PrimaryButton }
                    store.connectAi(provider, k)
                    coach.settings.setModel(provider, model)
                }
                onApplied()
            },
            enabled = canApply,
            modifier = Modifier.fillMaxWidth().testTag("model:apply"),
        )
    }
}

/** Credit part: Claude models with their price per chat message; tap selects. */
@Composable
private fun CreditModels(
    coach: CoachService,
    available: Boolean,
    asked: Boolean,
    tier: String,
    language: Language,
    s: CoachSettingsStrings,
    onSelect: (String) -> Unit,
) {
    val f = LocalFuelStrings.current
    if (!available) {
        Text(f.fuelSoonTitle)
        Spacer(Modifier.height(Space.xs))
        Text(f.fuelSoonText, style = Theme.type.caption, tone = Tone.Muted)
        return
    }
    if (!asked) return
    val (config, reload) = rememberLoaded(coach) { coach.fuelConfig() }
    when (val c = config) {
        is Loaded.Loading -> Text(f.loading, tone = Tone.Muted)
        is Loaded.Failed -> { Text(f.unreachable, tone = Tone.Muted); SmallButton(f.retry, reload) }
        is Loaded.Ready -> {
            val cfg = c.value
            if (cfg == null) { Text(f.unreachable, tone = Tone.Muted); SmallButton(f.retry, reload); return }
            val offered = TIERS.mapNotNull { id -> cfg.tiers[id]?.takeIf { it.available }?.let { id to it } }
            if (offered.isEmpty()) { Text(f.unreachable, tone = Tone.Muted); return }
            val selected = offered.firstOrNull { it.first == tier }?.first ?: offered.firstOrNull { it.first == TIER_STANDARD }?.first ?: offered.first().first
            // The stored tier is no longer offered: the marked fallback is what "Apply" saves.
            LaunchedEffect(selected) { if (selected != tier) onSelect(selected) }
            Text(s.creditModels, style = Theme.type.caption, tone = Tone.Muted)
            Spacer(Modifier.height(Space.xs))
            Panel {
                offered.forEach { (id, info) ->
                    TierRow(info, CostEstimate.micro(info, cfg.markupBp, FuelTask.CHAT, cfg.usageProfiles), id == selected, language, s) { onSelect(id) }
                }
            }
            Hint(s.creditNote)
        }
    }
}

@Composable
private fun TierRow(info: FuelTierInfo, perMessage: Long, selected: Boolean, language: Language, s: CoachSettingsStrings, onClick: () -> Unit) {
    val name = info.displayName.ifEmpty { modelShortName(info.model) }
    Row(
        Modifier.fillMaxWidth().background(Theme.colors.surface).pressableRow(onClick)
            .semantics { this.selected = selected }
            .testTag("tier:${info.model}")
            .heightIn(min = 64.dp).padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, tone = if (selected) Tone.Default else Tone.Muted)
            Text(s.perMessage(formatCents(perMessage, language)), style = Theme.type.caption, tone = Tone.Muted)
        }
        if (selected) LineIcon(Icon.Check, size = 20.dp)
    }
}

/** Result of "Test": success with a white check (green is only for completing double-tap confirmations), otherwise an understandable error. */
@Composable
private fun KeyResultLine(r: KeyTestResult, s: CoachSettingsStrings) {
    val text = when (r) {
        is KeyTestResult.Ok -> s.keyOk(r.models.size)
        KeyTestResult.Invalid -> s.keyInvalid
        KeyTestResult.NoCredit -> s.keyNoCredit
        KeyTestResult.RateLimited -> s.keyRateLimited
        KeyTestResult.Network -> s.keyNetwork
        is KeyTestResult.Failed -> s.keyFailed(r.status)
        KeyTestResult.Empty -> s.keyEmpty
    }
    Row(Modifier.fillMaxWidth().padding(top = Space.s).testTag("key:result"), verticalAlignment = Alignment.CenterVertically) {
        if (r is KeyTestResult.Ok) {
            LineIcon(Icon.Check, size = 18.dp, color = Theme.colors.foreground)
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = Theme.type.caption)
    }
}

/** Single-line input as a grey area (no border, no underline), like [app.overfit.ui.components.MultilineInput]. */
@Composable
internal fun FilledField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: () -> Unit = {},
) {
    val c = Theme.colors
    val style = Theme.type.body.copy(color = c.foreground)
    val focus = LocalFocusManager.current
    Box(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(c.foreground),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else keyboardType, autoCorrectEnabled = false, imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onDone() }),
            modifier = Modifier.textInputArea().fillMaxWidth().heightIn(min = 48.dp)
                .clip(RoundedCornerShape(16.dp)).background(c.surface).padding(horizontal = Space.m, vertical = 13.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = c.muted), maxLines = 1)
                    inner()
                }
            },
        )
    }
}
