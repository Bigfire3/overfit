package app.overfit.ui.fuel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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

/** Profile → Fuel: balance, top-up packages, invitations, transactions, recovery. Not configured: "coming soon" + demo. */
@Composable
fun FuelScreen(
    session: FuelSession,
    onBack: () -> Unit,
    onLedger: () -> Unit,
    onRecovery: () -> Unit,
    onInviteShow: () -> Unit,
    onInviteRedeem: () -> Unit,
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
            is Loaded.Ready -> Balance(a.value, (config as? Loaded.Ready<FuelConfig>)?.value, language, t)
        }

        SectionTitle(t.topUp)
        val packages = (config as? Loaded.Ready<FuelConfig>)?.value?.packages.orEmpty()
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
        val markupBp = (config as? Loaded.Ready<FuelConfig>)?.value?.markupBp ?: 0
        if (markupBp > 0) {
            val uriHandler = LocalUriHandler.current
            Hint(t.pricesNote(formatPercent(markupBp)))
            Spacer(Modifier.height(Space.s))
            Panel { ListRow(t.pricesHow, onClick = { uriHandler.openUri(PRICES_URL + if (language == Language.EN) "?lang=en" else "?lang=de") }) }
        }
        WebCreditSection(service, (config as? Loaded.Ready<FuelConfig>)?.value, distribution, language, isDemo, onRedeemCode)

        PanelSection(t.invite) {
            ListRow(t.inviteShow, onClick = onInviteShow)
            ListRow(t.inviteRedeem, onClick = onInviteRedeem)
        }

        PanelSection(t.fuel) {
            ListRow(t.ledger, onClick = onLedger)
            ListRow(t.recovery, onClick = onRecovery)
        }
    }
}

/**
 * Balance: "4,20 € Guthaben", gifted reviews as their own counter, bought vs. gifted credit and what expires when.
 * Credit is always "X € Guthaben"; real money (prices) only appears with the packages.
 */
@Composable
private fun Balance(account: FuelAccount, config: FuelConfig?, language: Language, t: FuelStrings) {
    val s = LocalStrings.current
    Spacer(Modifier.height(Space.m))
    Panel {
        PanelItem {
            Text(t.balanceLabel, style = Theme.type.caption, tone = Tone.Muted)
            Text(t.creditAmount(formatEuro(account.balanceMicro, language)), style = Theme.type.title)
            val reviews = account.giftReviews
            if (reviews.remaining > 0) {
                Spacer(Modifier.height(Space.xs))
                Text(t.giftReviews(reviews.remaining))
            }
            val gifted = account.giftedMicro ?: 0
            val bought = account.purchasedMicro
            if (bought != null && gifted > 0) {
                Spacer(Modifier.height(Space.xs))
                Text(t.balanceSplit(formatEuro(bought, language), formatEuro(gifted, language)), style = Theme.type.caption, tone = Tone.Muted)
            }
            account.expiring.sumOf { it.amountMicro }.takeIf { it > 0 }?.let { amount ->
                val until = account.expiring.minOf { it.expiresAt }
                Text(t.expiringLine(formatEuro(amount, language), s.date(epochToDate(until))), style = Theme.type.caption, tone = Tone.Muted)
            }
            reviews.expiring.forEach { item ->
                Text(t.giftReviewsExpiring(item.count, s.date(epochToDate(item.expiresAt))), style = Theme.type.caption, tone = Tone.Muted)
            }
            if (gifted > 0 || reviews.remaining > 0) {
                Text(t.giftedFirst, style = Theme.type.caption, tone = Tone.Muted)
            }
            if (reviews.remaining > 0) {
                Spacer(Modifier.height(Space.xs))
                Text(t.giftReviewsScope(giftModelName(config)), style = Theme.type.caption, tone = Tone.Muted)
            }
        }
    }
}

/** Real name of the model the gifted reviews cover ("Gemini 3.6 Flash"). */
internal fun giftModelName(config: FuelConfig?): String {
    val tier = config?.invite?.giftTier ?: app.overfit.ai.TIER_STANDARD
    return config?.tiers?.get(tier)?.displayName?.takeIf { it.isNotEmpty() }
        ?: app.overfit.fuel.modelOfTier(tier)?.let { app.overfit.fuel.modelShortName(it) }
        ?: "Gemini Flash"
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
        if (entries.isEmpty() && state is Loaded.Ready) Text(t.ledgerEmpty, tone = Tone.Muted, modifier = Modifier.padding(top = Space.m))
        if (entries.isNotEmpty()) Panel { entries.forEach { e ->
            val w = app.overfit.i18n.webCreditStringsFor(language)
            val title = when (e.type) {
                "usage" -> if (e.reviews != null && e.reviews < 0) t.ledgerGiftReviewUsage else t.ledgerUsage
                "test_credit" -> t.ledgerTestCredit
                "invite_expired" -> t.ledgerInviteExpired
                "purchase" -> if (e.source == "web") w.ledgerWebPurchase else t.ledgerPurchase
                "code_redeem" -> w.ledgerCode
                "refund" -> w.ledgerRefund
                "chargeback" -> w.ledgerChargeback
                "invite_bonus" -> t.ledgerInviteBonus
                "invite_reward" -> t.ledgerInviteReward
                else -> e.type
            }
            val sign = if (e.amountMicro > 0) "+" else ""
            // Euro amounts are credit ("… € Guthaben"); gifted reviews are a count of their own.
            val amount = when {
                e.reviews != null && e.amountMicro == 0L -> t.ledgerReviews(e.reviews)
                e.type == "usage" -> t.creditAmount(formatCost(e.amountMicro, language))
                e.reviews != null -> t.creditAmount(sign + formatEuro(e.amountMicro, language)) + " · " + t.ledgerReviews(e.reviews)
                else -> t.creditAmount(sign + formatEuro(e.amountMicro, language))
            }
            PanelItem {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text(title, Modifier.weight(1f).padding(end = Space.m))
                Text(amount)
            }
            // detail line under the row: paid/gifted/used/refunded, date and, for usage, tokens
            val detail = buildString {
                append(ledgerKindLabel(e.kind, t)).append(" · ")
                append(s.date(epochToDate(e.ts)))
                if (e.type == "usage") {
                    e.model?.let { append(" · ").append(app.overfit.fuel.modelLabel(it)) }
                    append(" · ").append(t.ledgerTokens(e.inputTokens + e.cacheReadTokens + e.cacheWriteTokens, e.outputTokens))
                }
            }
            val chosen = if (e.type == "usage" && e.fallback) e.tier?.let { app.overfit.fuel.modelOfTier(it) }?.let { app.overfit.fuel.modelShortName(it) } else null
            Text(detail, style = Theme.type.caption, tone = Tone.Muted, modifier = Modifier.padding(top = Space.xs))
            if (chosen != null) Text(t.ledgerFallback(chosen), style = Theme.type.caption, tone = Tone.Muted, modifier = Modifier.padding(top = Space.xs))
            }
        } }
        when (val st = state) {
            is Loaded.Loading -> Text(t.loading, tone = Tone.Muted, modifier = Modifier.padding(top = Space.m))
            is Loaded.Failed -> ErrorBlock(t) { load(next) }
            is Loaded.Ready -> if (next != null) SmallButton(t.loadMore, { load(next) }, tone = Tone.Default)
        }
    }
}

internal fun ledgerKindLabel(kind: app.overfit.fuel.LedgerKind, t: FuelStrings): String = when (kind) {
    app.overfit.fuel.LedgerKind.Paid -> t.ledgerKindPaid
    app.overfit.fuel.LedgerKind.Gifted -> t.ledgerKindGifted
    app.overfit.fuel.LedgerKind.Used -> t.ledgerKindUsed
    app.overfit.fuel.LedgerKind.Refunded -> t.ledgerKindRefunded
    app.overfit.fuel.LedgerKind.Expired -> t.ledgerKindExpired
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
            Panel {
                PanelItem {
                    Text(shown, style = Theme.type.title)
                    Spacer(Modifier.height(Space.s))
                    SmallButton(if (copied) t.recoveryCopied else t.recoveryCopy, {
                        clipboard.setText(AnnotatedString(shown)); copied = true
                    }, tone = Tone.Default)
                }
            }
            Spacer(Modifier.height(Space.s))
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
        SmallButton(t.restoreAction, {
            if (service == null || input.isBlank() || busy) return@SmallButton
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

/** 2800 → "28", 750 → "7,5" in a locale-neutral way (the string is built per language by the caller's text). */
private fun formatPercent(bp: Int): String = if (bp % 100 == 0) (bp / 100).toString() else (bp / 100.0).toString()
