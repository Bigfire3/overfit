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
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Text
import app.overfit.ui.components.TextButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.share.PreviewScreen
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
    onTemplate: (Long) -> Unit,
) {
    val t = LocalStrings.current
    val c = LocalChatStrings.current
    val inbox by chat.inbox.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<InboxTemplate?>(null) }
    LaunchedEffect(Unit) { chat.inbox.refresh() }

    val current = open
    if (current != null) {
        TemplateProposalScreen(chat, current, unit, language, onBack = { open = null }, onDone = { id ->
            open = null
            id?.let(onTemplate)
        })
        return
    }
    Screen(scroll = true, header = { BackHeader(t.back, onBack) }) {
        Title(c.inboxTitle)
        Spacer(Modifier.height(Space.m))
        if (inbox.full) Text(c.inboxFull, Modifier.padding(bottom = Space.m), style = Theme.type.caption)
        if (inbox.items.isEmpty()) Text(c.inboxEmpty, tone = Tone.Muted)
        Hairline()
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

private fun templateTitle(item: InboxTemplate): String =
    Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(item.templateJson)?.groupValues?.get(1).orEmpty()

@Composable
private fun NoteCard(chat: ChatFeature, item: InboxNote) {
    val c = LocalChatStrings.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
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
            TextButton(c.inboxAccept, tone = Tone.Default, onClick = { scope.launch { chat.inbox.acceptNote(item) } })
            TextButton(c.inboxDiscard, onClick = { scope.launch { chat.inbox.discard(item) } })
        }
    }
    Hairline()
}

/** The chat's template proposal: the normal import preview, plus the chat's message and, when it replaces one, the diff. */
@Composable
private fun TemplateProposalScreen(
    chat: ChatFeature,
    item: InboxTemplate,
    unit: WeightUnit,
    language: Language,
    onBack: () -> Unit,
    onDone: (Long?) -> Unit,
) {
    val c = LocalChatStrings.current
    val t = LocalStrings.current
    var proposal by remember(item.id) { mutableStateOf<TemplateProposal?>(null) }
    var invalid by remember(item.id) { mutableStateOf(false) }
    var busy by remember(item.id) { mutableStateOf(false) }
    // App scope: saving must not be cancelled when this screen leaves the composition.
    val scope = chat.scope
    LaunchedEffect(item.id) {
        val p = chat.inbox.proposal(item)
        if (p == null) invalid = true else proposal = p
    }
    val p = proposal
    if (p == null) {
        Screen(header = { BackHeader(t.back, onBack) }) {
            if (invalid) {
                Text(c.inboxInvalid, tone = Tone.Muted)
                Spacer(Modifier.height(Space.m))
                TextButton(c.inboxDiscard, onClick = { scope.launch { chat.inbox.discard(item); onDone(null) } })
            }
        }
        return
    }
    val replaces = p.replaces
    fun accept(replace: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val id = chat.inbox.acceptTemplate(item, p, replace)
            onDone(id)
        }
    }
    PreviewScreen(
        preview = p.preview,
        unit = unit,
        language = language,
        busy = busy,
        onBack = onBack,
        acceptLabel = if (replaces != null) c.inboxUpdate else null,
        intro = {
            Text(c.inboxFrom(item.clientName.ifBlank { "Chat" }), style = Theme.type.caption, tone = Tone.Muted)
            item.message?.let { Spacer(Modifier.height(Space.s)); Text(it) }
            if (replaces != null) {
                Spacer(Modifier.height(Space.m))
                Text(c.diffTitle, style = Theme.type.label)
                val lines = TemplateDiff.lines(replaces, p.preview, c, unit, language)
                if (lines.isEmpty()) Text(c.diffNone, tone = Tone.Muted)
                lines.forEach { Text(it, style = Theme.type.caption) }
            }
        },
        footerExtra = {
            if (replaces != null) TextButton(c.inboxAsNew, tone = Tone.Default, onClick = { accept(false) })
            TextButton(c.inboxDiscard, onClick = { scope.launch { chat.inbox.discard(item); onDone(null) } })
        },
        onAccept = { accept(replaces != null) },
    )
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
        Hairline()
        notes.forEach { note -> SavedNote(note) { chat.notes.remove(note.id) } }
    }
}

@Composable
private fun SavedNote(note: ChatNote, onDelete: () -> Unit) {
    val c = LocalChatStrings.current
    Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
        Text(note.title ?: c.notesTitle, style = Theme.type.label)
        Text(
            listOfNotNull(note.clientName.takeIf { it.isNotBlank() }?.let(c.inboxFrom), note.rating?.let(c.notesRating)).joinToString(" · "),
            style = Theme.type.caption,
            tone = Tone.Muted,
        )
        Spacer(Modifier.height(Space.s))
        NoteBody(note.text)
        TextButton(c.notesDelete, onClick = onDelete, modifier = Modifier.padding(top = Space.s))
    }
    Hairline()
}
