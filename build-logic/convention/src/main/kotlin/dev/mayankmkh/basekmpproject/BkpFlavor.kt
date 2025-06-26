package dev.mayankmkh.basekmpproject

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor

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

fun configureFlavors(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
    flavorConfigurationBlock: ProductFlavor.(flavor: BkpFlavor) -> Unit = {}
) {
    commonExtension.apply {
        FlavorDimension.values().forEach { flavorDimension ->
            flavorDimensions += flavorDimension.name
        }

        productFlavors {
            BkpFlavor.values().forEach { bkpFlavor ->
                register(bkpFlavor.name) {
                    dimension = bkpFlavor.dimension.name
                    flavorConfigurationBlock(this, bkpFlavor)
                    if (this@apply is ApplicationExtension && this is ApplicationProductFlavor) {
                        if (bkpFlavor.applicationIdSuffix != null) {
                            applicationIdSuffix = bkpFlavor.applicationIdSuffix
                        }
                    }
                }
            }
        }
    }
}
