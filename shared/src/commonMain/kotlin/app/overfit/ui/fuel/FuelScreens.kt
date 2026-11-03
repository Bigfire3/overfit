package app.overfit.ui.fuel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overfit.domain.Language
import app.overfit.fuel.FuelAccount
import app.overfit.fuel.FuelConfig
import app.overfit.fuel.FuelException
import app.overfit.fuel.FuelService
import app.overfit.fuel.FuelSession
import app.overfit.fuel.LedgerEntry
import app.overfit.fuel.formatCost
import app.overfit.fuel.formatEuro
import app.overfit.i18n.FuelStrings
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.BigInput
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.TextButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun epochToDate(ts: Long) = Instant.fromEpochSeconds(ts).toLocalDateTime(TimeZone.currentSystemDefault()).date

@Composable
private fun DemoBanner(session: FuelSession, t: FuelStrings) {
    Text(t.demoBanner, style = Theme.type.caption, tone = Tone.Muted)
    TextButton(t.demoExit, { session.endDemo() })
    Spacer(Modifier.height(Space.m))
}

@Composable
private fun ErrorBlock(t: FuelStrings, onRetry: () -> Unit) {
    Text(t.unreachable, tone = Tone.Muted)
    TextButton(t.retry, onRetry, tone = Tone.Default)
}

/** Profile → Fuel: balance, top-up packages, Spot, transactions, recovery. Not configured: "coming soon" + demo. */
@Composable
fun FuelScreen(
    session: FuelSession,
    onBack: () -> Unit,
    onLedger: () -> Unit,
    onRecovery: () -> Unit,
    onSpotInvite: () -> Unit,
    onSpotRedeem: () -> Unit,
    /** True when Fuel is the chosen AI access. With an own key the screen asks first (no request before). */
    fuelActive: Boolean = true,
    onRedeemCode: () -> Unit = {},
    /** How this build was installed; with `/v1/config` it decides whether "Top up on the web" is shown. */
    distribution: app.overfit.fuel.Distribution = app.overfit.fuel.Distribution.Sideload,
) {
    val t = LocalFuelStrings.current
    val language = t.language
    val isDemo by session.demo.collectAsStateWithLifecycle()
    val service = remember(isDemo) { session.current() } // re-evaluated when the demo starts/ends
    var optedIn by rememberSaveable { mutableStateOf(false) }

    if (service != null && !isDemo && !fuelActive && !optedIn) {
        Screen(
            header = { BackHeader(LocalStrings.current.back, onBack) },
            footer = { PrimaryButton(t.optInButton, onClick = { optedIn = true }) },
        ) {
            Title(t.fuel)
            Spacer(Modifier.height(Space.m))
            Text(t.fuelTagline, tone = Tone.Muted)
            Spacer(Modifier.height(Space.xl))
            Text(t.optInText, tone = Tone.Muted)
        }
        return
    }

    if (service == null) {
        Screen(
            header = { BackHeader(LocalStrings.current.back, onBack) },
            footer = { PrimaryButton(t.demoView, onClick = { session.startDemo() }) },
        ) {
            Title(t.fuel)
            Spacer(Modifier.height(Space.m))
            Text(t.fuelTagline, tone = Tone.Muted)
            Spacer(Modifier.height(Space.xl))
            Text(t.fuelSoonTitle)
            Spacer(Modifier.height(Space.s))
            Text(t.fuelSoonText, tone = Tone.Muted)
        }
        return
    }

    val (account, reload) = rememberLoaded(service) { service.account() }
    val (config, _) = rememberLoaded(service) { service.config() }
    // Back from the web purchase page (browser → app, or the overfit://guthaben link): show the new balance.
    var resumes by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(service) {
        if (resumes++ > 0) reload()
        onPauseOrDispose { }
    }
    Screen(scroll = true, header = { BackHeader(LocalStrings.current.back, onBack) }) {
        Title(t.fuel)
        Spacer(Modifier.height(Space.s))
        if (isDemo) DemoBanner(session, t)
        if ((config as? Loaded.Ready<FuelConfig>)?.value?.mode == "test") {
            Text(t.testModeNote, Modifier.padding(top = Space.s), style = Theme.type.caption, tone = Tone.Muted)
        }
        when (val a = account) {
            is Loaded.Loading -> Text(t.loading, tone = Tone.Muted)
            is Loaded.Failed -> ErrorBlock(t, reload)
            is Loaded.Ready -> Balance(a.value, language, t)
        }

        SectionTitle(t.topUp)
        val packages = (config as? Loaded.Ready<FuelConfig>)?.value?.packages.orEmpty()
        packages.forEach { p ->
            ListRow(t.topUpPackage(formatEuro(p.priceMicro, language)), Tone.Muted) {
                Column {
                    Text(t.topUpGives(formatEuro(p.creditMicro, language)))
                    Text(t.storeSoon, style = Theme.type.caption, tone = Tone.Muted)
                }
            }
        }
        Text(t.topUpNote, style = Theme.type.caption, tone = Tone.Muted, modifier = Modifier.padding(top = Space.s))
        val markupBp = (config as? Loaded.Ready<FuelConfig>)?.value?.markupBp ?: 0
        if (markupBp > 0) {
            val uriHandler = LocalUriHandler.current
            Text(t.pricesNote(formatPercent(markupBp)), style = Theme.type.caption, tone = Tone.Muted, modifier = Modifier.padding(top = Space.xs))
            ListRow(t.pricesHow, onClick = { uriHandler.openUri(PRICES_URL + if (language == Language.EN) "?lang=en" else "?lang=de") })
        }
        WebCreditSection(service, (config as? Loaded.Ready<FuelConfig>)?.value, distribution, language, isDemo, onRedeemCode)

        SectionTitle(t.spot)
        ListRow(t.spotInvite, onClick = onSpotInvite)
        ListRow(t.spotRedeem, onClick = onSpotRedeem)

        SectionTitle(t.fuel)
        ListRow(t.ledger, onClick = onLedger)
        ListRow(t.recovery, onClick = onRecovery)
    }
}

@Composable
private fun Balance(account: FuelAccount, language: Language, t: FuelStrings) {
    Spacer(Modifier.height(Space.m))
    Text(t.balanceLabel, style = Theme.type.caption, tone = Tone.Muted)
    Text("${t.fuel} · ${formatEuro(account.balanceMicro, language)}", style = Theme.type.title)
    account.expiring.sumOf { it.amountMicro }.takeIf { it > 0 }?.let { amount ->
        val until = account.expiring.minOf { it.expiresAt }
        Spacer(Modifier.height(Space.xs))
        Text(
            t.expiringLine(formatEuro(amount, language), LocalStrings.current.date(epochToDate(until))),
            style = Theme.type.caption, tone = Tone.Muted,
        )
    }
}

/** Transactions, newest first, paged. */
@Composable
fun LedgerScreen(session: FuelSession, onBack: () -> Unit) {
    val t = LocalFuelStrings.current
    val s = LocalStrings.current
    val language = t.language
    val service = session.current()
    val entries = remember { mutableStateListOf<LedgerEntry>() }
    var next by remember { mutableStateOf<Long?>(null) }
    var state by remember { mutableStateOf<Loaded<Unit>>(Loaded.Loading) }
    val scope = rememberCoroutineScope()

    fun load(before: Long?) {
        if (service == null) return
        state = Loaded.Loading
        scope.launch {
            state = try {
                val page = service.ledger(before)
                entries += page.entries
                next = page.next
                Loaded.Ready(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Loaded.Failed((e as? FuelException)?.code ?: "network")
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(service) { entries.clear(); load(null) }

    Screen(scroll = true, header = { BackHeader(s.back, onBack) }) {
        Title(t.ledger)
        Spacer(Modifier.height(Space.l))
        Hairline()
        if (entries.isEmpty() && state is Loaded.Ready) Text(t.ledgerEmpty, tone = Tone.Muted, modifier = Modifier.padding(top = Space.m))
        entries.forEach { e ->
            val w = app.overfit.i18n.webCreditStringsFor(language)
            val title = when (e.type) {
                "usage" -> t.ledgerUsage
                "purchase" -> if (e.source == "web") w.ledgerWebPurchase else t.ledgerPurchase
                "code_redeem" -> w.ledgerCode
                "refund" -> w.ledgerRefund
                "chargeback" -> w.ledgerChargeback
                "spot_bonus" -> t.ledgerSpotBonus
                "spot_reward" -> t.ledgerSpotReward
                else -> e.type
            }
            val sign = if (e.amountMicro > 0) "+" else ""
            ListRow(title, tone = Tone.Default, trailing = {
                Text(sign + (if (e.type == "usage") formatCost(e.amountMicro, language) else formatEuro(e.amountMicro, language)))
            })
            // detail line under the row: date and, for usage, tokens
            val detail = buildString {
                append(s.date(epochToDate(e.ts)))
                if (e.type == "usage") {
                    e.model?.let { append(" · ").append(app.overfit.fuel.modelLabel(it)) }
                    append(" · ").append(t.ledgerTokens(e.inputTokens + e.cacheReadTokens + e.cacheWriteTokens, e.outputTokens))
                }
            }
            val chosen = if (e.type == "usage" && e.fallback) e.tier?.let { app.overfit.fuel.modelOfTier(it) }?.let { app.overfit.fuel.modelShortName(it) } else null
            Text(detail, style = Theme.type.caption, tone = Tone.Muted, modifier = Modifier.padding(bottom = if (chosen == null) Space.s else Space.xs))
            if (chosen != null) Text(t.ledgerFallback(chosen), style = Theme.type.caption, tone = Tone.Muted, modifier = Modifier.padding(bottom = Space.s))
        }
        when (val st = state) {
            is Loaded.Loading -> Text(t.loading, tone = Tone.Muted, modifier = Modifier.padding(top = Space.m))
            is Loaded.Failed -> ErrorBlock(t) { load(next) }
            is Loaded.Ready -> if (next != null) TextButton(t.loadMore, { load(next) }, tone = Tone.Default)
        }
    }
}

/** Create/show the recovery code (shown once) and restore on a new phone. */
@Composable
fun RecoveryScreen(session: FuelSession, onBack: () -> Unit, onRestored: () -> Unit) {
    val t = LocalFuelStrings.current
    val s = LocalStrings.current
    val service = session.current()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var code by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Screen(scroll = true, header = { BackHeader(s.back, onBack) }) {
        Title(t.recovery)
        Spacer(Modifier.height(Space.m))
        if (session.demo.collectAsStateWithLifecycle().value) DemoBanner(session, t)
        Text(t.recoveryText, tone = Tone.Muted)
        Spacer(Modifier.height(Space.l))
        val shown = code
        if (shown != null) {
            Text(shown, style = Theme.type.title)
            Spacer(Modifier.height(Space.s))
            TextButton(if (copied) t.recoveryCopied else t.recoveryCopy, {
                clipboard.setText(AnnotatedString(shown)); copied = true
            }, tone = Tone.Default)
        }
        Text(t.recoveryReplaceWarning, style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.height(Space.m))
        PrimaryButton(t.recoveryCreate, enabled = service != null && !busy, onClick = {
            if (service == null) return@PrimaryButton
            busy = true
            scope.launch {
                code = try { service.createRecoveryCode() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { message = t.unreachable; null }
                copied = false
                busy = false
            }
        })

        SectionTitle(t.restoreTitle)
        BigInput(input, { input = it.uppercase().take(40); message = null }, t.restorePlaceholder)
        Spacer(Modifier.height(Space.m))
        message?.let { Text(it, style = Theme.type.caption); Spacer(Modifier.height(Space.m)) }
        TextButton(t.restoreAction, {
            if (service == null || input.isBlank() || busy) return@TextButton
            busy = true
            scope.launch {
                message = try {
                    service.restore(input); onRestored(); t.restoreDone
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: FuelException) {
                    if (e.code == "invalid_recovery_code") t.restoreInvalid else t.unreachable
                } catch (_: Exception) { t.unreachable }
                busy = false
            }
        }, tone = Tone.Default)
    }
}

/** Explains how the prices are made up (static page, same text as docs/monetization.md). */
private const val PRICES_URL = "https://bigfire3.github.io/overfit/preise/"

/** 6900 → "69", 750 → "7,5" in a locale-neutral way (the string is built per language by the caller's text). */
private fun formatPercent(bp: Int): String = if (bp % 100 == 0) (bp / 100).toString() else (bp / 100.0).toString()
