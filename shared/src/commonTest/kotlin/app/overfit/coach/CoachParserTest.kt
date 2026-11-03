package app.overfit.coach

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachParserTest {
    private val exact = """{"rating":7,"summary":"Good.","positives":["a","b"],"improvements":["c"],"tips":["d"],
        "photo_feedback":null,"next_session":{"exercises":[{"exercise_id":"wg:bench","is_new":false,"rest_sec":120,
        "sets":[{"weight_kg":82.5,"reps":8,"duration_sec":null}],"reason":"up"}],"note":"n"},"memory":"mem"}"""

    private fun ok(text: String): CoachReview = assertIs<ParseResult.Ok>(CoachParser.parse(text)).review

    private fun withRating(r: String) = ok("""{"rating":$r,"summary":"s"}""").rating

    @Test
    fun exactJson() {
        val r = ok(exact)
        assertEquals(7, r.rating)
        assertEquals("Good.", r.summary)
        assertEquals(listOf("a", "b"), r.positives)
        assertEquals(listOf("c"), r.improvements)
        assertEquals(listOf("d"), r.tips)
        assertNull(r.photoFeedback)
        assertEquals("mem", r.memory)
        val plan = assertNotNull(r.nextSession)
        assertEquals("n", plan.note)
        val e = plan.exercises.single()
        assertEquals("wg:bench", e.exerciseId)
        assertEquals(120, e.restSec)
        assertEquals(SuggestedSet(82.5, 8, null), e.sets.single())
        assertEquals("up", e.reason)
    }

    @Test
    fun jsonInFenceWithProse() {
        val r = ok("Here is my review:\n```json\n$exact\n```\nHope it helps {not json}.")
        assertEquals(7, r.rating)
    }

    @Test
    fun proseWithoutFence() {
        val r = ok("Sure! $exact Let me know.")
        assertEquals("Good.", r.summary)
    }

    @Test
    fun camelCaseKeys() {
        val r = ok(
            """{"rating":6,"summary":"s","photoFeedback":"nice","nextSession":{"exercises":[
            {"exerciseId":"x","isNew":true,"restSec":90,"sets":[{"weightKg":50,"reps":10}]}]}}""",
        )
        assertEquals("nice", r.photoFeedback)
        val e = assertNotNull(r.nextSession).exercises.single()
        assertEquals("x", e.exerciseId)
        assertTrue(e.isNew)
        assertEquals(90, e.restSec)
        assertEquals(SuggestedSet(50.0, 10, null), e.sets.single())
    }

    @Test
    fun ratingVariants() {
        assertEquals(8, withRating("\"8\""))
        assertEquals(8, withRating("\"8/10\""))
        assertEquals(9, withRating("8.6"))
        assertEquals(10, withRating("15"))
        assertEquals(1, withRating("0"))
    }

    @Test
    fun positivesAsSingleString() {
        assertEquals(listOf("only one"), ok("""{"summary":"s","positives":"only one"}""").positives)
    }

    @Test
    fun trailingComma() {
        val r = ok("""{"summary":"s","tips":["a","b",],}""")
        assertEquals(listOf("a", "b"), r.tips)
    }

    @Test
    fun wrapperObject() {
        val r = ok("""{"coach_review":{"rating":5,"summary":"wrapped"}}""")
        assertEquals("wrapped", r.summary)
        assertEquals(5, r.rating)
    }

    @Test
    fun nextSessionNullMissingOrEmpty() {
        assertNull(ok("""{"summary":"s","next_session":null}""").nextSession)
        assertNull(ok("""{"summary":"s"}""").nextSession)
        assertNull(ok("""{"summary":"s","next_session":{"exercises":[]}}""").nextSession)
    }

    @Test
    fun exerciseWithoutIdIsDropped() {
        val r = ok(
            """{"summary":"s","next_session":{"exercises":[
            {"is_new":false,"sets":[{"reps":5}]},{"exercise_id":"keep","sets":[{"reps":5}]}]}}""",
        )
        assertEquals(listOf("keep"), assertNotNull(r.nextSession).exercises.map { it.exerciseId })
    }

    @Test
    fun weightAsCommaStringAndNegative() {
        val r = ok(
            """{"summary":"s","next_session":{"exercises":[{"exercise_id":"a","sets":[
            {"weight_kg":"82,5","reps":8},{"weight_kg":-20,"reps":10}]}]}}""",
        )
        val sets = assertNotNull(r.nextSession).exercises.single().sets
        assertEquals(82.5, sets[0].weightKg)
        assertEquals(-20.0, sets[1].weightKg)
    }

    @Test
    fun emptyObjectIsInvalid() {
        assertIs<ParseResult.Invalid>(CoachParser.parse("{}"))
    }

    @Test
    fun noJsonAtAll() {
        assertEquals(ParseResult.Invalid("no_json"), CoachParser.parse("Sorry, I cannot help."))
    }

    @Test
    fun truncatedJsonIsInvalid() {
        val cut = exact.substring(0, exact.length / 2)
        assertIs<ParseResult.Invalid>(CoachParser.parse(cut))
    }

    @Test
    fun bracesInsideStrings() {
        val text = """prefix {"summary":"a } b { c \" } d","tips":["x"]} suffix {"other":1}"""
        val extracted = assertNotNull(CoachParser.extractJsonObject(text))
        assertEquals("""{"summary":"a } b { c \" } d","tips":["x"]}""", extracted)
        assertEquals("a } b { c \" } d", ok(text).summary)
    }

    // ---- exercise_reviews ----

    private fun reviews(json: String) = ok("""{"summary":"s","exercise_reviews":$json}""").exerciseReviews

    @Test
    fun exerciseReviewsParse() {
        val r = reviews(
            """[{"exercise_id":"wg:bench","verdict":"better","comment":"More weight","next":{"weight_kg":82.5,"reps":8,"duration_sec":null}},
            {"exercise_id":"wg:row","verdict":"same","comment":"","next":null},
            {"exercise_id":"wg:plank","verdict":"worse","comment":"c","next":{"duration_sec":50}}]""",
        )
        assertEquals(listOf("wg:bench", "wg:row", "wg:plank"), r.map { it.exerciseId })
        assertEquals(listOf(Verdict.BETTER, Verdict.SAME, Verdict.WORSE), r.map { it.verdict })
        assertEquals("More weight", r[0].comment)
        assertEquals(SuggestedSet(82.5, 8, null), r[0].next)
        assertNull(r[1].comment)
        assertNull(r[1].next)
        assertEquals(SuggestedSet(null, null, 50), r[2].next)
    }

    @Test
    fun exerciseReviewVerdictVariantsAndUnknown() {
        val r = reviews(
            """[{"exercise_id":"a","verdict":"besser"},{"exercise_id":"b","verdict":"Gleich"},{"exercise_id":"c","verdict":"SCHLECHTER"},
            {"exercise_id":"d","verdict":"great"},{"exercise_id":"e"}]""",
        )
        assertEquals(listOf(Verdict.BETTER, Verdict.SAME, Verdict.WORSE, null, null), r.map { it.verdict })
    }

    @Test
    fun exerciseReviewNextWithoutValuesIsNull() {
        val r = reviews("""[{"exercise_id":"a","verdict":"same","next":{"weight_kg":null,"reps":null,"duration_sec":null}}]""")
        assertNull(r.single().next)
    }

    @Test
    fun exerciseReviewWithoutIdIsDroppedAndDuplicatesCollapse() {
        val r = reviews(
            """[{"verdict":"better"},{"exercise_id":"","verdict":"better"},{"exercise_id":"a","verdict":"better"},
            {"exercise_id":"a","verdict":"worse"},{"id":"b","verdict":"same"},"junk"]""",
        )
        assertEquals(listOf("a", "b"), r.map { it.exerciseId })
        assertEquals(Verdict.BETTER, r[0].verdict) // first one wins
    }

    @Test
    fun oldAnswerWithoutExerciseReviews() {
        assertEquals(emptyList(), ok(exact).exerciseReviews)
        assertEquals(emptyList(), ok("""{"summary":"s","exercise_reviews":null}""").exerciseReviews)
        assertEquals(emptyList(), ok("""{"summary":"s","exercise_reviews":"x"}""").exerciseReviews)
    }

    @Test
    fun reviewWithOnlyExerciseReviewsIsValid() {
        val r = ok("""{"exercise_reviews":[{"exercise_id":"a","verdict":"better"}]}""")
        assertEquals("", r.summary)
        assertEquals(1, r.exerciseReviews.size)
        assertIs<ParseResult.Invalid>(CoachParser.parse("""{"exercise_reviews":[{"verdict":"better"}]}"""))
    }

    @Test
    fun schemaRequiresExerciseReviewsWithVerdictEnum() {
        val schema = CoachSchema.schema
        val required = schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue("exercise_reviews" in required)
        val items = schema["properties"]!!.jsonObject["exercise_reviews"]!!.jsonObject["items"]!!.jsonObject
        val verdict = items["properties"]!!.jsonObject["verdict"]!!.jsonObject
        assertEquals(listOf("better", "same", "worse"), verdict["enum"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertTrue("next" in items["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        // the example in the prompt is valid for the parser and contains the field
        assertEquals("wg:bench-press", ok(CoachSchema.example).exerciseReviews.single().exerciseId)
    }
}
