package app.overfit.ui.components

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
    LaunchedEffect(copied) { if (copied) { delay(3000); copied = false } }
    Column(modifier.fillMaxWidth().padding(vertical = Space.m)) {
        Text(title, style = Theme.type.label)
        if (hint != null) Text(hint, style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        Column(
            Modifier
                .fillMaxWidth()
                .background(Theme.colors.surface, RoundedCornerShape(8.dp))
                .padding(Space.m)
                .testTag("prompt:$title"),
        ) {
            Text(
                text ?: h.promptLoading,
                style = Theme.type.caption,
                tone = if (text == null) Tone.Muted else Tone.Default,
                maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            )
            if (text != null && text.lines().size > collapsedLines || (text?.length ?: 0) > collapsedLines * 60) {
                Spacer(Modifier.height(Space.xs))
                TextButton(if (expanded) h.promptLess else h.promptMore, { expanded = !expanded })
            }
        }
        Spacer(Modifier.height(Space.s))
        Row {
            TextButton(
                if (copied) copiedLabel ?: h.promptCopied else h.promptCopy,
                { if (text != null) { onCopy(text); copied = true } },
                tone = if (copied) Tone.Muted else Tone.Default,
            )
            if (onUseInChat != null) {
                Spacer(Modifier.width(Space.l))
                TextButton(h.promptUseInChat, onUseInChat)
            }
        }
    }
    Hairline()
}
