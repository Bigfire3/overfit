package app.overfit.ai

import app.overfit.coach.ChatCompletionsCoach
import app.overfit.coach.CoachErrorKind
import app.overfit.coach.CoachException
import app.overfit.coach.CoachPrompt
import app.overfit.coach.CoachReply
import app.overfit.coach.CoachSchema
import app.overfit.coach.anthropicBody
import app.overfit.domain.Equipment
import app.overfit.domain.Language
import app.overfit.domain.Muscle
import app.overfit.domain.TrackingType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseAutofillParserTest {
    private fun json(
        tracking: String = "WEIGHT_REPS",
        primary: String = """["chest","triceps"]""",
        secondary: String = """["shoulders"]""",
        equipment: String = "dumbbell",
        rest: String = "75",
        note: String = "Ellbogen eng",
    ) = """{"trackingType":"$tracking","primaryMuscles":$primary,"secondaryMuscles":$secondary,"equipment":"$equipment","restSec":$rest,"note":"$note"}"""

    @Test fun validJsonBecomesSuggestion() {
        val s = assertNotNull(ExerciseAutofillParser.parse(json()))
        assertEquals(TrackingType.WEIGHT_REPS, s.trackingType)
        assertEquals(listOf(Muscle.CHEST, Muscle.TRICEPS), s.primary)
        assertEquals(listOf(Muscle.SHOULDERS), s.secondary)
        assertEquals(Equipment.DUMBBELL, s.equipment)
        assertEquals(75, s.restSec)
        assertEquals("Ellbogen eng", s.note)
    }

    @Test fun unknownKeysAreDropped() {
        val s = assertNotNull(ExerciseAutofillParser.parse(json(primary = """["chest","wings"]""", secondary = """["nope","lats"]""", equipment = "jetpack")))
        assertEquals(listOf(Muscle.CHEST), s.primary)
        assertEquals(listOf(Muscle.LATS), s.secondary)
        assertNull(s.equipment)
    }

    @Test fun secondaryNeverRepeatsPrimary() {
        val s = assertNotNull(ExerciseAutofillParser.parse(json(primary = """["chest"]""", secondary = """["chest","triceps"]""")))
        assertEquals(listOf(Muscle.TRICEPS), s.secondary)
    }

    @Test fun restIsClampedAndRoundedToFifteen() {
        fun rest(v: String) = ExerciseAutofillParser.parse(json(rest = v))!!.restSec
        assertEquals(15, rest("1"))
        assertEquals(15, rest("0"))
        assertEquals(600, rest("5000"))
        assertEquals(90, rest("88"))
        assertEquals(75, rest("80"))
        assertEquals(60, rest("66"))
        assertEquals(600, rest("598"))
        assertTrue(listOf("15", "37", "100", "250", "599").all { rest(it)!! % 15 == 0 && rest(it)!! in 15..600 })
    }

    @Test fun jsonInsideFencesOrTextWorks() {
        assertNotNull(ExerciseAutofillParser.parse("```json\n${json()}\n```"))
        assertNotNull(ExerciseAutofillParser.parse("Klar! Hier:\n${json()}\nViel Spaß."))
    }

    @Test fun invalidTrackingTypeOrGarbageGivesNull() {
        assertNull(ExerciseAutofillParser.parse(json(tracking = "SWIMMING")))
        assertNull(ExerciseAutofillParser.parse("kein json"))
        assertNull(ExerciseAutofillParser.parse("{broken"))
        assertNull(ExerciseAutofillParser.parse(""))
    }
}

class ExerciseAutofillTest {
    private val valid = """{"trackingType":"REPS","primaryMuscles":["abs"],"secondaryMuscles":[],"equipment":"bodyweight","restSec":45,"note":"Rumpf anspannen"}"""

    private class Fake(private val answers: MutableList<Any>) : AiGateway {
        val sent = mutableListOf<Pair<CoachPrompt, Boolean>>()
        override fun available() = true
        override suspend fun send(prompt: CoachPrompt, structured: Boolean): CoachReply {
            sent += prompt to structured
            val next = answers.removeAt(0)
            if (next is CoachException) throw next
            return CoachReply(next as String, next)
        }
    }

    @Test fun sendsAutofillSchemaAndName() = runTest {
        val fake = Fake(mutableListOf(valid))
        val s = ExerciseAutofill(fake).fill("  Plank  ", Language.DE)
        assertEquals(TrackingType.REPS, s.trackingType)
        val (prompt, structured) = fake.sent.single()
        assertTrue(structured)
        assertEquals(ExerciseAutofillSchema.schema, prompt.schema)
        assertTrue(prompt.user.contains("Plank"))
        assertEquals(ExerciseAutofillSchema.MAX_TOKENS, prompt.maxTokens)
    }

    @Test fun badRequestWithStructuredRetriesOnceWithoutIt() = runTest {
        val fake = Fake(mutableListOf(CoachException(CoachErrorKind.BAD_REQUEST), valid))
        ExerciseAutofill(fake).fill("Plank", Language.EN)
        assertEquals(listOf(true, false), fake.sent.map { it.second })
    }

    @Test fun badRequestTwiceIsThrown() = runTest {
        val fake = Fake(mutableListOf(CoachException(CoachErrorKind.BAD_REQUEST), CoachException(CoachErrorKind.BAD_REQUEST)))
        val e = assertFailsWith<CoachException> { ExerciseAutofill(fake).fill("Plank", Language.EN) }
        assertEquals(CoachErrorKind.BAD_REQUEST, e.kind)
        assertEquals(2, fake.sent.size)
    }

    @Test fun unparseableAnswerIsAskedAgainOnce() = runTest {
        val fake = Fake(mutableListOf("Sorry, no JSON", valid))
        ExerciseAutofill(fake).fill("Plank", Language.DE)
        assertEquals(2, fake.sent.size)
    }

    @Test fun twoUnparseableAnswersThrowInvalidAnswer() = runTest {
        val fake = Fake(mutableListOf("nope", "still nope", valid))
        val e = assertFailsWith<CoachException> { ExerciseAutofill(fake).fill("Plank", Language.DE) }
        assertEquals(CoachErrorKind.INVALID_ANSWER, e.kind)
        assertEquals(2, fake.sent.size)
    }

    @Test fun noAccessIsPassedThrough() = runTest {
        val fake = Fake(mutableListOf(CoachException(CoachErrorKind.NO_ACCESS), valid))
        val e = assertFailsWith<CoachException> { ExerciseAutofill(fake).fill("Plank", Language.DE) }
        assertEquals(CoachErrorKind.NO_ACCESS, e.kind)
        assertEquals(1, fake.sent.size)
    }
}

class ExerciseAutofillSchemaTest {
    private fun enumOf(name: String): List<String> {
        val props = ExerciseAutofillSchema.schema["properties"]!!.jsonObject
        val node = props.getValue(name).jsonObject
        val holder = if (node["items"] != null) node.getValue("items").jsonObject else node
        return holder.getValue("enum").jsonArray.map { it.jsonPrimitive.content }
    }

    @Test fun enumsMatchAppKeys() {
        assertEquals(Muscle.entries.map { it.key }, enumOf("primaryMuscles"))
        assertEquals(Muscle.entries.map { it.key }, enumOf("secondaryMuscles"))
        assertEquals(Equipment.entries.map { it.key }, enumOf("equipment"))
        assertEquals(TrackingType.entries.map { it.name }, enumOf("trackingType"))
    }

    private val autofill = CoachPrompt("S", "D", "U", schema = ExerciseAutofillSchema.schema, schemaName = ExerciseAutofillSchema.NAME, maxTokens = ExerciseAutofillSchema.MAX_TOKENS)
    private val coach = CoachPrompt("S", "D", "U")

    @Test fun anthropicBodyUsesPromptSchema() {
        val b = anthropicBody(autofill, "claude-sonnet-5-5", structured = true)
        assertEquals(ExerciseAutofillSchema.schema, b["output_config"]!!.jsonObject["format"]!!.jsonObject["schema"])
        assertEquals(ExerciseAutofillSchema.MAX_TOKENS, b["max_tokens"]!!.jsonPrimitive.content.toInt())

        val c = anthropicBody(coach, "claude-sonnet-5-5", structured = true)
        assertEquals(CoachSchema.schema, c["output_config"]!!.jsonObject["format"]!!.jsonObject["schema"])
    }

    @Test fun chatCompletionsBodyUsesPromptSchema() {
        val http = HttpClient(MockEngine { respondOk("{}") })
        val t = ChatCompletionsCoach(http, { "k" }, ProviderId.OPENROUTER, "https://example.test/v1")
        val b = t.body(autofill, "m", structured = true)
        val js = b["response_format"]!!.jsonObject["json_schema"]!!.jsonObject
        assertEquals(ExerciseAutofillSchema.NAME, js["name"]!!.jsonPrimitive.content)
        assertEquals(ExerciseAutofillSchema.schema, js["schema"])
        assertEquals(ExerciseAutofillSchema.MAX_TOKENS, b["max_tokens"]!!.jsonPrimitive.content.toInt())

        val c = t.body(coach, "m", structured = true).getValue("response_format").jsonObject.getValue("json_schema").jsonObject
        assertEquals(CoachSchema.NAME, c["name"]!!.jsonPrimitive.content)
        assertEquals(CoachSchema.schema, c["schema"])
    }
}
