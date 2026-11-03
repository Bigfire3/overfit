package app.overfit.fuel

/**
 * Deep link of the web purchase page ("Zurück zur App"). Opening it brings the user back to the credit screen, which
 * then reloads the balance. Carries no data: the purchase is booked by the server (payment webhook), never by the link.
 */
const val CREDIT_RETURN_LINK = "overfit://guthaben"

fun isCreditReturnLink(text: String): Boolean {
    val t = text.trim()
    return t == CREDIT_RETURN_LINK || t.startsWith("$CREDIT_RETURN_LINK/") || t.startsWith("$CREDIT_RETURN_LINK?") || t.startsWith("$CREDIT_RETURN_LINK#")
}
