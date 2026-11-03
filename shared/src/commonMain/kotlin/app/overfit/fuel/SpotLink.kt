package app.overfit.fuel

/**
 * Invite link in the QR code. Custom scheme like trainings on iOS: the camera app opens it straight in Overfit; the
 * in-app scanner (Android) reads the same text. The token travels in the fragment.
 *
 * New links use `overfit://invite#…`; the old `overfit://spot#…` (earlier name of the program) is still read so old QR
 * codes and links keep working.
 */
const val INVITE_LINK_BASE = "overfit://invite"
const val SPOT_LINK_BASE = "overfit://spot"

fun isSpotLink(text: String): Boolean {
    val t = text.trim()
    return t.startsWith(INVITE_LINK_BASE) || t.startsWith(SPOT_LINK_BASE)
}

fun spotLink(token: String): String = "$INVITE_LINK_BASE#$token"

private val TOKEN = Regex("[A-Za-z0-9_.-]{8,512}")

/** Accepts a scanned/pasted invite link (`…/invite#<token>` or legacy `…/spot#<token>`) or the bare token. Null = not an invite code. */
fun parseSpotToken(input: String): String? {
    val t = input.trim()
    val candidate = if (t.contains('#')) t.substringAfterLast('#') else t
    return candidate.takeIf { TOKEN.matches(it) && (t.contains('#') || !t.contains('/')) }
}
