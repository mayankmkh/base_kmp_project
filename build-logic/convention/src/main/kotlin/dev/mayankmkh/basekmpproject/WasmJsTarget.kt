package dev.mayankmkh.basekmpproject

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmJsTargetDsl

/**
 * The project's one definition of what "web" compiles to.
 *
 * Two decisions live here rather than in each module:
 *
 * `wasmJs` over `js`. Compose renders the whole UI onto a canvas, so the web build is bound by
 * layout and draw throughput rather than by DOM work, which is the workload Wasm suits and
 * JavaScript does not. The cost is the WasmGC floor -- Chrome and Firefox from late 2023, Safari
 * from 18.2 -- with no fallback for anything older.
 *
 * Adding `js` alongside is possible: every dependency here publishes a `-js` variant, so what it
 * takes is a `webMain` source set parented over both and a second set of compilations and bundles.
 * It buys reach on pre-WasmGC browsers at the cost of the slower backend on the workload above, so
 * it waits for someone who needs that reach.
 *
 * `browser()` over `nodejs()`/`d8()`. The target only compiles; an environment is what gives it run
 * and test tasks, and the browser is the one this project ships to. It is also the expensive half:
 * `browser()` is what drags in the npm toolchain that costs the build its project isolation.
 */
@OptIn(ExperimentalWasmDsl::class)
internal fun KotlinMultiplatformExtension.wasmJsBrowser(configure: KotlinWasmJsTargetDsl.() -> Unit = {}) {
    wasmJs {
        browser()
        configure()
    }
}
