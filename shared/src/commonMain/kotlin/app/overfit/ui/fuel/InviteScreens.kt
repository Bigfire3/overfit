package app.overfit.ui.fuel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overfit.fuel.FuelException
import app.overfit.fuel.FuelSession
import app.overfit.fuel.RevealPhase
import app.overfit.fuel.RevealPlan
import app.overfit.fuel.InviteToken
import app.overfit.fuel.formatEuro
import app.overfit.fuel.formatRevealNumber
import app.overfit.fuel.parseInviteToken
import app.overfit.fuel.inviteLink
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.LocalStrings
import app.overfit.platform.HapticPattern
import app.overfit.platform.Haptics
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.BigInput
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.share.QrView
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import app.overfit.share.QrScanner
import app.overfit.ui.components.RowInput
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.components.ListRow
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** The code is re-fetched this long before it expires so a scan always meets a valid token (backend TTL 45 s → every 30 s). */
internal fun rotateAfterMs(ttlSec: Int): Long = (minOf(30, maxOf(ttlSec - 10, 5))) * 1_000L

/** "Freunde einladen": name + QR code with a rotating one-time token. */
@Composable
fun InviteShowScreen(session: FuelSession, onBack: () -> Unit) {
    val t = LocalFuelStrings.current
    val s = LocalStrings.current
    val service = session.current()
    val isDemo by session.demo.collectAsStateWithLifecycle()
    val (account, _) = rememberLoaded(service) { service?.account() }
    val (config, _) = rememberLoaded(service) { service?.config() }

    var name by remember { mutableStateOf<String?>(null) }
    val storedName = (account as? Loaded.Ready)?.value?.displayName.orEmpty()
    val shownName = name ?: storedName
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf<InviteToken?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var secondsLeft by remember { mutableLongStateOf(0) }
    LaunchedEffect(service) {
        if (service == null) return@LaunchedEffect
        while (true) {
            val fresh = try {
                service.inviteToken()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = inviteErrorText((e as? FuelException)?.code ?: "network", t)
                token = null
                delay(5_000)
                continue
            }
            error = null
            token = fresh
            val rotate = rotateAfterMs(fresh.ttlSec)
            val start = TimeSource.Monotonic.markNow()
            while (true) {
                val elapsed = start.elapsedNow().inWholeMilliseconds
                if (elapsed >= rotate) break
                progress = elapsed.toFloat() / rotate
                secondsLeft = (rotate - elapsed + 999) / 1_000
                delay(100)
            }
        }
    }

    val cfg = (config as? Loaded.Ready)?.value
    val starter = cfg?.invite?.starterReviews ?: 5
    Screen(scroll = true, header = { BackHeader(s.back, onBack) }) {
        Title(t.inviteShow)
        Spacer(Modifier.height(Space.m))
        if (isDemo) {
            Text(t.demoBanner, style = Theme.type.caption, tone = Tone.Muted)
            Spacer(Modifier.height(Space.m))
        }
        Text(t.inviteShowText(starter), tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        Text(t.inviteGiftNote(giftModelName(cfg), cfg?.invite?.expiryDays ?: 180), style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.height(Space.l))

        val current = token
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (current != null) {
                QrView(inviteLink(current.token), Modifier.size(240.dp))
                Spacer(Modifier.height(Space.m))
                HairlineProgress(1f - progress, Modifier.padding(horizontal = Space.xl))
                Spacer(Modifier.height(Space.s))
                Text(t.inviteRotates(secondsLeft.toInt()), style = Theme.type.caption, tone = Tone.Muted)
            } else {
                Text(error ?: t.loading, tone = Tone.Muted)
            }
        }

        SectionTitle(t.inviteName)
        ListRow(s.name) {
            RowInput(shownName, { name = it.take(24) }, onCommit = {
                val value = name ?: return@RowInput
                scope.launch { runCatching { service?.setDisplayName(value) } }
            })
        }
        Text(t.inviteNameHint, style = Theme.type.caption, tone = Tone.Muted)
        (account as? Loaded.Ready)?.value?.invite?.takeIf { it.monthlyLimit > 0 }?.let {
            Spacer(Modifier.height(Space.m))
            Text(t.inviteCount(it.invitedThisMonth, it.monthlyLimit), style = Theme.type.caption, tone = Tone.Muted)
        }
    }
}

/**
 * "Einladung einlösen": scan the QR code (when a scanner is available) or paste the link, then redeem.
 * [canScan]: the in-app QR scanner exists (Android); on iOS the camera app opens the link instead.
 */
@Composable
fun InviteRedeemScreen(
    session: FuelSession,
    onBack: () -> Unit,
    onRedeemed: (grantedReviews: Int, inviterName: String?) -> Unit,
    canScan: Boolean = false,
    initialCode: String = "",
) {
    val t = LocalFuelStrings.current
    val s = LocalStrings.current
    val service = session.current()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initialCode) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    fun redeem(raw: String) {
        val token = parseInviteToken(raw)
        if (token == null) { message = t.inviteNoCode; return }
        if (service == null || busy) return
        busy = true
        message = null
        scope.launch {
            try {
                val result = service.redeemInvite(token)
                onRedeemed(result.grantedReviews, result.inviter.displayName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = inviteErrorText((e as? FuelException)?.code ?: "network", t)
            }
            busy = false
        }
    }

    if (scanning) {
        Screen(header = { BackHeader(s.back, { scanning = false }) }) {
            Title(t.inviteScan)
            Spacer(Modifier.height(Space.l))
            Box(Modifier.fillMaxWidth().weight(1f).background(Theme.colors.hairline)) {
                QrScanner(
                    Modifier.fillMaxSize(),
                    onScanned = { code -> scanning = false; input = code; redeem(code) },
                    onPermissionDenied = { scanning = false; message = t.inviteCameraDenied },
                )
            }
            Spacer(Modifier.height(Space.m))
        }
        return
    }

    Screen(
        scroll = true,
        header = { BackHeader(s.back, onBack) },
        footer = { PrimaryButton(t.inviteRedeemAction, onClick = { redeem(input) }, enabled = input.isNotBlank(), loading = busy) },
    ) {
        Title(t.inviteRedeem)
        Spacer(Modifier.height(Space.m))
        if (session.demo.collectAsStateWithLifecycle().value) {
            Text(t.demoBanner, style = Theme.type.caption, tone = Tone.Muted)
            Spacer(Modifier.height(Space.m))
        }
        Text(t.inviteRedeemText, tone = Tone.Muted)
        Spacer(Modifier.height(Space.l))
        if (canScan) {
            SmallButton(t.inviteScan, { scanning = true }, tone = Tone.Default)
            Spacer(Modifier.height(Space.m))
        }
        SectionTitle(t.invitePaste)
        BigInput(input, { input = it.trim(); message = null }, t.invitePastePlaceholder, onDone = { redeem(input) })
        message?.let {
            Spacer(Modifier.height(Space.m))
            Text(it, style = Theme.type.caption)
        }
    }
}

private val RevealNumber = TextStyle(fontSize = 88.sp, lineHeight = 96.sp, letterSpacing = (-2).sp, fontFeatureSettings = "tnum")

/**
 * The invite reveal: the screen goes black, two heartbeat pulses, then the number of gifted coach reviews counts up from 0
 * in Inter with tabular digits, "Coach-Auswertungen geschenkt" and "Einladung von <Name>" below it, and a strong pulse at
 * the end. Deliberate exception to "no animations".
 */
@Composable
fun InviteRevealScreen(reviews: Int, inviterName: String?, haptics: Haptics, vibration: Boolean, onClose: () -> Unit) {
    val t = LocalFuelStrings.current
    val plan = remember(reviews) { RevealPlan(reviews.toLong()) }
    var elapsed by remember { mutableLongStateOf(0) }
    LaunchedEffect(plan) {
        val start = TimeSource.Monotonic.markNow()
        if (vibration) haptics.play(HapticPattern.Reveal)
        var finished = false
        while (!finished) {
            elapsed = start.elapsedNow().inWholeMilliseconds
            if (plan.phaseAt(elapsed) == RevealPhase.Done) {
                elapsed = plan.totalMs
                if (vibration) haptics.play(HapticPattern.RevealFinish)
                finished = true
            } else {
                androidx.compose.runtime.withFrameMillis { }
            }
        }
    }
    val phase = plan.phaseAt(elapsed)
    val white = Color.White
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(MutableInteractionSource(), indication = null) { if (phase == RevealPhase.Done) onClose() },
        contentAlignment = Alignment.Center,
    ) {
        if (phase != RevealPhase.Black) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.m)) {
                BasicText(
                    plan.valueAt(elapsed).toString(),
                    style = Theme.type.hero.merge(RevealNumber).copy(color = white, textAlign = TextAlign.Center),
                )
                BasicText(t.revealReviews(reviews), style = Theme.type.body.copy(color = white))
                BasicText(t.revealFrom(inviterName), style = Theme.type.body.copy(color = white))
            }
        }
        if (phase == RevealPhase.Done) {
            BasicText(
                t.revealClose,
                Modifier.align(Alignment.BottomCenter).padding(bottom = Space.xxl),
                style = Theme.type.caption.copy(color = Color(0xFF8A8A8A)),
            )
        }
    }
}
