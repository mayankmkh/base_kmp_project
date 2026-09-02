package dev.mayankmkh.basekmpproject.foundation.presentation

import kotlin.jvm.JvmInline

@JvmInline
public value class CellPlacementId private constructor(public val value: String) {
    public companion object {
        public fun fromHostStableId(hostStableId: String): CellPlacementId {
            require(hostStableId.isNotBlank()) { "A Cell placement ID cannot be blank" }
            return CellPlacementId(hostStableId)
        }
    }
}

@JvmInline
public value class FeatureInstanceKey private constructor(public val value: String) {
    public companion object {
        public fun forPlacement(
            surface: String,
            cellType: String,
            placement: CellPlacementId,
        ): FeatureInstanceKey {
            require(surface.isNotBlank()) { "A Feature surface cannot be blank" }
            require(cellType.isNotBlank()) { "A Cell type cannot be blank" }
            return FeatureInstanceKey("$surface/$cellType/${placement.value}")
        }

        public fun forScreen(route: String, cellType: String): FeatureInstanceKey {
            require(route.isNotBlank()) { "A screen route cannot be blank" }
            require(cellType.isNotBlank()) { "A screen Cell type cannot be blank" }
            // Screens already have placement identity in their route, so the stable two-part form
            // deliberately has no synthetic placement segment.
            return FeatureInstanceKey("$route/$cellType")
        }
    }
}

/** Open Cell contract: Feature modules may add Cell implementations without a central registry. */
public interface CellSpec {
    public val instanceKey: FeatureInstanceKey
}
