package dev.mayankmkh.basekmpproject

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor

@Suppress("EnumEntryName")
enum class FlavorDimension {
    contentType
}

// The content for the app can either come from local static data which is useful for demo
// purposes, or from a production backend server which supplies up-to-date, real content.
// These two product flavors reflect this behaviour.
@Suppress("EnumEntryName")
enum class BkpFlavor(val dimension: FlavorDimension, val applicationIdSuffix: String? = null) {
    demo(FlavorDimension.contentType, applicationIdSuffix = ".demo"),
    prod(FlavorDimension.contentType),
}

/**
 * In AGP 9 the `productFlavors { }` block DSL is only declared on the concrete extension types —
 * `CommonExtension.getProductFlavors()` returns an out-projected container that cannot be
 * registered into. Since only the application plugin declares flavors, this takes the concrete
 * [ApplicationExtension], which also removes the need for the old runtime type checks.
 */
fun configureFlavors(
    applicationExtension: ApplicationExtension,
    flavorConfigurationBlock: ApplicationProductFlavor.(flavor: BkpFlavor) -> Unit = {}
) {
    applicationExtension.apply {
        FlavorDimension.entries.forEach { flavorDimension ->
            flavorDimensions += flavorDimension.name
        }

        productFlavors {
            BkpFlavor.entries.forEach { bkpFlavor ->
                register(bkpFlavor.name) {
                    dimension = bkpFlavor.dimension.name
                    flavorConfigurationBlock(this, bkpFlavor)
                    if (bkpFlavor.applicationIdSuffix != null) {
                        applicationIdSuffix = bkpFlavor.applicationIdSuffix
                    }
                }
            }
        }
    }
}
