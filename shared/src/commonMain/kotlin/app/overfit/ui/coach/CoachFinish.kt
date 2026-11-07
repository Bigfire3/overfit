package app.overfit.ui.coach

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.overfit.coach.CoachAccess
import app.overfit.coach.CoachService
import app.overfit.coach.CoachSettings
import app.overfit.data.backup.BackupContributor
import app.overfit.data.db.CoachMemoryEntity
import app.overfit.data.db.CoachReviewEntity
import app.overfit.i18n.LocalCoachStrings
import app.overfit.ui.components.Hint
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.PanelSection
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Segmented
import app.overfit.ui.components.Text
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** "Send to coach" / "Later" of the current finish flow; read when the workout is saved. */
class CoachFinishChoice {
    var send by mutableStateOf(true)
}

/**
 * Coach part of the finish flow (above "Save"). With the automatic review on, "Send" opens a new chat with this training
 * attached and the question "Review my training" (sent right away); "Later" skips it this once.
 */
@Composable
fun CoachFinishSection(coach: CoachService, prefs: app.overfit.coach.CoachChatPrefsStore, choice: CoachFinishChoice, workoutId: String) {
    val c = LocalCoachStrings.current
    val auto by prefs.state.collectAsState()
    LaunchedEffect(workoutId) { choice.send = true }
    if (!auto.autoReview) {
        SectionTitle(c.coachTitle)
        Text(app.overfit.i18n.coachChatStringsFor(c.language).finishAutoOff, style = Theme.type.caption, tone = Tone.Muted)
    } else if (coach.access() is CoachAccess.Ready) {
        PanelSection(c.coachTitle) {
            ListRow(c.sendToCoach) {
                Segmented(choice.send, listOf(true to c.finishSend, false to c.finishLater)) { choice.send = it }
            }
        }
        Hint(c.finishHint)
    } else {
        SectionTitle(c.coachTitle)
        Text(c.finishNoAccess, style = Theme.type.caption, tone = Tone.Muted)
    }
}

/** Coach memory (all versions), reviews and coach settings in the backup (no keys). */
class CoachBackup(private val coach: CoachService) : BackupContributor {
    override val key = "coach"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Content(
        val reviews: List<CoachReviewEntity> = emptyList(),
        val memory: List<CoachMemoryEntity> = emptyList(),
        val settings: CoachSettings = CoachSettings(),
    )

    override suspend fun export(): JsonElement? {
        val (reviews, memory) = coach.exportAll()
        return json.encodeToJsonElement(Content.serializer(), Content(reviews, memory, coach.settings.state.value))
    }

    override suspend fun import(value: JsonElement?) {
        val content = value?.let { runCatching { json.decodeFromJsonElement(Content.serializer(), it) }.getOrNull() } ?: Content()
        coach.importAll(content.reviews, content.memory)
        coach.settings.replace(content.settings)
    }
}
