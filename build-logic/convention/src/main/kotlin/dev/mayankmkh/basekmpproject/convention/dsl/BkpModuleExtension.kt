package dev.mayankmkh.basekmpproject.convention.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class BkpModuleExtension @Inject constructor(objects: ObjectFactory) {
    val targets: Targets = objects.newInstance(Targets::class.java)
    val features: Features = objects.newInstance(Features::class.java)
    val cocoapods: Cocoapods = objects.newInstance(Cocoapods::class.java)
}

abstract class Targets @Inject constructor() {
    abstract val android: Property<Boolean>
    abstract val jvm: Property<Boolean>
    abstract val ios: Property<Boolean>
}

abstract class Features @Inject constructor() {
    abstract val flavorsDemoProd: Property<Boolean>
    abstract val firebase: Property<Boolean>
    abstract val cocoapods: Property<Boolean>
}

abstract class Cocoapods @Inject constructor() {
    abstract val frameworkBaseName: Property<String>
    abstract val iosDeploymentTarget: Property<String>
    abstract val podfilePath: Property<String>
}
