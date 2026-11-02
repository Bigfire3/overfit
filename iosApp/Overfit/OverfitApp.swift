import SwiftUI
import Shared

@main
struct OverfitApp: App {
    var body: some Scene {
        WindowGroup {
            // Compose draws edge to edge and handles safe areas and the keyboard itself.
            ComposeView().ignoresSafeArea()
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
