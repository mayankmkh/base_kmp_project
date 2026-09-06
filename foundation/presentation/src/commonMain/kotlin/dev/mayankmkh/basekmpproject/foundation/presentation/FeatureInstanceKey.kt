package dev.mayankmkh.basekmpproject.foundation.presentation

import kotlin.jvm.JvmInline

// The key is one string with `/` between its segments, so only the leading surface/route may carry
// the delimiter: it is the prefix, and everything after it must stay one segment. A slash inside a
// segment would let two different placements collapse into one key.
private fun requireSegment(label: String, value: String) {
    require(value.isNotBlank()) { "$label cannot be blank" }
    require('/' !in value) { "$label cannot contain '/'" }
}

@JvmInline
public value class CellPlacementId private constructor(public val value: String) {
    public companion object {
        public fun fromHostStableId(hostStableId: String): CellPlacementId {
            requireSegment("A Cell placement ID", hostStableId)
            return CellPlacementId(hostStableId)
        }
    }
}

@JvmInline
public value class FeatureInstanceKey private constructor(public val value: String) {
    /**
     * The `cellType` argument and the rendered key are saved-state contracts: renaming a Cell type
     * changes every key it appears in, so restored UI state is dropped after process death.
     * Semantics are defined in the architecture source of truth §12.8 (FeatureInstanceKey
     * construction contract).
     */
    public companion object {
        public fun forPlacement(
            surface: String,
            cellType: String,
            placement: CellPlacementId,
        ): FeatureInstanceKey {
            require(surface.isNotBlank()) { "A Feature surface cannot be blank" }
            requireSegment("A Cell type", cellType)
            return FeatureInstanceKey("$surface/$cellType/${placement.value}")
        }

        public fun forScreen(route: String, cellType: String): FeatureInstanceKey {
            require(route.isNotBlank()) { "A screen route cannot be blank" }
            requireSegment("A Cell type", cellType)
            // Screens already have placement identity in their route, so the stable two-part form
            // deliberately has no synthetic placement segment.
            return FeatureInstanceKey("$route/$cellType")
        }
    }
}

/**
 * Optional composition metadata for a registry-driven surface.
 *
 * [instanceKey] must be built with [FeatureInstanceKey.forPlacement] and never
 * [FeatureInstanceKey.forScreen], because a registry surface is a host with placements. The
 * interface is open rather than sealed (ADR-40) so independently compiled Feature modules can
 * implement it.
 *
 * Semantics are defined in the architecture source of truth §7.4 (CellSpec).
 */
public interface CellSpec {
    public val instanceKey: FeatureInstanceKey
}
