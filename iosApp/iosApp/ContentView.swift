import UIKit
import SwiftUI
import SharedApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

struct ContentView: View {
    var body: some View {
        // Compose applies the system bar insets itself (each screen's `Scaffold` does), so SwiftUI
        // must hand it the whole window. Without this the safe area is inset twice and the UI is
        // banded top and bottom.
        ComposeView()
            .ignoresSafeArea()
    }
}
