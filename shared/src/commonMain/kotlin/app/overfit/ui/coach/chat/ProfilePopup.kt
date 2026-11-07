package app.overfit.ui.coach.chat

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import app.overfit.ui.coach.CoachProfileForm
import app.overfit.ui.components.Text
import app.overfit.ui.theme.Theme

/**
 * "Dein Profil" (from the profile chip): the same [CoachProfileForm] as Settings → Coach profile, in its popup look.
 * Body weight is read only (from the calendar). "Speichern" stores it in [CoachChatEnv.prefs] and closes the popup.
 */
@Composable
internal fun ColumnScope.ProfilePopup(env: CoachChatEnv, onSaved: () -> Unit) {
    val s = chatStrings()
    val start = remember { env.prefs.state.value.profile }
    val weight = remember { env.latestWeight() }
    Text(s.profileTitle, style = Theme.type.label.copy(fontWeight = FontWeight.SemiBold, fontSize = Theme.type.title.fontSize * 0.65f))
    CoachProfileForm(
        profile = start,
        latestWeight = weight,
        onSave = { env.prefs.setProfile(it); onSaved() },
        popup = true,
    )
}
