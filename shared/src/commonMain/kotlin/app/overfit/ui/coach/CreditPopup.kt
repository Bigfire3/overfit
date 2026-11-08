package app.overfit.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overfit.ai.ProviderId
import app.overfit.data.db.AiRequestDao
import app.overfit.data.db.AiRequestEntity
import app.overfit.domain.Language
import app.overfit.fuel.Distribution
import app.overfit.fuel.FuelConfig
import app.overfit.fuel.FuelSession
import app.overfit.fuel.MICRO_PER_EURO
import app.overfit.fuel.formatEuro
import app.overfit.fuel.modelShortName
import app.overfit.fuel.showsWebPurchase
import app.overfit.i18n.CoachSettingsStrings
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.RequestLogStrings
import app.overfit.i18n.coachSettingsStringsFor
import app.overfit.i18n.requestLogStringsFor
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.Text
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressScale
import app.overfit.ui.components.pressableRow
import app.overfit.ui.fuel.Loaded
import app.overfit.ui.fuel.formatRequestCost
import app.overfit.ui.fuel.purposeLabel
import app.overfit.ui.fuel.rememberLoaded
import app.overfit.ui.fuel.reservedNote
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Top-up amounts when the backend names no web packages (old backend). */
private val DEFAULT_TOP_UPS = listOf(5L, 10L, 20L, 50L).map { it * MICRO_PER_EURO }

/** Billing lines in the popup. */
private const val RECENT_REQUESTS = 5

/**
 * Credit chip in the chat header ("4,62 €"). Only with credit as the AI access (or while the demo runs, like the
 * credit row in the settings): with an own key it is not shown at all and asks the backend nothing.
 */
@Composable
fun CreditChip(session: FuelSession, fuelActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val demo by session.demo.collectAsStateWithLifecycle()
    val service = remember(demo) { session.current() }
    if (service == null || !(fuelActive || demo)) return
    val f = LocalFuelStrings.current
    val language = f.language
    val s = coachSettingsStringsFor(language)
    val (account, _) = rememberLoaded(service) { service.account() }
    val text = when (val a = account) {
        is Loaded.Ready -> formatEuro(a.value.balanceMicro, language)
        is Loaded.Failed -> "– €"
        Loaded.Loading -> "…"
    }
    // The chip stays short; the reservation is read out and shown in the popup.
    val reserved = (account as? Loaded.Ready)?.value?.let { reservedNote(it.reservedMicro, f) }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.colors.surface)
            .semantics { contentDescription = s.creditChip(text) + (reserved?.let { " $it" } ?: "") }
            .testTag("coach:credit")
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Theme.type.label)
    }
}

/**
 * Content of the credit popup (the chat puts it into a centred popup): balance, top up 5/10/20/50 € on the existing
 * purchase way (web purchase) and the last requests with their cost. When this build cannot buy (store build, demo,
 * old backend), it points to the credit page like the credit screen does. No referral programme.
 */
@Composable
fun CreditPopupContent(
    session: FuelSession,
    dao: AiRequestDao,
    onOpenFuel: () -> Unit,
    onAllRequests: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    distribution: Distribution = Distribution.Sideload,
) {
    val f = LocalFuelStrings.current
    val language = f.language
    val s = coachSettingsStringsFor(language)
    val demo by session.demo.collectAsStateWithLifecycle()
    val service = remember(demo) { session.current() }
    val (account, reload) = rememberLoaded(service) { service?.account() }
    val (config, _) = rememberLoaded(service) { service?.config() }
    // Back from the purchase page in the browser: show the new balance.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(service) {
        if (resumes++ > 0) reload()
        onPauseOrDispose { }
    }
    val recent by remember(dao) { dao.observeRecent(RECENT_REQUESTS) }.collectAsStateWithLifecycle(null)

    Column(modifier.fillMaxWidth().padding(Space.l)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(s.creditTitle, Modifier.weight(1f), style = Theme.type.label, tone = Tone.Muted)
            val closeInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier.size(40.dp).semantics { contentDescription = s.close }
                    .pressScale(closeInteraction, app.overfit.ui.components.PressScale.icon)
                    .clickable(closeInteraction, indication = null, role = Role.Button, onClick = onClose)
                    .testTag("credit:close"),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icon.Close, size = 20.dp, color = Theme.colors.muted) }
        }
        when (val a = account) {
            is Loaded.Ready -> {
                Text(a.value?.let { formatEuro(it.balanceMicro, language) } ?: "–", Modifier.testTag("credit:balance"), style = Theme.type.title)
                a.value?.let { reservedNote(it.reservedMicro, f) }?.let {
                    Text(it, Modifier.testTag("credit:reserved"), style = Theme.type.caption, tone = Tone.Muted)
                }
            }
            is Loaded.Failed -> Text(f.unreachable, tone = Tone.Muted)
            Loaded.Loading -> Text("…", style = Theme.type.title, tone = Tone.Muted)
        }
        Text(if (demo) "${s.neverExpires} · ${f.fuelRowDemo}" else s.neverExpires, style = Theme.type.caption, tone = Tone.Muted)

        Spacer(Modifier.height(Space.l))
        Text(s.topUp.uppercase(), style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        val cfg = (config as? Loaded.Ready<FuelConfig?>)?.value
        TopUp(service, cfg, distribution, demo, language, s, onOpenFuel)

        Spacer(Modifier.height(Space.l))
        Text(s.billing.uppercase(), style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.height(Space.xs))
        val r = requestLogStringsFor(language)
        val list = recent
        when {
            list == null -> Text(f.loading, Modifier.padding(vertical = Space.s), style = Theme.type.caption, tone = Tone.Muted)
            list.isEmpty() -> Text(s.billingEmpty, Modifier.padding(vertical = Space.s), style = Theme.type.caption, tone = Tone.Muted)
            else -> list.take(RECENT_REQUESTS).forEach { BillingLine(it, r, language) }
        }
        Row(
            Modifier.fillMaxWidth().pressableRow(onAllRequests).heightIn(min = 48.dp).testTag("credit:all-requests"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.allRequests, Modifier.weight(1f), style = Theme.type.label)
            LineIcon(Icon.ChevronRight, size = 20.dp, color = Theme.colors.muted)
        }
    }
}

/**
 * One "Top up" button that opens the web purchase page, with the amounts it offers as a hint; or the way to the credit
 * page. The purchase link from the backend cannot carry an amount (web/guthaben preselects its own package), so the app
 * shows no amount buttons that would all open the same page.
 */
@Composable
private fun TopUp(
    service: app.overfit.fuel.FuelService?,
    config: FuelConfig?,
    distribution: Distribution,
    demo: Boolean,
    language: Language,
    s: CoachSettingsStrings,
    onOpenFuel: () -> Unit,
) {
    val web = service != null && !demo && config != null && config.showsWebPurchase(distribution)
    if (!web) {
        Text(s.topUpElsewhere, style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        PopupPill(s.openCreditPage, Modifier.fillMaxWidth().testTag("credit:open-fuel"), onClick = onOpenFuel)
        return
    }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val amounts = config!!.webPurchase.packages.map { it.creditMicro }.ifEmpty { DEFAULT_TOP_UPS }
    PopupPill(s.topUpOpen, Modifier.fillMaxWidth().testTag("credit:topup")) {
        if (opening) return@PopupPill
        opening = true
        failed = false
        scope.launch {
            try {
                uriHandler.openUri(service!!.webPurchaseLink(if (language == Language.EN) "en" else "de").url)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            }
            opening = false
        }
    }
    fun euro(micro: Long) = formatEuro(micro, language).replace(",00", "").replace(".00", "")
    Text(
        s.topUpChooseAmount(euro(amounts.min()), euro(amounts.max())),
        Modifier.padding(top = Space.s), style = Theme.type.caption, tone = Tone.Muted,
    )
    if (failed) Text(app.overfit.i18n.webCreditStringsFor(language).webTopUpFailed, Modifier.padding(top = Space.xs), style = Theme.type.caption)
}

/** Button inside the popup (which sits on the surface colour): the nested surface fill, no border. */
@Composable
private fun PopupPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Theme.colors.surfacePressed)
            .padding(horizontal = Space.s),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Theme.type.label, maxLines = 1) }
}

/** "Chat · Claude Sonnet 5.5" left, cost right (same wording as the request log). */
@Composable
private fun BillingLine(e: AiRequestEntity, r: RequestLogStrings, language: Language) {
    val isFuel = e.providerId == ProviderId.FUEL.name
    val cost = when {
        !isFuel -> r.costOwnKey
        (e.costMicro ?: 0) == 0L -> r.costNone
        else -> formatRequestCost(e.costMicro!!, language)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = Space.xs).testTag("credit:request"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(purposeLabel(e.purpose, r), style = Theme.type.caption, maxLines = 1)
            Text(modelShortName(e.servedModel ?: e.requestedModel), style = Theme.type.caption, tone = Tone.Muted, maxLines = 1)
        }
        Spacer(Modifier.width(Space.m))
        Text(cost, style = Theme.type.caption, tone = Tone.Muted, maxLines = 1)
    }
}
