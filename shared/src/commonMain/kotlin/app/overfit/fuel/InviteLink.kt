package app.overfit.fuel

/**
 * Invite link in the QR code. Custom scheme like trainings on iOS: the camera app opens it straight in Overfit; the
 * in-app scanner (Android) reads the same text. The token travels in the fragment. Only `overfit://invite#…` is read.
 */
const val INVITE_LINK_BASE = "overfit://invite"

fun isInviteLink(text: String): Boolean = text.trim().startsWith("$INVITE_LINK_BASE#")

fun inviteLink(token: String): String = "$INVITE_LINK_BASE#$token"

private val TOKEN = Regex("[A-Za-z0-9_.-]{8,512}")

/** Accepts a scanned/pasted invite link (`overfit://invite#<token>`) or the bare token. Null = not an invite code. */
fun parseInviteToken(input: String): String? {
    val t = input.trim()
    val candidate = when {
        isInviteLink(t) -> t.substringAfter('#')
        t.contains('#') || t.contains('/') || t.contains(':') -> return null
        else -> t
    }
    return candidate.takeIf { TOKEN.matches(it) }
}
