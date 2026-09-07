package dev.mayankmkh.basekmpproject

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor

@Suppress("EnumEntryName")
enum class FlavorDimension {
    environment
}

// Which backend deployment a variant talks to. The two flavors are the Android half of the
// app-wide environment axis: the name is what reaches Kotlin as `BuildConfig.APP_ENVIRONMENT`, and
// it must match a `BuildEnvironment.id` in the app's shared configuration. Staging suffixes the
// application id so both installs can sit on one device with separate storage.
@Suppress("EnumEntryName")
enum class BkpFlavor(
    val dimension: FlavorDimension,
    val applicationIdSuffix: String? = null,
    val versionNameSuffix: String? = null,
    val isDefault: Boolean = false,
) {
    staging(
        FlavorDimension.environment,
        applicationIdSuffix = ".staging",
        // So a crash report, a bug report screenshot or a Play console entry says which backend the
        // build was talking to without anyone having to check the application id.
        versionNameSuffix = "-staging",
        // What a fresh checkout selects, and what the IDE offers first. Without this AGP takes the
        // alphabetically first flavor in the dimension, which is `prod` -- so the default would be
        // to point a development build at production. Staging is the safe end of that choice.
        isDefault = true,
    ),
    prod(FlavorDimension.environment),
}

/**
 * In AGP 9 the `productFlavors { }` block DSL is only declared on the concrete extension types —
 * `CommonExtension.getProductFlavors()` returns an out-projected container that cannot be
 * registered into. Since only the application plugin declares flavors, this takes the concrete
 * [ApplicationExtension], which also removes the need for the old runtime type checks.
 */
fun configureFlavors(
    applicationExtension: ApplicationExtension,
    flavorConfigurationBlock: ApplicationProductFlavor.(flavor: BkpFlavor) -> Unit = {},
) {
    applicationExtension.apply {
        FlavorDimension.entries.forEach { flavorDimension ->
            flavorDimensions += flavorDimension.name
        }

        productFlavors {
            BkpFlavor.entries.forEach { bkpFlavor ->
                register(bkpFlavor.name) {
                    dimension = bkpFlavor.dimension.name
                    // The one build fact Kotlin cannot derive: which deployment this variant is
                    // for. The generated constant is a plain String because that is all AGP can
                    // carry across the boundary; the app maps it back to its own enum and fails on
                    // anything it does not recognise.
                    buildConfigField("String", APP_ENVIRONMENT_FIELD, "\"${bkpFlavor.name}\"")
                    flavorConfigurationBlock(this, bkpFlavor)
                    if (bkpFlavor.applicationIdSuffix != null) {
                        applicationIdSuffix = bkpFlavor.applicationIdSuffix
                    }
                    if (bkpFlavor.versionNameSuffix != null) {
                        versionNameSuffix = bkpFlavor.versionNameSuffix
                    }
                    isDefault = bkpFlavor.isDefault
                }
            }
        }
    }
}

private const val APP_ENVIRONMENT_FIELD = "APP_ENVIRONMENT"
