package app.overfit.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overfit.chat.ChatFeature
import app.overfit.chat.ChatNote
import app.overfit.chat.InboxItem
import app.overfit.chat.InboxNote
import app.overfit.chat.InboxTemplate
import app.overfit.chat.TemplateDiff
import app.overfit.chat.TemplateProposal
import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import app.overfit.i18n.LocalChatStrings
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.Panel
import app.overfit.ui.components.PanelItem
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Text
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.templates.EditorSeed
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.launch

/** Markdown note as plain lines. */
@Composable
internal fun NoteBody(text: String) {
    MarkdownLite.lines(text).forEach { line ->
        when {
            line.text.isEmpty() -> Spacer(Modifier.height(Space.s))
            line.heading -> Text(line.text, Modifier.padding(top = Space.s), style = Theme.type.label)
            line.bullet -> Text("• ${line.text}", Modifier.padding(start = Space.s))
            else -> Text(line.text)
        }
    }
}

/** What the chat left for the user: template proposals (with the import preview) and notes. */
@Composable
fun ChatInboxScreen(
    chat: ChatFeature,
    unit: WeightUnit,
    language: Language,
    onBack: () -> Unit,
    onEdit: (EditorSeed) -> Unit,
) {
    val t = LocalStrings.current
    val c = LocalChatStrings.current
    val inbox by chat.inbox.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<InboxTemplate?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { chat.inbox.refresh() }

    val current = open
    if (current != null) {
        TemplateProposalLoader(chat, current, unit, language, onBack = { open = null }, onEdit = { seed -> open = null; onEdit(seed) })
        return
    }
    Screen(scroll = true, header = { BackHeader(t.back, onBack) }) {
        Title(c.inboxTitle)
        Spacer(Modifier.height(Space.m))
        if (inbox.full) Text(c.inboxFull, Modifier.padding(bottom = Space.m), style = Theme.type.caption)
        // Loading and failure are their own states: "Nothing new" only once the server really said so.
        if (inbox.items.isEmpty()) when {
            inbox.loading -> Text(c.inboxLoading, tone = Tone.Muted)
            inbox.failed -> {
                Text(c.inboxFailed, tone = Tone.Muted)
                Spacer(Modifier.height(Space.m))
                SmallButton(c.inboxRetry, { scope.launch { chat.inbox.refresh() } })
            }
            else -> Text(c.inboxEmpty, tone = Tone.Muted)
        }
        if (inbox.items.isNotEmpty()) Panel {
            inbox.items.forEach { item ->
                when (item) {
                    is InboxTemplate -> ListRow(
                        item.clientName.takeIf { it.isNotBlank() }?.let(c.inboxFrom) ?: c.inboxTitle,
                        onClick = { open = item },
                    ) { Text(templateTitle(item), style = Theme.type.caption, tone = Tone.Muted, maxLines = 1) }
                    is InboxNote -> NoteCard(chat, item)
                }
            }
        }
    }
}

private fun templateTitle(item: InboxTemplate): String =
    Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(item.templateJson)?.groupValues?.get(1).orEmpty()

@Composable
private fun NoteCard(chat: ChatFeature, item: InboxNote) {
    val c = LocalChatStrings.current
    val scope = rememberCoroutineScope()
    PanelItem {
        Text(item.title ?: c.noteForWorkout.takeIf { item.workoutId != null } ?: c.notesTitle, style = Theme.type.label)
        Text(
            listOfNotNull(item.clientName.takeIf { it.isNotBlank() }?.let(c.inboxFrom), item.rating?.let(c.notesRating)).joinToString(" · "),
            style = Theme.type.caption,
            tone = Tone.Muted,
        )
        Spacer(Modifier.height(Space.s))
        NoteBody(item.text)
        Spacer(Modifier.height(Space.s))
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.l)) {
            SmallButton(c.inboxAccept, tone = Tone.Default, onClick = { scope.launch { chat.inbox.acceptNote(item) } })
            SmallButton(c.inboxDiscard, onClick = { scope.launch { chat.inbox.discard(item) } })
        }
    }
}

/** The chat's template proposal opens in the normal workout editor (message and diff as hints, "Discard" at the top). */
@Composable
private fun TemplateProposalLoader(
    chat: ChatFeature,
    item: InboxTemplate,
    unit: WeightUnit,
    language: Language,
    onBack: () -> Unit,
    onEdit: (EditorSeed) -> Unit,
) {
    val c = LocalChatStrings.current
    val t = LocalStrings.current
    var invalid by remember(item.id) { mutableStateOf(false) }
    // App scope: acknowledging must not be cancelled when this screen leaves the composition.
    val scope = chat.scope
    LaunchedEffect(item.id) {
        val p = chat.inbox.proposal(item)
        if (p == null) { invalid = true; return@LaunchedEffect }
        val replaces = p.replaces
        val hints = buildList {
            add(c.inboxFrom(item.clientName.ifBlank { "Chat" }))
            item.message?.let(::add)
            if (replaces != null) {
                add(c.diffTitle)
                val lines = TemplateDiff.lines(replaces, p.preview, c, unit, language)
                if (lines.isEmpty()) add(c.diffNone) else addAll(lines)
            }
        }
        onEdit(EditorSeed(
            preview = p.preview,
            hints = hints,
            replaceId = replaces?.id,
            discardLabel = c.inboxDiscard,
            onDiscard = { chat.inbox.discard(item) },
            onSaved = { chat.inbox.discard(item) },
        ))
    }
    Screen(header = { BackHeader(t.back, onBack) }) {
        if (invalid) {
            Text(c.inboxInvalid, tone = Tone.Muted)
            Spacer(Modifier.height(Space.m))
            SmallButton(c.inboxDiscard, onClick = { scope.launch { chat.inbox.discard(item); onBack() } })
        }
    }
}

/** Accepted notes without a workout (or all of them): "Notes from the chat". */
@Composable
fun ChatNotesScreen(chat: ChatFeature, onBack: () -> Unit) {
    val t = LocalStrings.current
    val c = LocalChatStrings.current
    val notes by chat.notes.notes.collectAsStateWithLifecycle()
    Screen(scroll = true, header = { BackHeader(t.back, onBack) }) {
        Title(c.notesTitle)
        Spacer(Modifier.height(Space.m))
        if (notes.isEmpty()) Text(c.notesEmpty, tone = Tone.Muted)
        if (notes.isNotEmpty()) Panel { notes.forEach { note -> SavedNote(note) { chat.notes.remove(note.id) } } }
    }
}

@Composable
private fun SavedNote(note: ChatNote, onDelete: () -> Unit) {
    val c = LocalChatStrings.current
    PanelItem {
        Text(note.title ?: c.notesTitle, style = Theme.type.label)
        Text(
            listOfNotNull(note.clientName.takeIf { it.isNotBlank() }?.let(c.inboxFrom), note.rating?.let(c.notesRating)).joinToString(" · "),
            style = Theme.type.caption,
            tone = Tone.Muted,
        )
        Spacer(Modifier.height(Space.s))
        NoteBody(note.text)
        SmallButton(c.notesDelete, onClick = onDelete, modifier = Modifier.padding(top = Space.s))
    }
}
