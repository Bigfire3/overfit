package app.overfit.share

/**
 * Bridge for the SwiftUI host: opened `overfit://` links and `.overfit` files arrive here as text
 * and wait for the import screen. The same inbox is used by [app.overfit.MainViewController].
 */
object IosIncoming {
    val inbox = ImportInbox()
}

/** Called from Swift (`IncomingKt.handleIncoming(text:)`). */
@Suppress("unused")
fun handleIncoming(text: String) = IosIncoming.inbox.offer(text)
