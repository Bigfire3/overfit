package app.overfit.ui.coach.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.overfit.coach.CoachChatService
import app.overfit.data.db.CoachChatEntity
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.FloatingCard
import app.overfit.ui.components.HeaderDeleteButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Earlier chats: one card per chat (title, date); tap opens it, the bin deletes it with two taps. */
@Composable
fun CoachHistoryScreen(chat: CoachChatService, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val s = chatStrings()
    val t = LocalStrings.current
    val chats by chat.chats.collectAsState(initial = null)
    Screen(scroll = true, header = { BackHeader(t.back, onBack) }) {
        Title(s.historyTitle)
        Spacer(Modifier.height(Space.l))
        val list = chats
        when {
            list == null -> Unit
            list.isEmpty() -> Text(s.historyEmpty, tone = Tone.Muted)
            else -> Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                list.forEach { c -> ChatCard(c, s.deleteChat, onOpen = { onOpen(c.id) }, onDelete = { chat.delete(c.id) }) }
            }
        }
    }
}

@Composable
private fun ChatCard(c: CoachChatEntity, deleteLabel: String, onOpen: () -> Unit, onDelete: () -> Unit) {
    val t = LocalStrings.current
    val date = t.date(Instant.fromEpochMilliseconds(c.updatedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date)
    FloatingCard(Modifier.testTag("history:item"), onClick = onOpen) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = Space.m, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = Space.m)) {
                Text(c.title, maxLines = 2)
                Text(date, style = Theme.type.caption, tone = Tone.Muted)
            }
            // Own semantics node: otherwise the tag would merge into the card.
            Box(Modifier.semantics(mergeDescendants = true) {}.testTag("history:delete")) { HeaderDeleteButton(deleteLabel, onDelete, color = Theme.colors.muted) }
        }
    }
}
