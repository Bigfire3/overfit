package app.overfit.chat

import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import app.overfit.i18n.ChatGerman
import app.overfit.share.ShareFormat
import app.overfit.share.ShareMapper
import app.overfit.share.ShareParse
import app.overfit.ui.chat.MarkdownLite
import app.overfit.workout.exercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatInboxTest {
    private val json = """{"v":1,"kind":"template","name":"Push B","exercises":[
        {"name":"Bench","sets":[{"kg":65,"reps":8},{"kg":65,"reps":8},{"kg":65,"reps":8}]},
        {"name":"Dips","sets":[{"reps":10}]}]}"""

    @Test
    fun inboxTemplateBecomesImportPreview() {
        val training = assertIs<ShareParse.Ok>(ShareFormat.fromJson(json)).training
        val catalog = listOf(exercise("e1", name = "Bench"))
        val preview = ShareMapper.resolve(training, catalog)
        assertEquals("Push B", preview.name)
        assertEquals(2, preview.exercises.size)
        assertEquals("e1", preview.exercises[0].exercise?.id)
        assertTrue(preview.exercises[1].isNew)
    }

    @Test
    fun diffShowsChangedAddedAndRemoved() {
        val old = template(id = 7, name = "Push").copy(
            exercises = listOf(
                app.overfit.workout.entry(exercise("e1", name = "Bench"), 120, app.overfit.workout.planned(60.0, 8), app.overfit.workout.planned(60.0, 8)),
                app.overfit.workout.entry(exercise("e2", name = "Fly"), null, app.overfit.workout.planned(10.0, 12)),
            ),
        )
        val training = (ShareFormat.fromJson(json) as ShareParse.Ok).training
        val preview = ShareMapper.resolve(training, listOf(exercise("e1", name = "Bench"), exercise("e2", name = "Fly")))
        val lines = TemplateDiff.lines(old, preview, ChatGerman, WeightUnit.KG, Language.DE)
        assertEquals(
            listOf("Name: Push → Push B", "Bench: 2× 60 kg 8× → 3× 65 kg 8×", "+ Dips", "− Fly"),
            lines,
        )
    }

    @Test
    fun noteMarkdownRendersAsLines() {
        val lines = MarkdownLite.lines("## Gut\n- **Bank** 80 kg\n\nText mit `code`")
        assertEquals(listOf("Gut", "Bank 80 kg", "", "Text mit code"), lines.map { it.text })
        assertTrue(lines[0].heading)
        assertTrue(lines[1].bullet)
    }
}
