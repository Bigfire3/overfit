package app.overfit.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.delay

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

    // Restarts on every page change (also after a tap), so a tap gives the new page its full time.
    LaunchedEffect(page, autoAdvance) {
        if (autoAdvance && page < last) {
            delay(STORY_AUTO_MS)
            page += 1
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .pointerInput(page) {
                // Tap right two thirds = next (past the last page = close), left third = back. Buttons take their own taps first.
                detectTapGestures { at ->
                    if (at.x < size.width / 3f) { if (page > 0) page -= 1 } else if (page < last) page += 1 else onClose()
                }
            }
            .semantics {
                onClick(label = s.next) { if (page < last) page += 1 else onClose(); true }
            }
            .pointerInput(Unit) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag > CLOSE_DRAG_PX) onClose() },
                    onVerticalDrag = { _, delta -> drag += delta },
                )
            }
            .testTag("story"),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Space.m)) {
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier.weight(1f).height(2.dp)
                            .background(if (i <= page) Theme.colors.foreground else Theme.colors.hairline),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.s), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.size(44.dp).testTag("story:close")
                        .semantics { contentDescription = s.close }
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icon.Close) }
            }

            val p = pages[page]
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LineIcon(storyIcons[page], size = 96.dp)
                Spacer(Modifier.height(Space.xl))
                Text(p.title, style = Theme.type.title, align = TextAlign.Center, modifier = Modifier.testTag("story:title").semantics { heading() })
                Spacer(Modifier.height(Space.m))
                Text(p.text, tone = Tone.Muted, align = TextAlign.Center)
                val target = storyTargets[page]
                if (target != null && p.action != null) {
                    Spacer(Modifier.height(Space.l))
                    SmallButton(p.action, { onTarget(target) }, Modifier.testTag("story:action"))
                }
            }
            Text(
                s.progress(page + 1, pages.size),
                Modifier.fillMaxWidth().padding(bottom = Space.m).semantics { liveRegion = LiveRegionMode.Polite },
                style = Theme.type.caption, tone = Tone.Muted, align = TextAlign.Center,
            )
        }
    }
}

private const val CLOSE_DRAG_PX = 120f
