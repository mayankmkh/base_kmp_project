//
//  AppDelegate.swift
//  iosApp
//
//  Created by Mayank on 25/06/25.
//

import UIKit
import SharedApp

class AppDelegate: NSObject, UIApplicationDelegate {
    // The same dispatcher the root component was built with is handed to the gesture overlay in
    // `ContentView`, so an edge swipe reaches the navigation stack.
    let backDispatcher: BackDispatcher = BackDispatcherKt.BackDispatcher()

    lazy var root: RootComponent = DefaultRootComponent(
        // Swift cannot use Kotlin default arguments, so every parameter is spelled out.
        componentContext: DefaultComponentContext(
            lifecycle: ApplicationLifecycle(),
            stateKeeper: nil,
            instanceKeeper: nil,
            backHandler: backDispatcher
        ),
        deepLinkUrl: nil
    )
}
