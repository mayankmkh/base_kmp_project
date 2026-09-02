package dev.mayankmkh.basekmpproject.convention.dsl

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory

abstract class BkpModuleExtension @Inject constructor(objects: ObjectFactory) {
    // `internal`, so a build script can only reach the features through the block below. Direct
    // access would let a module read a feature as well as set one, and reads during script
    // evaluation see a half-built extension.
    internal val features: Features = objects.newInstance(Features::class.java)

    fun features(action: Action<Features>) {
        action.execute(features)
    }
}

/**
 * Opt-ins, one function per feature. Calling the function is the whole declaration -- there is no
 * value to pass and nothing to turn off, so a module either asks for a feature or says nothing.
 *
 * The previous shape was `Property<Boolean>` flags with conventions, which meant
 * `features.flavorsDemoProd.set(true)` in a build script could be either a real request or a no-op
 * restatement of the convention, and nothing in the script said which.
 */
abstract class Features {
    internal var demoProdFlavorsEnabled = false
        private set

    internal var composeEnabled = false
        private set

    private val composeCallbacks = mutableListOf<() -> Unit>()

    /**
     * Registers `demo` and `prod` product flavors in place of plain `debug`/`release` variants.
     * Only valid on `bkp.android.app*` modules; the validator rejects it anywhere else.
     */
    fun demoProdFlavors() {
        demoProdFlavorsEnabled = true
    }

    /** Enables Compose for role plugins where it is an explicit, per-module choice. */
    fun compose() {
        if (composeEnabled) return
        composeEnabled = true
        composeCallbacks.forEach { it() }
    }

    /**
     * Reacts while the module build script is still evaluating. Applying Compose from
     * `afterEvaluate` is too late for other build-script blocks to configure its extensions.
     */
    internal fun whenComposeEnabled(action: () -> Unit) {
        if (composeEnabled) action() else composeCallbacks += action
    }
}
