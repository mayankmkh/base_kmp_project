import SwiftUI
import SharedApp

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self)
        var appDelegate: AppDelegate

    init() {
        // Which deployment this build talks to is an Xcode fact, so it travels the only way Xcode
        // can carry one into a bundle: the environment's xcconfig stamps `AppEnvironment` into
        // Info.plist and Swift hands the id to the shared module, which maps it to its own type
        // and rejects anything it does not recognise. Whether this is a debug build, Kotlin reads
        // for itself.
        guard let environmentId = Bundle.main.object(forInfoDictionaryKey: "AppEnvironment")
                as? String, !environmentId.isEmpty
        else {
            fatalError("Info.plist has no AppEnvironment; the build configuration is missing its xcconfig.")
        }

        KoinApp_iosKt.doInitKoin(environmentId: environmentId)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
