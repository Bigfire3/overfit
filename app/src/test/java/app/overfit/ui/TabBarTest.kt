package app.overfit.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.ai.Providers
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.DeviceDefaults
import app.overfit.data.InMemoryKeyValueStore
import app.overfit.data.InMemorySecretStore
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The bottom bar on a narrow phone (360 dp): exactly four tabs (no History any more), each label one word on
 * one line without being cut off, in German and English. With `SCREENSHOT_DIR` set it also saves the screen
 * in German and English (evidence for the tab icons).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class TabBarTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })

    @Test fun german() = check(Language.DE, listOf("Workouts", "Coach", "Kalender", "Profil"))
    @Test fun english() = check(Language.EN, listOf("Workouts", "Coach", "Calendar", "Profile"))

    private fun check(language: Language, labels: List<String>) {
        store.setName("Fabi")
        store.setLanguage(language)
        store.completeOnboarding()
        val deps = AppDependencies(store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test")
        compose.setContent { OverfitApp(deps) }
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("tab:PROFILE")).fetchSemanticsNodes().isNotEmpty() }

        System.getenv("SCREENSHOT_DIR")?.let { dir ->
            val file = File(File(dir).also { it.mkdirs() }, "tabbar-360dp-${language.name.lowercase()}.png")
            compose.waitForIdle()
            file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertEquals(listOf("START", "COACH", "PHOTOS", "PROFILE"), Tab.entries.map { it.name })
        for ((tab, label) in Tab.entries.zip(labels)) {
            val node = compose.onAllNodes(hasTestTag("tab:${tab.name}"))[0].fetchSemanticsNode()
            val texts = compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes()
                .filter { t -> generateSequence(t.parent) { it.parent }.any { it.id == node.id } }
            assertEquals("label $label in tab ${tab.name}", 1, texts.size)
            val layouts = mutableListOf<TextLayoutResult>()
            texts[0].config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            assertEquals("$label on one line", 1, layouts.single().lineCount)
            val l = layouts.single()
            // Natural text width against the tab's width (hasVisualOverflow/didOverflowWidth are unreliable here:
            // Robolectric reports an overflow for labels that are fully drawn, see the screenshot).
            val natural = l.multiParagraph.intrinsics.maxIntrinsicWidth
            assertTrue("$label ($natural px) fits its tab (${node.size.width} px)", natural <= node.size.width)
            assertFalse("$label ellipsized", l.isLineEllipsized(0))
            assertEquals("$label fully visible", label.length, l.getLineEnd(0, visibleEnd = true))
            // The whole tab column is the touch target (padding inside the clickable): at least 48 dp high.
            val minPx = 48 * ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
            assertTrue("tab ${tab.name} is ${node.size.height} px high, needs ≥ $minPx", node.size.height >= minPx)
        }

    }
}
