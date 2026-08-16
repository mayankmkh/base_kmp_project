package dev.mayankmkh.basekmpproject.convention.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class BkpModuleExtension @Inject constructor(objects: ObjectFactory) {
    val features: Features = objects.newInstance(Features::class.java)
}

abstract class Features @Inject constructor() {
    abstract val flavorsDemoProd: Property<Boolean>
    abstract val firebase: Property<Boolean>
}
