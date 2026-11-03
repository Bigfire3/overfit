import SwiftUI
import Shared

@main
struct OverfitApp: App {
    init() {
        // Before launch finishes: a tap on the rest notification opens the running workout.
        IosNotificationTaps.shared.install()
    }

    var body: some Scene {
        WindowGroup {
            // Compose draws edge to edge and handles safe areas and the keyboard itself.
            ComposeView()
                .ignoresSafeArea()
                // Shared trainings: overfit://t#… links and .overfit files (Files, Mail, AirDrop).
                .onOpenURL { url in
                    if url.isFileURL {
                        let scoped = url.startAccessingSecurityScopedResource()
                        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                        if let text = try? String(contentsOf: url, encoding: .utf8) {
                            IncomingKt.handleIncoming(text: text)
                        }
                    } else {
                        IncomingKt.handleIncoming(text: url.absoluteString)
                    }
                }
        }
    }
}

/** Hosts the shared Compose Multiplatform UI (shared/src/iosMain/.../MainViewController.kt). */
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
