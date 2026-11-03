package app.overfit.fuel

/**
 * Spot link in the QR code. Custom scheme like trainings on iOS: the camera app opens it straight in Overfit; the in-app
 * scanner (Android) reads the same text. The token travels in the fragment.
 */
const val SPOT_LINK_BASE = "overfit://spot"

fun isSpotLink(text: String): Boolean = text.trim().startsWith(SPOT_LINK_BASE)

fun spotLink(token: String): String = "$SPOT_LINK_BASE#$token"

private val TOKEN = Regex("[A-Za-z0-9_.-]{8,512}")

/** Accepts a scanned/pasted spot link (`…/spot#<token>`) or the bare token. Null = not a Spot code. */
fun parseSpotToken(input: String): String? {
    val t = input.trim()
    val candidate = if (t.contains('#')) t.substringAfterLast('#') else t
    return candidate.takeIf { TOKEN.matches(it) && (t.contains('#') || !t.contains('/')) }
}
