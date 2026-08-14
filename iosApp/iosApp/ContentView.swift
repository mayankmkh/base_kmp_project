import UIKit
import SwiftUI
import SharedApp

struct ComposeView: UIViewControllerRepresentable {
    let root: RootComponent
    
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(root: root)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

struct ContentView: View {
    let root: RootComponent

    var body: some View {
        // Compose applies the system bar insets itself (each screen's `Scaffold` does), so SwiftUI
        // must hand it the whole window. Without this the safe area is inset twice and the UI is
        // banded top and bottom.
        ComposeView(root: root)
            .ignoresSafeArea()
    }
}
