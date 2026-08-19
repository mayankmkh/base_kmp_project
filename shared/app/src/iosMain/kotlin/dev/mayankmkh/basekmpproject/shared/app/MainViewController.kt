package dev.mayankmkh.basekmpproject.shared.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.PredictiveBackGestureOverlay
import com.arkivanov.essenty.backhandler.BackDispatcher
import dev.mayankmkh.basekmpproject.shared.app.nav.RootComponent

// UIKit's own edge swipe is gone once Compose owns the whole window, so the overlay reads the
// gesture and feeds it to the same `BackDispatcher` the root component was built with.
// `backIcon = null`: drawing the arrow would pull in a Material icons artifact for one glyph.
@OptIn(ExperimentalDecomposeApi::class)
@Suppress("FunctionNaming")
fun MainViewController(root: RootComponent, backDispatcher: BackDispatcher) =
    ComposeUIViewController {
        PredictiveBackGestureOverlay(
            backDispatcher = backDispatcher,
            backIcon = null,
            modifier = Modifier.fillMaxSize(),
        ) {
            App(root)
        }
    }
