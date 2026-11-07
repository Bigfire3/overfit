package app.overfit.ui

import kotlinx.coroutines.runBlocking
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
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
import app.overfit.platform.RecordingHaptics
import app.overfit.platform.RecordingRestAlerts
import app.overfit.share.NoopPlatformShare
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import org.junit.Assume.assumeTrue
import org.junit.Rule
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Evidence screenshot: the editor right after an import (only with `SCREENSHOT_DIR`, see [ScreenshotTest]). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class ImportEditorScreenshotTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore().apply { put(AppData.KEY_SAMPLES_SEEDED, "1") }
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(
        database, kv,
        readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() },
        readSample = { File("../shared/src/commonMain/composeResources/files/samples/$it.json").readBytes() },
    )
    private val share = NoopPlatformShare()

    @Test fun importedEditor() {
        val dir = System.getenv("SCREENSHOT_DIR")
        assumeTrue(dir != null)
        store.setName("Kim"); store.setBirthYear(1995); store.completeOnboarding()
        share.clipboard = """{"v":1,"kind":"template","name":"Oberkörper A","exercises":[
            {"name":"Bench Press","restSec":150,"sets":[{"kg":60,"reps":8},{"kg":60,"reps":8},{"kg":60,"reps":8}]},
            {"name":"Pull-up","restSec":120,"sets":[{"reps":8},{"reps":8}]},
            {"name":"Zzyzx Spezialcurl","restSec":60,"sets":[{"kg":10,"reps":12}]}]}"""
        compose.setContent {
            OverfitApp(AppDependencies(store, Providers(HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) })), data, versionName = "t", share = share, today = { LocalDate(2026, 11, 7) }))
        }
        compose.onNodeWithContentDescription("Neues Workout").performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Workout importieren")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Workout importieren").performScrollTo().performClick()
        compose.onNodeWithText("Aus Zwischenablage importieren").performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Speichern")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Bench Press")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val out = File(dir!!).also { it.mkdirs() }
        File(out, "01-editor-nach-import-dunkel-360.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
