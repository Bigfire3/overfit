package app.overfit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Starts the real activity with real storage; a fresh install begins with the onboarding. */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    @Config(qualifiers = "en-rUS")
    fun freshInstallStartsOnboardingInDeviceLanguage() {
        compose.onNodeWithText("Your name").assertIsDisplayed()
    }
}
