import SwiftUI
import SharedApp

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self)
        var appDelegate: AppDelegate

    init() {
        KoinApp_iosKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
