//
//  AppDelegate.swift
//  iosApp
//
//  Created by Mayank on 25/06/25.
//

import UIKit
import SharedApp

class AppDelegate: NSObject, UIApplicationDelegate {
    let root: RootComponent = DefaultRootComponent(
        componentContext: DefaultComponentContext(lifecycle: ApplicationLifecycle())
    )
}
