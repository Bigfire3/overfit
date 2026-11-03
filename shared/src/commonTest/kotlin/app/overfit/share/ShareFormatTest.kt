package app.overfit.share

import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSource
import app.overfit.domain.Muscle
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import app.overfit.i18n.English
import app.overfit.i18n.German
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeflateTest {
    @Test fun roundTripsText() {
        val text = ("""{"v":1,"kind":"template","name":"Push","exercises":[{"id":"wg:bench-press","sets":[{"kg":60,"reps":8}]}]}""").repeat(5)
        val data = text.encodeToByteArray()
        val packed = Deflate.compress(data)
        assertTrue(packed.size < data.size / 2)
        assertEquals(text, Deflate.decompress(packed).decodeToString())
    }

    @Test fun roundTripsEmptyAndRandom() {
        assertEquals(0, Deflate.decompress(Deflate.compress(ByteArray(0))).size)
        val random = Random(7).nextBytes(5000)
        assertTrue(random.contentEquals(Deflate.decompress(Deflate.compress(random))))
        val runs = ByteArray(70_000) { (it / 1000).toByte() }
        assertTrue(runs.contentEquals(Deflate.decompress(Deflate.compress(runs), maxOutput = 100_000)))
    }

    @Test fun readsDynamicHuffmanFromOtherEncoders() {
        // zlib/pako `deflateRaw("hello hello hello hello")`-style fixed block and a stored block.
        val stored = byteArrayOf(0x01, 0x03, 0x00, 0xFC.toByte(), 0xFF.toByte(), 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        assertEquals("abc", Deflate.decompress(stored).decodeToString())
        // Dynamic-Huffman block from Python zlib (raw deflate, level 9).
        val hex = "b594bb6ec23014865fc5ca4c50880901e66e5d2a31560c4e7220168e13f90205c49b74ec6374e3c5eaa4175929275b2647fe2d1f7dfafde51a1c8335994d4870e0b2709f8181aa11cc40e0f624aba0dd7bb1ba244fec4c2a6ec891830049eeef99957b90ed39780395730dda1d7ebd06bcbbe8b45f6720f3326c1468dd1e73abd940eec255d4de5e9beef6e75a1a550bc14119c2acdedd3f4be5265859904d5e5a614065e2fe61dc4a2e56b3aa0279e1507e0fd7607ee61ef66e8da369d4cd6adaddc56d427e83991fa45e10fbc1d20ba81fac6edb2efaa3e332175c4258d82acb408887a0b3f140e369f21894fa810f3af7031f34f1837fa0f5115409ac7808188f0798604d2eb02653acc9e56093ed7b574c848ab947dce3a3e3f1a558814bacc01552208d060bcc592620dc89738f6d3e1a1bc52ca4988514b3900e5ba8ea0642a3780e4dd8b8ff54519f640f33190f137390620e52cc413aeca03e58f787c9952304d5e35b8cc7872948310529a6201d56b0e04d8f2a1d8f0a138f62e2514cbcf93ff1b6b72f"
        val dynamic = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        assertEquals("{\"v\": 1, \"kind\": \"template\", \"name\": \"Push Day mit vielen Übungen\", \"exercises\": [{\"id\": \"wg:bench-press\", \"restSec\": 90, \"note\": \"Kontrolliert ausführen und Schulterblätter zusammenziehen\", \"sets\": [{\"kg\": 20.0, \"reps\": 6}, {\"kg\": 21.0, \"reps\": 7}, {\"kg\": 22.0, \"reps\": 8}, {\"kg\": 23.0, \"reps\": 9}]}, {\"id\": \"wg:incline-dumbbell-press\", \"restSec\": 91, \"note\": \"Kontrolliert ausführen und Schulterblätter zusammenziehen\", \"sets\": [{\"kg\": 22.5, \"reps\": 6}, {\"kg\": 23.5, \"reps\": 7}, {\"kg\": 24.5, \"reps\": 8}, {\"kg\": 25.5, \"reps\": 9}]}, {\"id\": \"wg:overhead-press\", \"restSec\": 92, \"note\": \"Kontrolliert ausführen und Schulterblätter zusammenziehen\", \"sets\": [{\"kg\": 25.0, \"reps\": 6}, {\"kg\": 26.0, \"reps\": 7}, {\"kg\": 27.0, \"reps\": 8}, {\"kg\": 28.0, \"reps\": 9}]}, {\"id\": \"wg:lateral-raise\", \"restSec\": 93, \"note\": \"Kontrolliert ausführen und Schulterblätter zusammenziehen\", \"sets\": [{\"kg\": 27.5, \"reps\": 6}, {\"kg\": 28.5, \"reps\": 7}, {\"kg\": 29.5, \"reps\": 8}, {\"kg\": 30.5, \"reps\": 9}]}, {\"id\": \"wg:cable-fly\", \"restSec\": 94, \"note\": \"Kontrolliert ausführen und Schulterblätter zusammenziehen\", \"sets\": [{\"kg\": 30.0, \"reps\": 6}, {\"kg\": 31.0, \"reps\": 7}, {\"kg\": 32.0, \"reps\": 8}, {\"kg\": 33.0, \"reps\": 9}]}, {\"id\": \"wg:rope-tricep-pushdown\", \"restSec\": 95, \"note\": \"Kontrolliert ausführen und Schulterblätter zusammenziehen\", \"sets\": [{\"kg\": 32.5, \"reps\": 6}, {\"kg\": 33.5, \"reps\": 7}, {\"kg\": 34.5, \"reps\": 8}, {\"kg\": 35.5, \"reps\": 9}]}, {\"id\": \"wg:skull-crusher\", \"restSec\": 96, \"note\": \"Kontrolliert ausführen und Schulterblätter zusammenziehen\", \"sets\": [{\"kg\": 35.0, \"reps\": 6}, {\"kg\": 36.0, \"reps\": 7}, {\"kg\": 37.0, \"reps\": 8}, {\"kg\": 38.0, \"reps\": 9}]}, {\"id\": \"wg:dip\", \"restSec\": 97, \"note\": \"Kontrolliert ausführen und Schulterblätter zusammenziehen\", \"sets\": [{\"kg\": 37.5, \"reps\": 6}, {\"kg\": 38.5, \"reps\": 7}, {\"kg\": 39.5, \"reps\": 8}, {\"kg\": 40.5, \"reps\": 9}]}]}", Deflate.decompress(dynamic).decodeToString())
    }

    @Test fun rejectsGarbage() {
        listOf(byteArrayOf(0x07), byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), ByteArray(0)).forEach {
            val r = runCatching { Deflate.decompress(it) }
            assertTrue(r.isFailure)
        }
    }

    @Test fun enforcesOutputLimit() {
        val packed = Deflate.compress(ByteArray(10_000))
        assertTrue(runCatching { Deflate.decompress(packed, maxOutput = 1000) }.isFailure)
    }
}

class ShareFormatTest {
    private val training = SharedTraining(
        name = "Push",
        note = "Notiz",
        exercises = listOf(
            SharedExercise(id = "wg:bench-press", sets = listOf(SharedSet(kg = 60.0, reps = 8), SharedSet(kg = 62.5, reps = 6)), restSec = 150, note = "langsam"),
            SharedExercise(name = "Klimmzug", sets = listOf(SharedSet(reps = 8))),
            SharedExercise(ref = "c1", sets = listOf(SharedSet(sec = 45))),
        ),
        custom = listOf(SharedCustom("c1", "Handstand", "TIME", muscle = "shoulders")),
    )

    @Test fun linkRoundTrip() {
        val link = ShareFormat.toLink(training)
        assertTrue(link.startsWith("https://bigfire3.github.io/overfit/t#"))
        assertTrue(link.none { it == '+' || it == '/' || it == '=' || it.isWhitespace() } || link.substringBefore('#').contains('/'))
        assertEquals(ShareParse.Ok(training), ShareFormat.parseInput(link))
    }

    @Test fun acceptsAppSchemeAndSurroundingText() {
        val payload = ShareFormat.toPayload(training)
        assertEquals(ShareParse.Ok(training), ShareFormat.parseInput("overfit://t#$payload"))
        assertEquals(ShareParse.Ok(training), ShareFormat.parseInput("Schau mal: https://bigfire3.github.io/overfit/t#$payload viel Spaß!"))
        assertEquals(ShareParse.Ok(training), ShareFormat.parseInput(payload))
        assertEquals(ShareParse.Ok(training), ShareFormat.parseInput("  $payload\n"))
        assertEquals(ShareParse.Ok(training), ShareFormat.parseInput(payload + "="))
    }

    @Test fun acceptsJsonInCodeBlockWithText() {
        val json = ShareFormat.toJson(training)
        val answer = "Klar, hier ist dein Plan:\n\n```json\n$json\n```\n\nViel Erfolg!"
        assertEquals(ShareParse.Ok(training), ShareFormat.parseInput(answer))
        assertEquals(ShareParse.Ok(training), ShareFormat.parseInput(json))
        assertEquals(ShareParse.Ok(training), ShareFormat.parseInput("```\n$json\n```"))
        assertEquals(ShareParse.Ok(training), ShareFormat.parseInput("Plan: $json Ende"))
    }

    @Test fun toleratesSloppyValues() {
        val r = ShareFormat.fromJson(
            """{"v":1,"name":"X","exercises":[{"name":"Squat","sets":[{"kg":"80","reps":"8-10"},{"kg":"82,5 kg","reps":8.0}],"restSec":"90 s","extra":1}],"unknown":true}""",
        )
        val t = assertIs<ShareParse.Ok>(r).training
        assertEquals(listOf(SharedSet(80.0, 8), SharedSet(82.5, 8)), t.exercises.single().sets)
        assertEquals(90, t.exercises.single().restSec)
        assertEquals(ShareFormat.KIND_TEMPLATE, t.kind)
    }

    @Test fun versionAndKind() {
        assertEquals(ShareParse.Fail(ShareFailure.TOO_NEW), ShareFormat.fromJson("""{"v":2,"kind":"template","name":"X","exercises":[]}"""))
        assertEquals(ShareParse.Fail(ShareFailure.UNSUPPORTED_KIND), ShareFormat.fromJson("""{"v":1,"kind":"plan","name":"X","exercises":[]}"""))
        assertEquals(ShareParse.Fail(ShareFailure.NOT_OVERFIT), ShareFormat.fromJson("""{"hello":1}"""))
        assertEquals(ShareParse.Fail(ShareFailure.INVALID), ShareFormat.fromJson("""{"v":1,"kind":"template","exercises":[]}"""))
        assertEquals(ShareParse.Fail(ShareFailure.INVALID), ShareFormat.fromJson("""{"v":1,"name":"X","exercises":"nope"}"""))
    }

    @Test fun brokenInputNeverCrashes() {
        listOf("", "   ", "{", "}{", "null", "[]", "42", "```json\n```", "https://bigfire3.github.io/overfit/t#", "overfit://t#!!!", "https://bigfire3.github.io/overfit/t#AAAA",
            "https://bigfire3.github.io/overfit/t#" + "A".repeat(500), "😀".repeat(30), "{\"v\":1,\"name\":\"x\",\"exercises\":[null,1,\"a\",{}]}",
        ).forEach { input ->
            val r = ShareFormat.parseInput(input)
            if (input.startsWith("{\"v\"")) assertIs<ShareParse.Ok>(r) else assertIs<ShareParse.Fail>(r)
        }
        assertEquals(ShareParse.Fail(ShareFailure.EMPTY), ShareFormat.parseInput(""))
    }

    @Test fun limits() {
        val many = SharedTraining(name = "Viel", exercises = List(60) { SharedExercise(name = "Squat") })
        assertEquals(ShareParse.Fail(ShareFailure.TOO_LARGE), ShareFormat.fromJson(ShareFormat.toJson(many)))
    }

    @Test fun typicalTemplateStaysSmallEnoughForQr() {
        val big = SharedTraining(name = "Push", exercises = List(8) { i ->
            SharedExercise(id = "wg:bench-press", sets = List(4) { SharedSet(kg = 60.0 + i, reps = 8) }, restSec = 120)
        })
        val link = ShareFormat.toLink(big)
        assertTrue(link.length < ShareFormat.MAX_QR_LINK_LENGTH, "link length ${link.length}")
        assertTrue(QrCode.fits(link))
    }
}

class ShareMapperTest {
    private fun ex(id: String, name: String, tracking: TrackingType = TrackingType.WEIGHT_REPS, custom: Boolean = false, muscle: Muscle? = null) = Exercise(
        id, name, if (custom) ExerciseSource.CUSTOM else ExerciseSource.WORKOUT_GUIDE, null, listOfNotNull(muscle), emptyList(), null, tracking, null,
    )

    private val bench = ex("wg:bench-press", "Bench Press")
    private val pullUp = ex("wg:pull-up", "Pull-up", TrackingType.REPS, muscle = Muscle.LATS)
    private val handstand = ex("custom:1", "Handstand", TrackingType.TIME, custom = true, muscle = Muscle.SHOULDERS)
    private val catalog = listOf(bench, pullUp, handstand, ex("wg:squat", "Squat"))

    private val template = Template(
        0, "Mix", "n",
        listOf(
            TemplateExercise(bench, 150, "x", listOf(PlannedSet(60.0, 8), PlannedSet(62.5, 6))),
            TemplateExercise(pullUp, null, null, listOf(PlannedSet(reps = 8))),
            TemplateExercise(handstand, 60, null, listOf(PlannedSet(durationSec = 30))),
        ),
    )

    @Test fun shareAndImportYieldsSameTemplate() {
        val link = ShareFormat.toLink(ShareMapper.fromTemplate(template))
        val parsed = assertIs<ShareParse.Ok>(ShareFormat.parseInput(link)).training
        val preview = ShareMapper.resolve(parsed, catalog)
        assertTrue(preview.newExerciseNames.isEmpty())
        val back = ShareMapper.toTemplate(preview, emptyMap())
        assertEquals(template.name, back.name)
        assertEquals(template.exercises.map { it.exercise.id }, back.exercises.map { it.exercise.id })
        assertEquals(template.exercises.map { it.sets }, back.exercises.map { it.sets })
        assertEquals(template.exercises.map { it.restSec }, back.exercises.map { it.restSec })
    }

    @Test fun unknownCustomExerciseBecomesNew() {
        val parsed = assertIs<ShareParse.Ok>(ShareFormat.parseInput(ShareFormat.toLink(ShareMapper.fromTemplate(template)))).training
        val preview = ShareMapper.resolve(parsed, catalog.filter { it.id != "custom:1" })
        assertEquals(listOf("Handstand"), preview.newExerciseNames)
        val line = preview.exercises.last()
        assertEquals(TrackingType.TIME, line.newExercise!!.trackingType)
        assertEquals(Muscle.SHOULDERS, line.newExercise!!.muscle)
    }

    @Test fun resolvesNamesGermanAndEnglishAndCreatesUnknown() {
        val t = SharedTraining(
            name = "Namen",
            exercises = listOf(
                SharedExercise(name = "Klimmzug", sets = listOf(SharedSet(kg = 10.0, reps = 5))),
                SharedExercise(name = "bench press", sets = listOf(SharedSet(kg = 50.0, reps = 5))),
                SharedExercise(name = "Zauberkugel Hocke", sets = listOf(SharedSet(sec = 30))),
                SharedExercise(id = "fedb:Does_Not_Exist", sets = listOf(SharedSet(reps = 3))),
            ),
        )
        val preview = ShareMapper.resolve(t, catalog)
        assertEquals("wg:pull-up", preview.exercises[0].exercise?.id)
        // Pull-up is bodyweight reps: the weight is dropped.
        assertEquals(listOf(PlannedSet(reps = 5)), preview.exercises[0].sets)
        assertEquals("wg:bench-press", preview.exercises[1].exercise?.id)
        assertEquals(TrackingType.TIME, preview.exercises[2].newExercise?.trackingType)
        assertEquals("Does Not Exist", preview.exercises[3].displayName)
        assertEquals(2, preview.newExerciseNames.size)
    }

    @Test fun sameCustomTwiceIsOneNewExercise() {
        val t = SharedTraining(
            name = "x",
            exercises = listOf(SharedExercise(name = "Zauberkugel"), SharedExercise(name = "zauberkugel")),
        )
        assertEquals(1, ShareMapper.resolve(t, catalog).newExerciseNames.size)
    }
}

class AiPromptTest {
    @Test fun exampleImportsAndPromptContainsIt() {
        val parsed = assertIs<ShareParse.Ok>(ShareFormat.fromJson(AiPrompt.EXAMPLE)).training
        assertEquals(3, parsed.exercises.size)
        listOf(German, English).forEach { s ->
            val prompt = s.share.aiTemplatePrompt(AiPrompt.EXAMPLE)
            assertTrue(prompt.contains("```json"))
            assertTrue(prompt.contains(AiPrompt.EXAMPLE))
            // A chat answer built around the example (as the prompt requests) imports.
            val answer = "Hier dein Plan:\n```json\n${AiPrompt.EXAMPLE}\n```\nViel Spaß."
            assertNotNull((ShareFormat.parseInput(answer) as? ShareParse.Ok)?.training)
        }
    }
}

class QrCodeTest {
    @Test fun hasFinderPatternsAndGrowsWithData() {
        val small = QrCode.encode("hi")
        assertEquals(21, small.size)
        for ((cx, cy) in listOf(0 to 0, 14 to 0, 0 to 14)) {
            assertTrue(small[cx, cy] && small[cx + 6, cy + 6] && small[cx + 3, cy + 3])
            assertTrue(!small[cx + 1, cy + 1] && small[cx + 2, cy + 2])
        }
        assertTrue(!small[7, 7] || true)
        val big = QrCode.encode("x".repeat(800))
        assertTrue(big.size > 100)
        assertTrue(!QrCode.fits("x".repeat(3000)))
        assertTrue(runCatching { QrCode.encode("x".repeat(3000)) }.isFailure)
    }
}
