package app.overfit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.overfit.i18n.LocalStrings
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme

/** Test tags of the buttons in [ConfirmDialog]. */
object DialogTags {
    const val confirm = "dialog:confirm"
    const val cancel = "dialog:cancel"
}

/**
 * One popup for the whole app: a centred, rounded area on a dimmed background. Tapping beside it or Back
 * closes it ([onDismiss]). Content is free; for yes/no questions use [ConfirmDialog].
 */
@Composable
fun OverfitDialog(onDismiss: () -> Unit, title: String, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)) {
        DialogCard(title, content)
    }
}

/** The visible part of [OverfitDialog]: rounded area with title and content (separate for screenshots). */
@Composable
fun DialogCard(title: String, content: @Composable () -> Unit) {
    val c = Theme.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.background)
            .border(1.dp, c.hairline, shape)
            .semantics { paneTitle = title }
            .padding(Space.l),
    ) {
        Text(title, Modifier.semantics { heading() }, style = Theme.type.label)
        content()
    }
}

/**
 * Yes/no question as a popup ([OverfitDialog]): [title] is the question, [message] an optional short
 * explanation, [confirmLabel] the action ("Löschen", "Beenden"); "Abbrechen" or tapping beside it closes.
 * [kind] Destructive = red action, Positive = black primary action.
 */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    kind: Confirm = Confirm.Destructive,
) {
    OverfitDialog(onDismiss, title) { ConfirmDialogContent(confirmLabel, onConfirm, onDismiss, message, kind) }
}

/** Message and the two buttons of [ConfirmDialog]. */
@Composable
fun ConfirmDialogContent(confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, message: String? = null, kind: Confirm = Confirm.Destructive) {
    val t = LocalStrings.current
    if (message != null) {
        Spacer(Modifier.height(Space.s))
        Text(message, style = Theme.type.caption.copy(lineHeight = Theme.type.body.lineHeight), tone = Tone.Muted)
    }
    Spacer(Modifier.height(Space.l))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        SecondaryButton(t.cancel, onDismiss, Modifier.weight(1f).testTag(DialogTags.cancel), compact = true)
        val confirm = Modifier.weight(1f).testTag(DialogTags.confirm)
        val run = { onDismiss(); onConfirm() }
        if (kind == Confirm.Destructive) DestructiveButton(confirmLabel, run, confirm, compact = true)
        else PrimaryButton(confirmLabel, run, modifier = confirm, compact = true)
    }
}
