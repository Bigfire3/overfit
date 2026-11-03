package app.overfit.i18n

import app.overfit.domain.Language

/**
 * Texts for buying credit on the web and redeeming credit codes. Same rule as [Strings]: German and English are both
 * required. Own file so the parallel renaming of [FuelStrings] ("Fuel" → "Guthaben") does not collide.
 */
class WebCreditStrings(
    val webTopUp: String,
    /** Heading above the web packages. */
    val webChannel: String,
    /** One web package: credit and price (1:1). */
    val webPackage: (credit: String, price: String) -> String,
    val webTopUpNote: String,
    val webTopUpTestNote: String,
    val webTopUpOpening: String,
    val webTopUpFailed: String,
    val redeemCode: String,
    val redeemTitle: String,
    val redeemText: String,
    val redeemPlaceholder: String,
    val redeemAction: String,
    val redeemDone: (amount: String) -> String,
    val redeemInvalid: String,
    val redeemUsed: String,
    val redeemRevoked: String,
    val ledgerWebPurchase: String,
    val ledgerCode: String,
    val ledgerRefund: String,
    val ledgerChargeback: String,
)

val GermanWebCredit = WebCreditStrings(
    webTopUp = "Im Web aufladen",
    webChannel = "Im Web",
    webPackage = { credit, price -> "$credit Guthaben · Preis: $price" },
    webTopUpNote = "Öffnet die Kaufseite im Browser. 10 € zahlen = 10 € Guthaben, ohne Store-Aufpreis. Kein Konto nötig.",
    webTopUpTestNote = "Testmodus – keine echte Zahlung, nur Testkarten.",
    webTopUpOpening = "Kaufseite wird geöffnet …",
    webTopUpFailed = "Die Kaufseite ließ sich nicht öffnen. Bitte später noch einmal versuchen.",
    redeemCode = "Code einlösen",
    redeemTitle = "Guthaben-Code einlösen",
    redeemText = "Codes gibt es auf der Kaufseite im Web, auch als Geschenk. Jeder Code lässt sich einmal einlösen.",
    redeemPlaceholder = "XXXX-XXXX-XXXX",
    redeemAction = "Einlösen",
    redeemDone = { "$it Guthaben gutgeschrieben." },
    redeemInvalid = "Diesen Code gibt es nicht. Bitte prüfen.",
    redeemUsed = "Dieser Code wurde schon eingelöst.",
    redeemRevoked = "Dieser Code wurde nach einer Erstattung gesperrt.",
    ledgerWebPurchase = "Aufladung im Web",
    ledgerCode = "Code eingelöst",
    ledgerRefund = "Erstattung",
    ledgerChargeback = "Rückbuchung",
)

val EnglishWebCredit = WebCreditStrings(
    webTopUp = "Top up on the web",
    webChannel = "On the web",
    webPackage = { credit, price -> "$credit credit · price: $price" },
    webTopUpNote = "Opens the purchase page in your browser. Pay €10 = €10 of credit, no store surcharge. No account needed.",
    webTopUpTestNote = "Test mode – no real payment, test cards only.",
    webTopUpOpening = "Opening the purchase page …",
    webTopUpFailed = "The purchase page could not be opened. Please try again later.",
    redeemCode = "Redeem code",
    redeemTitle = "Redeem a credit code",
    redeemText = "Codes are sold on the purchase page on the web, also as a gift. Each code can be redeemed once.",
    redeemPlaceholder = "XXXX-XXXX-XXXX",
    redeemAction = "Redeem",
    redeemDone = { "$it of credit added." },
    redeemInvalid = "This code does not exist. Please check it.",
    redeemUsed = "This code has already been redeemed.",
    redeemRevoked = "This code was blocked after a refund.",
    ledgerWebPurchase = "Web top-up",
    ledgerCode = "Code redeemed",
    ledgerRefund = "Refund",
    ledgerChargeback = "Chargeback",
)

fun webCreditStringsFor(language: Language): WebCreditStrings = when (language) {
    Language.DE -> GermanWebCredit
    Language.EN -> EnglishWebCredit
}
