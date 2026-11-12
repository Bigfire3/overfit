package app.overfit.ui.coach

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.overfit.ui.components.FloatingCard
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
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

/**
 * Coach part of the finish flow (above "Save"): one card "Get this training reviewed · ≈ 3,1 ct". Nothing is sent
 * automatically (Fabian, 8.11.): a tap saves the finish flow and opens the coach chat with this training attached and
 * "Review my training" in the input; the user sends it there. The price is estimated from the real prompt.
 */
@Composable
fun CoachFinishSection(env: app.overfit.ui.coach.chat.CoachChatEnv, workoutId: String, onReview: () -> Unit) {
    val c = LocalCoachStrings.current
    val s = app.overfit.i18n.coachChatStringsFor(c.language)
    val pricing by env.pricing.collectAsState()
    var tokens by remember(workoutId) { mutableStateOf<app.overfit.coach.TokenEstimate?>(null) }
    LaunchedEffect(workoutId) {
        tokens = runCatching {
            env.chat.estimate(null, reviewRequest(workoutId, s.reviewMyTraining))
        }.getOrNull()
    }
    val price = app.overfit.ui.coach.chat.sendPriceLabel(pricing, tokens, s, c.language)
    SectionTitle(c.coachTitle)
    FloatingCard(Modifier.testTag("finish:review"), onClick = onReview) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Space.m, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
            LineIcon(Icon.Coach)
            Spacer(Modifier.width(Space.m))
            Text(s.reviewCard, Modifier.weight(1f))
            if (price != null) {
                Spacer(Modifier.width(Space.s))
                Text(price, style = Theme.type.caption, tone = Tone.Muted, maxLines = 1)
            }
        }
    }
    Hint(s.reviewCardHint)
}

/** What the review card attaches: this training, the profile, "Review my training" (sent only by the user). */
fun reviewRequest(workoutId: String, question: String, label: String = "", startedAt: Long? = null) =
    app.overfit.coach.ChatSendRequest(question, listOf(app.overfit.coach.ChatAttachment.Training(workoutId, label, startedAt)))

/** Coach memory (all versions), reviews and coach settings in the backup (no keys). */
class CoachBackup(private val coach: CoachService, private val prefs: app.overfit.coach.CoachChatPrefsStore? = null) : BackupContributor {
    override val key = "coach"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Content(
        val reviews: List<CoachReviewEntity> = emptyList(),
        val memory: List<CoachMemoryEntity> = emptyList(),
        val settings: CoachSettings = CoachSettings(),
        /** Coach profile, "Deine Ergänzung", automatic review (since the chat redesign; missing in older backups). */
        val chat: app.overfit.coach.CoachChatPrefs? = null,
    )

    override suspend fun export(): JsonElement? {
        val (reviews, memory) = coach.exportAll()
        return json.encodeToJsonElement(Content.serializer(), Content(reviews, memory, coach.settings.state.value, prefs?.state?.value))
    }

    override suspend fun import(value: JsonElement?) {
        val content = value?.let { runCatching { json.decodeFromJsonElement(Content.serializer(), it) }.getOrNull() } ?: Content()
        coach.importAll(content.reviews, content.memory)
        coach.settings.replace(content.settings)
        prefs?.let { p ->
            content.chat?.let(p::replace)
            // An older backup may bring a whole edited instruction: it becomes the addition, the base stays Overfit's.
            p.migrateLegacyInstructions(coach.settings)
        }
    }
}
