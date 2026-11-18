package app.overfit.ui.coach

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import app.overfit.ui.components.PressScale
import app.overfit.ui.components.Swap
import app.overfit.ui.components.pressScale
import app.overfit.ui.theme.Motion
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.overfit.i18n.LocalCoachStrings
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Text
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme

/** Where a story page can lead: the matching place of the app (null = no shortcut on that page). */
enum class StoryTarget { OwnKey, Credit, ChatConnections }

/** Pages of the story in order; the first has no shortcut. */
private val storyIcons = listOf(Icon.Sparkle, Icon.Key, Icon.Wallet, Icon.Link)
private val storyTargets = listOf(null, StoryTarget.OwnKey, StoryTarget.Credit, StoryTarget.ChatConnections)

const val STORY_PAGES = 4
const val STORY_AUTO_MS = 6_000L

/**
 * Full-screen story like Instagram: [STORY_PAGES] hairline progress bars on top, tap right = next, tap left = back,
 * swipe down or X = close. Page 1 explains what the AI can evaluate, pages 2–4 the ways to use it (own key, credit,
 * own subscription), each with an optional shortcut button. Moves on by itself after [STORY_AUTO_MS] when [autoAdvance].
 */
@Composable
fun CoachStoryScreen(
    onClose: () -> Unit,
    onTarget: (StoryTarget) -> Unit,
    autoAdvance: Boolean = true,
) {
    val s = LocalCoachStrings.current.hub.story
    val pages = listOf(s.whatItDoes, s.ownKey, s.credit, s.subscription)
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = pages.lastIndex

    // Fill of the current page's bar, 0..1 over STORY_AUTO_MS. Restarts on every page change (also after a tap), so a
    // tap gives the new page its full time. Without auto advance the bar stays full.
    // Keyed by page: a fresh bar starts empty in the very first frame of the new page (no flash of a full bar).
    val progress = remember(page, autoAdvance) { Animatable(if (autoAdvance) 0f else 1f) }
    LaunchedEffect(page, autoAdvance) {
        if (autoAdvance) {
            progress.animateTo(1f, tween(STORY_AUTO_MS.toInt(), easing = LinearEasing))
            // The last page runs through too, then the story closes like with the X.
            if (page < last) page += 1 else onClose()
        }
    }

    val close by rememberUpdatedState(onClose)
    // The middle column scrolls; pulling down only closes while it stands at its start (else the pull scrolls it back).
    // One per page, so a page that fades out keeps its position.
    val textScrolls = remember { List(pages.size) { androidx.compose.foundation.ScrollState(0) } }
    // How far the content follows the finger while pulling down: written straight from the gesture (no coroutine per
    // pointer event); let go before the threshold, the rest springs back on its own animation.
    var fingerPull by remember { mutableFloatStateOf(0f) }
    val springBack = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .pointerInput(Unit) {
                // Tap right two thirds = next (past the last page = close), left third = back. Buttons take their own taps first.
                // Keyed by nothing: [page] is read when the tap lands, so taps during a page fade keep counting.
                detectTapGestures { at ->
                    if (at.x < size.width / 3f) { if (page > 0) page -= 1 } else if (page < last) page += 1 else close()
                }
            }
            .semantics {
                onClick(label = s.next) { if (page < last) page += 1 else onClose(); true }
                customActions = listOf(CustomAccessibilityAction(s.back) { if (page > 0) page -= 1; true })
            }
            .pointerInput(Unit) {
                // Swipe down closes. Watched in the initial pass without consuming, so it works from anywhere (also over the
                // scrollable middle column) and taps/scrolls/buttons are untouched.
                // Only movement while the text column stands at its start counts; closing itself stays instant.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val threshold = CLOSE_DRAG.toPx()
                    var total = 0f
                    var closed = false
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (textScrolls[page].value == 0) total = (total + change.positionChange().y).coerceAtLeast(0f) else total = 0f
                        if (!closed) fingerPull = total
                        if (!closed && total > threshold) { closed = true; close() }
                    }
                    if (!closed) {
                        // Hand the offset over to the spring in one step (the sum stays the same), then let it go back.
                        val from = fingerPull
                        scope.launch {
                            springBack.snapTo(springBack.value + from)
                            fingerPull = 0f
                            springBack.animateTo(0f, Motion.smooth())
                        }
                    }
                }
            }
            .testTag("story"),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Follows the finger down, a little smaller the further it goes (read here, so only this layer redraws).
                    val pulled = fingerPull + springBack.value
                    translationY = pulled
                    val shrink = 1f - (pulled / CLOSE_DRAG.toPx()).coerceIn(0f, 1f) * PULL_SHRINK
                    scaleX = shrink; scaleY = shrink
                }
                .statusBarsPadding().navigationBarsPadding().padding(horizontal = Space.m)) {
            Spacer(Modifier.height(Space.m))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                // Screen readers still hear "page N of 4" (no visible text any more).
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = s.progress(page + 1, pages.size); liveRegion = LiveRegionMode.Polite },
            ) {
                repeat(pages.size) { i ->
                    // Past bars full, coming ones empty, the current one runs from left to right.
                    StoryBar(i, Modifier.weight(1f)) { if (i < page) 1f else if (i == page) progress.value else 0f }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.s), horizontalArrangement = Arrangement.End) {
                val closeInteraction = remember { MutableInteractionSource() }
                Box(
                    Modifier.size(app.overfit.ui.theme.TouchTarget.min).testTag("story:close")
                        .semantics { contentDescription = s.close }
                        .pressScale(closeInteraction, PressScale.icon)
                        .clickable(closeInteraction, indication = null, role = Role.Button) { onClose() },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icon.Close) }
            }

            // Page change: a short fade-through in place (the content is centred, so no size to follow). Only the current
            // page carries the test tags, so a page that is still fading out is never found twice.
            Swap(page, Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { shown ->
                val p = pages[shown]
                val current = shown == page
                Column(
                    Modifier.fillMaxSize().verticalScroll(textScrolls[shown]),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LineIcon(storyIcons[shown], size = app.overfit.ui.theme.IconSize.hero)
                    Spacer(Modifier.height(Space.xl))
                    Text(
                        p.title, style = Theme.type.title, align = TextAlign.Center,
                        modifier = (if (current) Modifier.testTag("story:title") else Modifier).semantics { heading() },
                    )
                    Spacer(Modifier.height(Space.m))
                    Text(p.text, tone = Tone.Muted, align = TextAlign.Center)
                    val target = storyTargets[shown]
                    if (target != null && p.action != null) {
                        Spacer(Modifier.height(Space.l))
                        SmallButton(p.action, { onTarget(target) }, if (current) Modifier.testTag("story:action") else Modifier)
                    }
                }
            }
            Spacer(Modifier.height(Space.m))
        }
    }
}

/** One progress bar of the story: [fill] 0..1 is read here only, so just the bars redraw while it runs. */
@Composable
private fun StoryBar(index: Int, modifier: Modifier, fill: () -> Float) {
    Box(modifier.height(3.dp).background(Theme.colors.hairline).testTag("story:bar:$index")) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fill().coerceIn(0f, 1f)).background(Theme.colors.foreground).testTag("story:fill:$index"))
    }
}

/** Pull down this far (with the text column at its start) and the story closes. */
private val CLOSE_DRAG = 96.dp

/** How much smaller the content gets at the close threshold while it follows the finger. */
private const val PULL_SHRINK = 0.06f
