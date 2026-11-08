package app.overfit.coach

import app.overfit.ai.ProviderId
import app.overfit.data.InMemoryKeyValueStore
import app.overfit.data.WorkoutRecord
import app.overfit.data.db.CoachChatDao
import app.overfit.data.db.CoachChatEntity
import app.overfit.data.db.CoachChatMessageEntity
import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSource
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import app.overfit.i18n.chatStringsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Chat redesign: attachments as prompt text, profile on/off per chat, the user's addition in every system prompt. */
class CoachChatContextTest {
    private val bench = Exercise("wg:bench-press", "Bench Press", ExerciseSource.WORKOUT_GUIDE, null, emptyList(), emptyList(), null, TrackingType.WEIGHT_REPS, 120)
    private val push = Template(7, "Push", null, listOf(TemplateExercise(bench, 120, null, List(3) { PlannedSet(60.0, 8) })))
    private val day = 24L * 60 * 60 * 1000
    private val now = 40 * day

    private fun training(id: String, at: Long, kg: Double): WorkoutRecord {
        var w = Workout.fromTemplate(push.copy(exercises = listOf(push.exercises[0].copy(sets = List(2) { PlannedSet(kg, 8) }))), at, id = id)
        w.exercises.forEach { e -> e.sets.forEach { w = w.completeSet(it.id, at + 1000) } }
        return WorkoutRecord(w, endedAt = at + 3_600_000, feeling = null, updatedAt = at)
    }

    private val sources = AttachmentSources(
        workouts = listOf(training("w1", now - 2 * day, 60.0), training("w2", now - 9 * day, 57.5), training("old", now - 60 * day, 40.0)),
        templates = listOf(push),
        unit = WeightUnit.KG,
        language = Language.DE,
        now = now,
        zone = TimeZone.UTC,
    )
    private val t = chatStringsFor(Language.DE)

    @Test
    fun trainingAttachmentListsItsSets() {
        val text = ChatAttachmentText.build(listOf(ChatAttachment.Training("w1", "Push · heute")), sources, t)
        assertTrue(text.startsWith("## Attached training"), text)
        assertTrue(text.contains("Bench Press: 60×8, 60×8"), text)
    }

    @Test
    fun deletedTrainingSaysSoInsteadOfFailing() {
        val text = ChatAttachmentText.build(listOf(ChatAttachment.Training("gone", "Legs · gestern")), sources, t)
        assertTrue(text.contains("Legs · gestern: no longer in the calendar"), text)
    }

    @Test
    fun planAttachmentHasExercisesSetsAndRest() {
        val text = ChatAttachmentText.build(listOf(ChatAttachment.Plan(7, "Push")), sources, t)
        assertTrue(text.contains("## Attached workout (plan): Push"), text)
        assertTrue(text.contains("- Bench Press [wg:bench-press]: 3 sets (60×8, 60×8, 60×8), rest 120 s"), text)
    }

    @Test
    fun historyCoversOnlyTheLastFourWeeks() {
        val text = ChatAttachmentText.build(listOf(ChatAttachment.History(4)), sources, t)
        assertTrue(text.contains("2 trainings, 0.5 per week, 4 sets, 120 min in total"), text)
        assertFalse(text.contains("40 kg"), text)
        assertTrue(text.contains("Progress of the main exercises"), text)
    }

    @Test
    fun exerciseAttachmentHasBestValuesAndHistory() {
        val text = ChatAttachmentText.build(listOf(ChatAttachment.Exercise("wg:bench-press", "Bench Press")), sources, t)
        assertTrue(text.contains("Trained 3 times. Best set: 60×8"), text)
        assertTrue(text.contains("estimated 1RM 76 kg"), text)
        assertTrue(text.contains("heaviest 60 kg"), text)
        assertTrue(text.contains("→"), text)
    }

    @Test
    fun photosAreNoText() {
        assertEquals("", ChatAttachmentText.build(listOf(ChatAttachment.Photo("p1")), sources, t))
    }

    @Test
    fun attachmentsSurviveJsonAndBrokenJsonIsEmpty() {
        val list = listOf(ChatAttachment.Training("w1", "Push"), ChatAttachment.Photo("p1"), ChatAttachment.History(4, "Verlauf"))
        assertEquals(list, ChatAttachment.decode(ChatAttachment.encode(list)))
        assertEquals(emptyList(), ChatAttachment.decode("{kaputt"))
        assertEquals(null, ChatAttachment.encode(emptyList()))
    }

    @Test
    fun additionIsAppendedToTheBaseInstruction() {
        assertEquals("BASE", composeInstructions(" BASE ", "  "))
        val text = composeInstructions("BASE", "Antworte kurz.")
        assertTrue(text.startsWith("BASE\n\nAdditional instructions from the user"), text)
        assertTrue(text.endsWith("Antworte kurz."), text)
    }

    @Test
    fun athleteProfileLine() {
        assertEquals(null, AthleteProfile().promptLine())
        val line = AthleteProfile(TrainingGoal.STRENGTH, TrainingExperience.INTERMEDIATE, 4, 182, "Studio", "Knie links").promptLine()
        assertEquals("Goal: strength · Experience: intermediate · Training days per week: 4 · Height: 182 cm · Equipment: Studio · Limitations: Knie links", line)
    }

    @Test
    fun prefsAreStoredClampedAndLegacyInstructionsMoveIntoTheAddition() {
        val kv = InMemoryKeyValueStore()
        val settings = CoachSettingsStore(kv)
        settings.setInstructions("Sei streng.")
        val prefs = CoachChatPrefsStore(kv)
        prefs.migrateLegacyInstructions(settings)
        assertEquals("Sei streng.", prefs.state.value.addition)
        assertEquals(null, settings.state.value.instructions)
        prefs.migrateLegacyInstructions(settings) // second start: nothing doubles
        assertEquals("Sei streng.", prefs.state.value.addition)

        prefs.setProfile(AthleteProfile(daysPerWeek = 12, heightCm = 20))
        val reloaded = CoachChatPrefsStore(kv).state.value
        assertEquals(7, reloaded.profile.daysPerWeek)
        assertEquals(null, reloaded.profile.heightCm)
        assertEquals("Sei streng.", reloaded.addition)
    }

    // 8.11.: the automatic review switch is gone; stored prefs keep everything else and lose the key on the next start.
    @Test
    fun removedAutoReviewSwitchIsMigratedAway() {
        val kv = InMemoryKeyValueStore()
        kv.put(CoachChatPrefsStore.KEY, """{"profile":{"daysPerWeek":3},"addition":"Kurz bitte.","autoReview":false}""")
        val prefs = CoachChatPrefsStore(kv).state.value
        assertEquals(3, prefs.profile.daysPerWeek)
        assertEquals("Kurz bitte.", prefs.addition)
        val stored = kv.get(CoachChatPrefsStore.KEY)!!
        assertFalse(stored.contains("autoReview"), stored)
        assertTrue(stored.contains("Kurz bitte."), stored)
        // Broken JSON stays untouched (defaults in memory, nothing overwritten).
        val broken = InMemoryKeyValueStore().apply { put(CoachChatPrefsStore.KEY, "{kaputt") }
        assertEquals(CoachChatPrefs(), CoachChatPrefsStore(broken).state.value)
        assertEquals("{kaputt", broken.get(CoachChatPrefsStore.KEY))
    }

    // --- Service: what goes along -------------------------------------------------------------

    private val contexts = mutableListOf<ChatContextRequest>()
    private val dao = FakeChatDao()
    private val fake = FakeCoachTransport { _, _ -> CoachReply("""{"reply":"Ok.","workout":""}""", "") }

    private fun service(ready: Boolean = true) = CoachChatService(
        dao = dao,
        access = { if (ready) CoachAccess.Ready(ProviderId.ANTHROPIC) else CoachAccess.None },
        transport = { fake },
        model = { "claude-sonnet-5-5" },
        parts = { req -> contexts += req; ChatPromptParts("ROLE", if (req.includeProfile) "PROFILE ON" else "PROFILE OFF", req.attachments.joinToString { it.label }, emptyList()) },
        photos = { ids -> ids.map { CoachImage("2026-11-01", byteArrayOf(1)) } },
        demo = { ChatAnswer("Demo", null) },
        scope = CoroutineScope(Dispatchers.Unconfined),
        clock = { 1000L },
    )

    @Test
    fun attachmentsOfEarlierQuestionsStayInTheContextAndPhotosOnlyWithTheirQuestion() = runTest {
        val s = service()
        s.sendNow("c", ChatSendRequest("Wie lief es?", listOf(ChatAttachment.Training("w1", "Push"), ChatAttachment.Photo("p1"))))
        assertEquals(1, fake.prompts.last().images.size)
        s.sendNow("c", ChatSendRequest("Und die Übung?", listOf(ChatAttachment.Exercise("wg:bench-press", "Bench"), ChatAttachment.Training("w1", "Push"))))
        val second = contexts.last()
        assertEquals(listOf("Push", "Bench"), second.attachments.map { it.label })
        assertTrue(fake.prompts.last().images.isEmpty())
        assertEquals("Push, Bench", fake.prompts.last().dynamicSystem.lines().last())
        // The question keeps its attachments (cards in the chat), the answer the model that was asked.
        val stored = dao.messages("c")
        assertEquals(2, ChatAttachment.decode(stored.first { it.fromUser }.attachments).size)
        assertEquals("claude-sonnet-5-5", stored.last().model)
        assertEquals("ANTHROPIC", stored.last().providerId)
    }

    @Test
    fun profileOffIsStoredPerChatAndUsedOnRetry() = runTest {
        val s = service()
        s.sendNow("c", ChatSendRequest("Hallo", includeProfile = false))
        assertTrue(fake.prompts.last().dynamicSystem.startsWith("PROFILE OFF"))
        assertTrue(dao.chat("c")!!.profileOff)
        val answer = dao.messages("c").last()
        s.retry(answer.id).join()
        assertFalse(contexts.last().includeProfile)
        s.sendNow("d", ChatSendRequest("Neu"))
        assertTrue(fake.prompts.last().dynamicSystem.startsWith("PROFILE ON"))
    }

    // Fabian 8.11.: the credit server refused the chat (400 invalid_max_tokens); the app repeated it without structured
    // output and showed "Provider rejected the request (e.g. unknown model)".
    @Test
    fun backendRefusalIsNotRepeatedAndTheBubbleKeepsTheServerCode() = runTest {
        var calls = 0
        var code = "invalid_max_tokens"
        val failing = FakeCoachTransport(ProviderId.FUEL) { _, _ -> calls++; throw CoachException(CoachErrorKind.BAD_REQUEST, serverCode = code) }
        val s = CoachChatService(
            dao = dao, access = { CoachAccess.Ready(ProviderId.FUEL) }, transport = { failing }, model = { "standard" },
            parts = { ChatPromptParts("ROLE", "PROFILE", "", emptyList()) }, photos = { emptyList() }, demo = { ChatAnswer("Demo", null) },
            scope = CoroutineScope(Dispatchers.Unconfined), clock = { 1000L },
        )
        s.sendNow("c", ChatSendRequest("How was my workout?"))
        assertEquals(1, calls)
        val failed = s.messages("c").first().last()
        assertEquals(ChatMessageStatus.FAILED, failed.status)
        assertEquals("invalid_max_tokens", failed.errorCode)
        // A real provider refusal may still be tried once without structured output (OpenRouter models).
        code = "upstream_rejected"
        s.sendNow("d", ChatSendRequest("Again"))
        assertEquals(3, calls)
    }

    // Fabian 8.11.: "Ein Bild kostet ja viel mehr, als wenn man kein Bild mitschickt."
    @Test
    fun estimateGrowsWithPhotosAttachmentsAndHistoryAndSendsNothing() = runTest {
        val s = service()
        val plain = s.estimate(null, ChatSendRequest("Wie lief es?"))
        val onePhoto = s.estimate(null, ChatSendRequest("Wie lief es?", listOf(ChatAttachment.Photo("p1"))))
        val twoPhotos = s.estimate(null, ChatSendRequest("Wie lief es?", listOf(ChatAttachment.Photo("p1"), ChatAttachment.Photo("p2"))))
        // Attachments as the real text blocks (the fake [service] only joins labels).
        val real = CoachChatService(
            dao = dao, access = { CoachAccess.Ready(ProviderId.ANTHROPIC) }, transport = { fake }, model = { "claude-sonnet-5-5" },
            parts = { req -> ChatPromptParts("ROLE", "PROFILE", ChatAttachmentText.build(req.attachments, sources, t), emptyList()) },
            photos = { emptyList() }, demo = { ChatAnswer("Demo", null) }, scope = CoroutineScope(Dispatchers.Unconfined), clock = { 1000L },
        )
        val realPlain = real.estimate(null, ChatSendRequest("Wie lief es?"))
        val training = real.estimate(null, ChatSendRequest("Wie lief es?", listOf(ChatAttachment.Training("w1", "Push"))))
        val history = real.estimate(null, ChatSendRequest("Wie lief es?", listOf(ChatAttachment.Training("w1", "Push"), ChatAttachment.History(4))))
        assertTrue(onePhoto.inputTokens > plain.inputTokens)
        assertTrue(twoPhotos.inputTokens > onePhoto.inputTokens)
        assertEquals(2 * ChatTokens.image(), twoPhotos.imageTokens)
        // (A tiny training replaces the "nothing attached" note, so only more attachments are compared.)
        assertTrue(history.inputTokens > training.inputTokens)
        assertTrue(history.inputTokens > realPlain.inputTokens)
        assertTrue(plain.plusText("Und noch eine lange Frage dazu?").inputTokens > plain.inputTokens)
        // Profile off: a smaller system prompt.
        assertTrue(s.estimate(null, ChatSendRequest("Wie lief es?", includeProfile = false)).inputTokens <= plain.inputTokens)
        // A chat with history costs more than a fresh one.
        s.sendNow("c", ChatSendRequest("Erste Frage"))
        assertTrue(s.estimate("c", ChatSendRequest("Wie lief es?")).inputTokens > plain.inputTokens)
        // Estimating never sends.
        assertEquals(1, fake.prompts.size)
    }

    @Test
    fun withoutAttachmentsTheModelIsToldThatNothingIsAttached() = runTest {
        val p = CoachChatPrompt.build(ChatPromptParts("ROLE", "PROFILE", "", emptyList()), emptyList(), "q")
        assertTrue(p.dynamicSystem.contains("Nothing else is attached"), p.dynamicSystem)
    }
}

private class FakeChatDao : CoachChatDao {
    private val chats = MutableStateFlow<Map<String, CoachChatEntity>>(emptyMap())
    private val messages = MutableStateFlow<Map<String, CoachChatMessageEntity>>(emptyMap())
    private val order = compareBy<CoachChatMessageEntity>({ it.createdAt }, { !it.fromUser })

    override fun observeChats(): Flow<List<CoachChatEntity>> = chats.map { it.values.sortedByDescending { c -> c.updatedAt } }
    override suspend fun latestChat() = chats.value.values.maxByOrNull { it.updatedAt }
    override suspend fun chat(id: String) = chats.value[id]
    override fun observeChat(id: String): Flow<CoachChatEntity?> = chats.map { it[id] }
    override suspend fun upsertChat(chat: CoachChatEntity) { chats.value = chats.value + (chat.id to chat) }
    override suspend fun deleteChat(id: String) {
        chats.value = chats.value - id
        messages.value = messages.value.filterValues { it.chatId != id }
    }
    override suspend fun deleteAllChats() { chats.value = emptyMap(); messages.value = emptyMap() }
    override fun observeMessages(chatId: String): Flow<List<CoachChatMessageEntity>> =
        messages.map { m -> m.values.filter { it.chatId == chatId }.sortedWith(order) }
    override suspend fun messages(chatId: String) = messages.value.values.filter { it.chatId == chatId }.sortedWith(order)
    override suspend fun message(id: String) = messages.value[id]
    override suspend fun upsertMessage(message: CoachChatMessageEntity) { messages.value = messages.value + (message.id to message) }
    override suspend fun allChats() = chats.value.values.sortedBy { it.createdAt }
    override suspend fun allMessages() = messages.value.values.sortedBy { it.createdAt }
    override suspend fun failInterrupted() {
        messages.value = messages.value.mapValues { (_, m) -> if (m.status == "SENDING") m.copy(status = "FAILED", errorKind = "NETWORK") else m }
    }
}
