package dev.mayankmkh.basekmpproject.app.shared.config

import io.ktor.http.Url

/**
 * Which deployment a build talks to.
 *
 * The environment is a build fact, not a runtime setting. Every entry point reads it from its own
 * platform's build mechanism -- an Android product flavor, an Xcode build configuration, the
 * desktop launcher's system property, the host page's meta tag -- and hands it to `initKoin` beside
 * the debug signal. Nothing below the composition root sees this type; what the rest of the graph
 * receives is the configuration derived from it.
 *
 * Debug and environment are independent: a staging build can be a release build, and both
 * environments can be built in debug.
 */
enum class BuildEnvironment(val id: String) {
    Staging("staging"),
    Production("prod");

    companion object {
        /**
         * The reverse of [id], for the entry points whose platform can only carry the environment
         * as text -- a `BuildConfig` field, an `Info.plist` value, a system property, a meta tag.
         * An unknown id fails loudly rather than defaulting: a typo in a flavor name or an xcconfig
         * must never silently point a build at the wrong backend.
         */
        fun fromId(id: String): BuildEnvironment =
            entries.firstOrNull { it.id == id }
                ?: error("Unknown app environment '$id'; expected one of ${entries.map { it.id }}")
    }
}

/**
 * Where this environment's backend lives.
 *
 * Exhaustive on purpose: adding an environment fails to compile until it has been given a host, so
 * a new environment cannot ship pointing at whatever the default happened to be.
 *
 * JSONPlaceholder gives the sample screens a real endpoint to talk to and has no staging
 * deployment, so both entries name it today. A fork replaces these two lines and nothing else.
 */
internal val BuildEnvironment.apiBaseUrl: Url
    get() =
        when (this) {
            BuildEnvironment.Staging -> Url("https://jsonplaceholder.typicode.com")
            BuildEnvironment.Production -> Url("https://jsonplaceholder.typicode.com")
        }

/**
 * This names the app's own storage and must never change after a build ships.
 *
 * The environments are separate installations that must not read each other's databases,
 * preferences or secrets, so staging carries the same `.staging` suffix its Android applicationId
 * and its iOS bundle identifier do. Production keeps the bare id it shipped with.
 */
internal val BuildEnvironment.applicationId: String
    get() =
        when (this) {
            BuildEnvironment.Staging -> "$BaseApplicationId.staging"
            BuildEnvironment.Production -> BaseApplicationId
        }

private const val BaseApplicationId = "dev.mayankmkh.basekmpproject"
