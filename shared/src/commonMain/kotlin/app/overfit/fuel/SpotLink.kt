package app.overfit.fuel

/** Public page that explains Spot to people without the app; the token travels in the fragment (never sent to a server). */
const val SPOT_LINK_BASE = "https://bigfire3.github.io/overfit/spot"

fun spotLink(token: String): String = "$SPOT_LINK_BASE#$token"

private val TOKEN = Regex("[A-Za-z0-9_.-]{8,512}")

/** Accepts a scanned/pasted spot link (`…/spot#<token>`) or the bare token. Null = not a Spot code. */
fun parseSpotToken(input: String): String? {
    val t = input.trim()
    val candidate = if (t.contains('#')) t.substringAfterLast('#') else t
    return candidate.takeIf { TOKEN.matches(it) && (t.contains('#') || !t.contains('/')) }
}
