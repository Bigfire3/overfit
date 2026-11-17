package app.overfit.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.overfit.i18n.LocalCoachStrings
import app.overfit.ui.theme.Motion
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.delay

/**
 * A prompt the user can copy, always with its text visible (Fabian: "you should see what you copy"). Collapsed it
 * shows the first lines with "More"; "Copy" turns into "Copied" for a moment (confirmation pattern). [text] null =
 * still being built. Used in the Coach tab and wherever else a prompt is copied (import, chat connections).
 */
@Composable
fun PromptView(
    title: String,
    text: String?,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    onUseInChat: (() -> Unit)? = null,
    collapsedLines: Int = 5,
    /** Text of the copy button after copying (default "Copied"). */
    copiedLabel: String? = null,
) {
    val h = LocalCoachStrings.current.hub
    var expanded by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    LaunchedEffect(copied) { if (copied) { delay(3000); copied = false } }
    Column(modifier.fillMaxWidth().padding(vertical = Space.m)) {
        Text(title, style = Theme.type.label)
        if (hint != null) Text(hint, style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        Column(
            Modifier
                .fillMaxWidth()
                .background(Theme.colors.surface, RoundedCornerShape(8.dp))
                // "More"/"Less" and loading → text: the grey area grows and shrinks instead of jumping.
                .animateContentSize(Motion.smooth())
                .padding(Space.m)
                .testTag("prompt:$title"),
        ) {
            // Loading → text fades over; a rebuilt prompt just changes.
            Swap(text, contentKey = { it == null }) { shown ->
                Text(
                    shown ?: h.promptLoading,
                    style = Theme.type.caption,
                    tone = if (shown == null) Tone.Muted else Tone.Default,
                    maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
                )
            }
            Reveal(text != null && text.lines().size > collapsedLines || (text?.length ?: 0) > collapsedLines * 60) {
                Column {
                    Spacer(Modifier.height(Space.xs))
                    Swap(expanded) { open ->
                        SmallButton(if (open) h.promptLess else h.promptMore, { expanded = !expanded })
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        Row {
            // "Copy" → "Copied" fades over and the width follows, so the button next to it slides instead of jumping.
            // Only the current label carries the tag (the old one may still be fading out).
            Swap(copied) { isCopied ->
                SmallButton(
                    if (isCopied) copiedLabel ?: h.promptCopied else h.promptCopy,
                    { if (text != null) { onCopy(text); copied = true; haptics.confirm() } },
                    if (isCopied == copied) Modifier.testTag("prompt-copy:$title") else Modifier,
                    tone = if (isCopied) Tone.Muted else Tone.Default,
                )
            }
            if (onUseInChat != null) {
                Spacer(Modifier.width(Space.l))
                SmallButton(h.promptUseInChat, onUseInChat, Modifier.testTag("prompt-chat:$title"))
            }
        }
    }
    Hairline()
}
