package app.overfit.ui.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overfit.ai.ProviderId
import app.overfit.coach.AiPurpose
import app.overfit.coach.CoachErrorKind
import app.overfit.coach.LoggingTransport
import app.overfit.coach.syncServerRequests
import app.overfit.data.db.AiRequestDao
import app.overfit.data.db.AiRequestEntity
import app.overfit.domain.Language
import app.overfit.fuel.FuelSession
import app.overfit.fuel.formatEuro
import app.overfit.fuel.modelShortName
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.RequestLogStrings
import app.overfit.i18n.requestLogStringsFor
import app.overfit.ui.chat.formatMillis
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.FloatingCard
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.CancellationException

/**
 * "Abrechnung": every AI request (coach review, chat, autofill …), newest first – when, what for, which model,
 * tokens, cost, status. The local list shows at once. Only when credit is the chosen AI access ([fuelActive]) and no
 * demo runs, the server log of the credit account is fetched in the background (also requests of other devices).
 * With an own key nothing reaches the Overfit backend (OwnKeyPrivacyTest).
 */
@Composable
fun RequestLogScreen(
    dao: AiRequestDao,
    session: FuelSession,
    fuelActive: Boolean,
    clock: () -> Long,
    onBack: () -> Unit,
) {
    val entries by remember(dao) { dao.observeRecent() }.collectAsStateWithLifecycle(null)
    val isDemo by session.demo.collectAsStateWithLifecycle()
    var offline by remember { mutableStateOf(false) }
    LaunchedEffect(fuelActive, isDemo) {
        if (!fuelActive || isDemo) return@LaunchedEffect
        val service = session.current() ?: return@LaunchedEffect
        offline = try {
            syncServerRequests(service, dao)
            false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            true
        }
    }
    Screen(scroll = true, header = { BackHeader(LocalStrings.current.back, onBack) }) {
        RequestLogContent(entries, offline, clock())
    }
}

/** Title, explanation, 30-day sum and the cards; [entries] null = still loading. */
@Composable
internal fun RequestLogContent(entries: List<AiRequestEntity>?, offline: Boolean, now: Long) {
    val language = LocalFuelStrings.current.language
    val r = requestLogStringsFor(language)
    Title(r.title)
    Text(r.intro, Modifier.padding(top = Space.s), style = Theme.type.caption, tone = Tone.Muted)
    if (offline) Text(r.offline, Modifier.padding(top = Space.s), style = Theme.type.caption, tone = Tone.Muted)
    val fuel = entries.orEmpty().filter { it.providerId == ProviderId.FUEL.name }
    if (fuel.isNotEmpty()) {
        val since = now - 30L * 24 * 3_600_000
        val sum = fuel.filter { it.startedAt >= since }.sumOf { it.costMicro ?: 0 }
        Text(r.last30Days(formatRequestCost(sum, language)), Modifier.padding(top = Space.m), style = Theme.type.caption)
    }
    Spacer(Modifier.height(Space.l))
    when {
        entries == null -> Text(LocalFuelStrings.current.loading, tone = Tone.Muted)
        entries.isEmpty() -> Text(r.empty, tone = Tone.Muted)
        else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            entries.forEach { RequestCard(it, r, language) }
        }
    }
}

@Composable
private fun RequestCard(e: AiRequestEntity, r: RequestLogStrings, language: Language) {
    val s = LocalStrings.current
    var open by rememberSaveable(e.id) { mutableStateOf(false) }
    val isFuel = e.providerId == ProviderId.FUEL.name
    val failed = e.status != LoggingTransport.STATUS_OK
    FloatingCard(
        Modifier.testTag("request:${e.id}").semantics { stateDescription = if (open) r.hideDetails else r.showDetails },
        onClick = { open = !open },
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.m)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(purposeLabel(e.purpose, r), Modifier.weight(1f), style = Theme.type.label)
                Spacer(Modifier.width(Space.m))
                LineIcon(if (open) Icon.ChevronUp else Icon.ChevronDown, size = app.overfit.ui.theme.IconSize.small, color = Theme.colors.muted)
            }
            // Date and time on their own line, so a long purpose never gets cut at 360 dp.
            Text(formatMillis(e.startedAt, s), Modifier.padding(top = 2.dp), style = Theme.type.caption, tone = Tone.Muted)
            val model = modelShortName(e.servedModel ?: e.requestedModel)
            val provider = if (isFuel) r.providerFuel else r.viaProvider(providerName(e.providerId))
            Text("$model · $provider", style = Theme.type.caption, tone = Tone.Muted)
            val cost = when {
                !isFuel -> r.costOwnKey
                (e.costMicro ?: 0) == 0L -> r.costNone
                else -> formatRequestCost(e.costMicro!!, language)
            }
            if (failed) {
                Text(cost, style = Theme.type.caption, tone = Tone.Muted)
                Text(r.statusFailed(errorText(e.errorKind, r)), Modifier.padding(top = 2.dp), style = Theme.type.caption)
            } else {
                Text("$cost · ${r.statusOk}", style = Theme.type.caption, tone = Tone.Muted)
            }
            if (open) {
                Spacer(Modifier.height(Space.s))
                val lines = buildList {
                    if (e.inputTokens != null || e.outputTokens != null) {
                        add(r.tokens(groupDigits(e.inputTokens ?: 0, language), groupDigits(e.outputTokens ?: 0, language)))
                    }
                    if (e.cacheReadTokens != null || e.cacheWriteTokens != null) {
                        add(r.cache(groupDigits(e.cacheReadTokens ?: 0, language), groupDigits(e.cacheWriteTokens ?: 0, language)))
                    }
                    e.durationMs?.let { add(r.duration(formatSeconds(it, language))) }
                    val served = e.servedModel
                    if (served != null && served != e.requestedModel) {
                        add(r.fallback(modelShortName(e.requestedModel), e.fallbackReason, modelShortName(served)))
                    } else if (failed && !e.fallbackReason.isNullOrBlank()) {
                        add(r.providerError(e.fallbackReason!!))
                    }
                    add(e.serverId?.let { r.sourceServer(it) } ?: r.sourceDevice)
                }
                lines.forEach { Text(it, Modifier.padding(top = Space.xs), style = Theme.type.caption, tone = Tone.Muted) }
            }
        }
    }
}

internal fun purposeLabel(purpose: String, r: RequestLogStrings): String = when (AiPurpose.of(purpose)) {
    AiPurpose.REVIEW -> r.purposeReview
    AiPurpose.CHAT -> r.purposeChat
    AiPurpose.AUTOFILL -> r.purposeAutofill
    AiPurpose.PLAN -> r.purposePlan
    AiPurpose.OTHER -> r.purposeOther
}

/** Brand names, the same in every language. */
private fun providerName(id: String): String = when (id) {
    ProviderId.ANTHROPIC.name -> "Anthropic"
    ProviderId.OPENAI.name -> "OpenAI"
    ProviderId.OPENROUTER.name -> "OpenRouter"
    ProviderId.GEMINI.name -> "Google Gemini"
    else -> id
}

/** Understandable text for a [CoachErrorKind] name or a server error code; unknown codes are shown as they are. */
internal fun errorText(code: String?, r: RequestLogStrings): String = when (code) {
    "insufficient_funds", CoachErrorKind.NO_FUEL.name -> r.errInsufficientFunds
    "upstream_unavailable" -> r.errUpstreamUnavailable
    "upstream_rejected" -> r.errUpstreamRejected
    "tier_unavailable", "model_not_allowed" -> r.errModelUnavailable
    "invalid_request", "invalid_max_tokens" -> r.errInvalidRequest
    "body_too_large" -> r.errTooLarge
    "images_not_supported" -> r.errImages
    "internal_error" -> r.errInternal
    "rate_limited", CoachErrorKind.RATE_LIMIT.name -> r.errRateLimit
    "network", CoachErrorKind.NETWORK.name -> r.errNetwork
    "timeout", CoachErrorKind.TIMEOUT.name -> r.errTimeout
    CoachErrorKind.SERVER.name -> r.errServer
    CoachErrorKind.NO_ACCESS.name -> r.errNoAccess
    CoachErrorKind.AUTH.name -> r.errAuth
    CoachErrorKind.BAD_REQUEST.name -> r.errBadRequest
    CoachErrorKind.INVALID_ANSWER.name -> r.errInvalidAnswer
    null -> "?"
    else -> code
}

/** Cost of requests: below 1 € in cents with one decimal ("0,4 ct"), from 1 € in euro ("1,23 €"). */
internal fun formatRequestCost(micro: Long, language: Language): String {
    if (micro >= 1_000_000) return formatEuro(micro, language)
    val tenths = (micro + 500) / 1_000 // tenths of a cent
    val sep = if (language == Language.DE) "," else "."
    if (tenths == 0L && micro > 0) return "<0${sep}1 ct"
    return "${tenths / 10}$sep${tenths % 10} ct"
}

/** 12345 → "12.345" (DE) / "12,345" (EN). */
internal fun groupDigits(n: Long, language: Language): String {
    val sep = if (language == Language.DE) '.' else ','
    val digits = (if (n < 0) -n else n).toString()
    val grouped = digits.reversed().chunked(3).joinToString(sep.toString()).reversed()
    return if (n < 0) "-$grouped" else grouped
}

/** 3240 ms → "3,2" (DE) / "3.2" (EN). */
private fun formatSeconds(ms: Long, language: Language): String {
    val tenths = (ms + 50) / 100
    return "${tenths / 10}${if (language == Language.DE) "," else "."}${tenths % 10}"
}
