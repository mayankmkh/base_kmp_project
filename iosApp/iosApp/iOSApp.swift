import SwiftUI
import SharedApp

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self)
        var appDelegate: AppDelegate

    init() {
        KoinAppKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView(root: appDelegate.root)
        }
    }
}
