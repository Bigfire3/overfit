package app.overfit.ui.fuel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
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
import app.overfit.ui.components.AnimatedItems
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Reveal
import app.overfit.ui.components.RevealNotNull
import app.overfit.ui.components.RollingText
import app.overfit.ui.components.Swap
import app.overfit.ui.components.rememberHaptics
import app.overfit.ui.theme.Motion
import app.overfit.ui.components.BigInput
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Hint
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.Panel
import app.overfit.ui.components.PanelItem
import app.overfit.ui.components.PanelSection
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun epochToDate(ts: Long) = Instant.fromEpochSeconds(ts).toLocalDateTime(TimeZone.currentSystemDefault()).date

@Composable
private fun DemoBanner(session: FuelSession, t: FuelStrings) {
    Text(t.demoBanner, style = Theme.type.caption, tone = Tone.Muted)
    SmallButton(t.demoExit, { session.endDemo() })
    Spacer(Modifier.height(Space.m))
}

@Composable
private fun ErrorBlock(t: FuelStrings, onRetry: () -> Unit) {
    Text(t.unreachable, tone = Tone.Muted)
    SmallButton(t.retry, onRetry, tone = Tone.Default)
}

/** Which of the three faces the Fuel screen shows; the body cross-fades between them. */
private sealed interface FuelView {
    data object OptIn : FuelView
    data object Soon : FuelView
    data class Account(val service: FuelService) : FuelView
}

/** Profile → Fuel: balance, top-up packages, transactions, recovery. Not configured: "coming soon" + demo. */
@Composable
fun FuelScreen(
    session: FuelSession,
    onBack: () -> Unit,
    onLedger: () -> Unit,
    onRecovery: () -> Unit,
    /** True when Fuel is the chosen AI access. With an own key the screen asks first (no request before). */
    fuelActive: Boolean = true,
    onRedeemCode: () -> Unit = {},
    /** How this build was installed; with `/v1/config` it decides whether "Top up on the web" is shown. */
    distribution: app.overfit.fuel.Distribution = app.overfit.fuel.Distribution.Sideload,
    /** Opens the request log ("Abrechnung"); null = no row. */
    onRequestLog: (() -> Unit)? = null,
) {
    val t = LocalFuelStrings.current
    val isDemo by session.demo.collectAsStateWithLifecycle()
    val service = remember(isDemo) { session.current() } // re-evaluated when the demo starts/ends
    var optedIn by rememberSaveable { mutableStateOf(false) }
    val view = when {
        service == null -> FuelView.Soon
        !isDemo && !fuelActive && !optedIn -> FuelView.OptIn
        else -> FuelView.Account(service)
    }

    // One screen for all three faces: the title stays, the body fades through, the footer button slides away.
    Screen(
        scroll = true,
        header = { BackHeader(LocalStrings.current.back, onBack) },
        footer = {
            RevealNotNull(view.takeIf { it !is FuelView.Account }, from = Alignment.Bottom) { v ->
                val optIn = v == FuelView.OptIn
                PrimaryButton(
                    if (optIn) t.optInButton else t.demoView,
                    onClick = { if (optIn) optedIn = true else session.startDemo() },
                    icon = if (optIn) null else app.overfit.ui.components.Icon.ChevronRight,
                )
            }
        },
    ) {
        Title(t.fuel)
        Swap(view, contentKey = { if (it is FuelView.Account) it.service else it }) { v ->
            Column {
                when (v) {
                    FuelView.OptIn -> {
                        Spacer(Modifier.height(Space.m))
                        Text(t.fuelTagline, tone = Tone.Muted)
                        Spacer(Modifier.height(Space.xl))
                        Text(t.optInText, tone = Tone.Muted)
                    }
                    FuelView.Soon -> {
                        Spacer(Modifier.height(Space.m))
                        Text(t.fuelTagline, tone = Tone.Muted)
                        Spacer(Modifier.height(Space.xl))
                        Text(t.fuelSoonTitle)
                        Spacer(Modifier.height(Space.s))
                        Text(t.fuelSoonText, tone = Tone.Muted)
                    }
                    is FuelView.Account -> FuelAccountBody(
                        session, v.service, isDemo, onLedger, onRecovery, onRedeemCode, distribution, onRequestLog,
                    )
                }
            }
        }
    }
}

@Composable
private fun FuelAccountBody(
    session: FuelSession,
    service: FuelService,
    isDemo: Boolean,
    onLedger: () -> Unit,
    onRecovery: () -> Unit,
    onRedeemCode: () -> Unit,
    distribution: app.overfit.fuel.Distribution,
    onRequestLog: (() -> Unit)?,
) {
    val t = LocalFuelStrings.current
    val language = t.language
    val haptics = rememberHaptics()
    val (loadedAccount, reload) = rememberLoaded(service) { service.account() }
    val (config, _) = rememberLoaded(service) { service.config() }
    // Back from the web purchase page (browser → app, or the overfit://guthaben link): show the new balance.
    var resumes by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(service) {
        if (resumes++ > 0) reload()
        onPauseOrDispose { }
    }
    // While the balance reloads, keep showing the last one, so the new amount rolls instead of the panel flickering.
    val lastReady = remember(service) { arrayOfNulls<Loaded.Ready<FuelAccount>>(1) }
    if (loadedAccount is Loaded.Ready) lastReady[0] = loadedAccount
    val account = if (loadedAccount is Loaded.Loading) lastReady[0] ?: loadedAccount else loadedAccount
    val balance = (account as? Loaded.Ready)?.value?.balanceMicro
    val lastBalance = remember(service) { arrayOfNulls<Long>(1) }
    LaunchedEffect(balance) {
        val before = lastBalance[0]
        if (balance != null && before != null && balance > before) haptics.confirm()
        if (balance != null) lastBalance[0] = balance
    }
    val failed = account is Loaded.Failed
    LaunchedEffect(failed) { if (failed) haptics.reject() }
    val readyConfig = (config as? Loaded.Ready<FuelConfig>)?.value

    Spacer(Modifier.height(Space.s))
    Reveal(isDemo) { Column { DemoBanner(session, t) } }
    Reveal(readyConfig?.mode == "test") {
        Text(t.testModeNote, Modifier.padding(top = Space.s), style = Theme.type.caption, tone = Tone.Muted)
    }
    Swap(account, contentKey = { it::class }) { a ->
        Column {
            when (a) {
                is Loaded.Loading -> BalancePlaceholder(t)
                is Loaded.Failed -> ErrorBlock(t, reload)
                is Loaded.Ready -> Balance(a.value, language, t)
            }
        }
    }

    SectionTitle(t.topUp)
    // The whole top-up block waits for the config, so it settles in one motion instead of piece by piece.
    Swap(config is Loaded.Loading) { loading ->
        Column {
            if (!loading) {
                val packages = readyConfig?.packages.orEmpty()
                if (packages.isNotEmpty()) {
                    Panel {
                        packages.forEach { p ->
                            // Both values openly: what you get and what the store charges (VAT and store fee on top).
                            ListRow(t.storePackage(formatEuro(p.creditMicro, language), formatEuro(p.priceMicro, language)), Tone.Muted) {
                                Text(t.storeSoon, style = Theme.type.caption, tone = Tone.Muted)
                            }
                        }
                    }
                    Hint(t.storeChannel)
                }
                Hint(t.topUpNote)
                val markupBp = readyConfig?.markupBp ?: 0
                if (markupBp > 0) {
                    val uriHandler = LocalUriHandler.current
                    Hint(t.pricesNote(formatPercent(markupBp)))
                    Spacer(Modifier.height(Space.s))
                    Panel { ListRow(t.pricesHow, onClick = { uriHandler.openUri(PRICES_URL + if (language == Language.EN) "?lang=en" else "?lang=de") }) }
                }
                WebCreditSection(service, readyConfig, distribution, language, isDemo, onRedeemCode)
            }
        }
    }

    PanelSection(t.fuel) {
        if (onRequestLog != null) ListRow(app.overfit.i18n.requestLogStringsFor(language).title, onClick = onRequestLog)
        ListRow(t.ledger, onClick = onLedger)
        ListRow(t.recovery, onClick = onRecovery)
    }
}

/** Same height as [Balance], so nothing below moves when the balance arrives. */
@Composable
private fun BalancePlaceholder(t: FuelStrings) {
    Spacer(Modifier.height(Space.m))
    Panel {
        PanelItem {
            Text(t.loading, style = Theme.type.caption, tone = Tone.Muted)
            Text("", style = Theme.type.title)
        }
    }
}

/** Balance: "4,20 € Guthaben". Credit is always "X € Guthaben"; real money (prices) only appears with the packages. */
@Composable
private fun Balance(account: FuelAccount, language: Language, t: FuelStrings) {
    Spacer(Modifier.height(Space.m))
    Panel {
        PanelItem {
            Text(t.balanceLabel, style = Theme.type.caption, tone = Tone.Muted)
            RollingText(t.creditAmount(formatEuro(account.balanceMicro, language)), style = Theme.type.title)
            RevealNotNull(reservedNote(account.reservedMicro, t)) {
                Text(it, Modifier.padding(top = Space.xs).testTag("fuel:reserved"), style = Theme.type.caption, tone = Tone.Muted)
            }
        }
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
    // Held for running requests (first page of newer servers); shown as its own row so the sum = balance + reserved.
    var reserved by remember { mutableStateOf(0L) }
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
                if (before == null) reserved = page.reservedMicro
                Loaded.Ready(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Loaded.Failed((e as? FuelException)?.code ?: "network")
            }
        }
    }
    LaunchedEffect(service) { entries.clear(); reserved = 0; load(null) }

    Screen(scroll = true, header = { BackHeader(s.back, onBack) }) {
        Title(t.ledger)
        Spacer(Modifier.height(Space.l))
        RevealNotNull(reserved.takeIf { it > 0 }) { held -> Column {
            Panel {
                PanelItem {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().testTag("ledger:reserved"),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    ) {
                        Text(t.ledgerReserved, Modifier.weight(1f).padding(end = Space.m))
                        Text(t.creditAmount(formatEuro(-held, language)))
                    }
                }
            }
            Hint(t.ledgerReservedHint)
            Spacer(Modifier.height(Space.m))
        } }
        Reveal(entries.isEmpty() && state is Loaded.Ready) { Text(t.ledgerEmpty, tone = Tone.Muted, modifier = Modifier.padding(top = Space.m)) }
        // Every further page grows in under the ones already there.
        Reveal(entries.isNotEmpty()) { Panel { AnimatedItems(entries.toList(), key = { it.id to it.ts }) { _, e ->
            val w = app.overfit.i18n.webCreditStringsFor(language)
            val title = when (e.type) {
                "usage" -> t.ledgerUsage
                "purchase" -> if (e.source == "web") w.ledgerWebPurchase else t.ledgerPurchase
                "code_redeem" -> w.ledgerCode
                "refund" -> w.ledgerRefund
                "chargeback" -> w.ledgerChargeback
                // Other types of older accounts (e.g. former starting credit): neutral credit/debit.
                else -> if (e.amountMicro < 0) t.ledgerDebit else t.ledgerCredit
            }
            val sign = if (e.amountMicro > 0) "+" else ""
            // Amounts are credit ("… € Guthaben").
            val amount = if (e.type == "usage") t.creditAmount(formatCost(e.amountMicro, language))
            else t.creditAmount(sign + formatEuro(e.amountMicro, language))
            PanelItem {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text(title, Modifier.weight(1f).padding(end = Space.m))
                Text(amount)
            }
            // detail line under the row: paid/credited/used/refunded, date and, for usage, tokens
            val detail = buildString {
                append(ledgerKindLabel(e.kind, t)).append(" · ")
                append(s.date(epochToDate(e.ts)))
                if (e.type == "usage") {
                    e.model?.let { append(" · ").append(app.overfit.fuel.modelLabel(it)) }
                    append(" · ").append(t.ledgerTokens(e.inputTokens + e.cacheReadTokens + e.cacheWriteTokens, e.outputTokens))
                }
            }
            val chosen = if (e.type == "usage" && e.fallback) (e.requestedModel ?: e.tier?.let { app.overfit.fuel.modelOfTierAt(it, e.ts) })?.let { app.overfit.fuel.modelShortName(it) } else null
            Text(detail, style = Theme.type.caption, tone = Tone.Muted, modifier = Modifier.padding(top = Space.xs))
            if (chosen != null) Text(t.ledgerFallback(chosen), style = Theme.type.caption, tone = Tone.Muted, modifier = Modifier.padding(top = Space.xs))
            }
        } } }
        Swap(state, contentKey = { it::class }) { st ->
            Column {
                when (st) {
                    is Loaded.Loading -> Text(t.loading, tone = Tone.Muted, modifier = Modifier.padding(top = Space.m))
                    is Loaded.Failed -> ErrorBlock(t) { load(next) }
                    is Loaded.Ready -> Reveal(next != null) { SmallButton(t.loadMore, { load(next) }, tone = Tone.Default) }
                }
            }
        }
    }
}

internal fun ledgerKindLabel(kind: app.overfit.fuel.LedgerKind, t: FuelStrings): String = when (kind) {
    app.overfit.fuel.LedgerKind.Paid -> t.ledgerKindPaid
    app.overfit.fuel.LedgerKind.Credited -> t.ledgerKindCredited
    app.overfit.fuel.LedgerKind.Used -> t.ledgerKindUsed
    app.overfit.fuel.LedgerKind.Refunded -> t.ledgerKindRefunded
    app.overfit.fuel.LedgerKind.Deducted -> t.ledgerKindDeducted
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
    // The error of "create code" stands under that button, not under the restore field.
    var createError by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    val busy = creating || restoring
    val haptics = rememberHaptics()
    LaunchedEffect(copied) { if (copied) { delay(COPIED_RESET_MS); copied = false } }

    fun restore() {
        if (service == null || input.isBlank() || busy) return
        restoring = true
        scope.launch {
            message = try {
                service.restore(input); onRestored(); haptics.confirm(); t.restoreDone
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: FuelException) {
                haptics.reject()
                if (e.code == "invalid_recovery_code") t.restoreInvalid else t.unreachable
            } catch (_: Exception) { haptics.reject(); t.unreachable }
            restoring = false
        }
    }

    Screen(scroll = true, header = { BackHeader(s.back, onBack) }) {
        Title(t.recovery)
        Spacer(Modifier.height(Space.m))
        val isDemo = session.demo.collectAsStateWithLifecycle().value
        Reveal(isDemo) { Column { DemoBanner(session, t) } }
        Text(t.recoveryText, tone = Tone.Muted)
        Spacer(Modifier.height(Space.l))
        RevealNotNull(code) { shown ->
            Column {
                Panel {
                    PanelItem {
                        Text(shown, style = Theme.type.title)
                        Spacer(Modifier.height(Space.s))
                        SmallButton(if (copied) t.recoveryCopied else t.recoveryCopy, {
                            clipboard.setText(AnnotatedString(shown)); copied = true; haptics.confirm()
                        }, tone = Tone.Default)
                    }
                }
                Spacer(Modifier.height(Space.s))
            }
        }
        Text(t.recoveryReplaceWarning, style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.height(Space.m))
        PrimaryButton(t.recoveryCreate, enabled = service != null && !busy, loading = creating, onClick = {
            if (service == null) return@PrimaryButton
            creating = true
            createError = null
            scope.launch {
                val created = try { service.createRecoveryCode() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
                if (created != null) { code = created; haptics.confirm() } else { createError = t.unreachable; haptics.reject() }
                copied = false
                creating = false
            }
        })
        RevealNotNull(createError) { Text(it, Modifier.padding(top = Space.s), style = Theme.type.caption) }

        SectionTitle(t.restoreTitle)
        BigInput(input, { input = it.uppercase().take(40); message = null }, t.restorePlaceholder, onDone = { restore() })
        Spacer(Modifier.height(Space.m))
        RevealNotNull(message) { Column { Text(it, style = Theme.type.caption); Spacer(Modifier.height(Space.m)) } }
        // Dimmed while there is nothing to restore or a request runs (the tap is ignored then).
        val restoreAlpha by animateFloatAsState(if (service != null && input.isNotBlank() && !busy) 1f else 0.3f, Motion.snappy(), label = "restoreEnabled")
        SmallButton(t.restoreAction, { restore() }, Modifier.graphicsLayer { alpha = restoreAlpha }, tone = Tone.Default)
    }
}

/** How long "Kopiert" stays before the button reads "Kopieren" again (not an animation: a state timeout). */
private const val COPIED_RESET_MS = 2_000L

/** Explains how the prices are made up (static page, same text as docs/monetization.md). */
private const val PRICES_URL = "https://bigfire3.github.io/overfit/preise/"

/** 2800 → "28", 750 → "7,5" in a locale-neutral way (the string is built per language by the caller's text). */
private fun formatPercent(bp: Int): String = if (bp % 100 == 0) (bp / 100).toString() else (bp / 100.0).toString()
