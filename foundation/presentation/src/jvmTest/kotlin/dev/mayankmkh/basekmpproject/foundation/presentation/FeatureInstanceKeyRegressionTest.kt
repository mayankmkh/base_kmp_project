package dev.mayankmkh.basekmpproject.foundation.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * The key is a saved-state contract: its rendered string is what survives process death, and its
 * `/` delimiter is what keeps two placements apart. Both are pinned here.
 */
class FeatureInstanceKeyRegressionTest {
    @Test
    fun keysAreDeterministicAndKeepPresentationIdentitySeparate() {
        val placement = CellPlacementId.fromHostStableId("slot-987")

        assertEquals(
            "home-feed/live-score/slot-987",
            FeatureInstanceKey.forPlacement("home-feed", "live-score", placement).value,
        )
        assertEquals(
            "todos/list/todo-list/todo-summary/header",
            FeatureInstanceKey.forPlacement(
                    "todos/list/todo-list",
                    "todo-summary",
                    CellPlacementId.fromHostStableId("header"),
                )
                .value,
        )
        assertEquals(
            "cricket-details/match-123/live-score",
            FeatureInstanceKey.forScreen("cricket-details/match-123", "live-score").value,
        )
        assertEquals(
            "todos/detail/7/todo-detail",
            FeatureInstanceKey.forScreen("todos/detail/7", "todo-detail").value,
        )
        assertNotEquals("match-123", placement.value)
    }

    @Test
    fun blankSegmentsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            FeatureInstanceKey.forPlacement("", "todo-summary", placement("header"))
        }
        assertFailsWith<IllegalArgumentException> {
            FeatureInstanceKey.forPlacement("todos/list", " ", placement("header"))
        }
        assertFailsWith<IllegalArgumentException> { FeatureInstanceKey.forScreen("", "todo-list") }
        assertFailsWith<IllegalArgumentException> {
            FeatureInstanceKey.forScreen("todos/list", " ")
        }
        assertFailsWith<IllegalArgumentException> { CellPlacementId.fromHostStableId(" ") }
    }

    @Test
    fun aSlashInTheCellTypeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            FeatureInstanceKey.forPlacement("todos/list", "todo/summary", placement("header"))
        }
        assertFailsWith<IllegalArgumentException> {
            FeatureInstanceKey.forScreen("todos/list", "todo/list")
        }
    }

    @Test
    fun aSlashInThePlacementIdIsRejected() {
        assertFailsWith<IllegalArgumentException> { CellPlacementId.fromHostStableId("head/er") }
    }

    private fun placement(id: String): CellPlacementId = CellPlacementId.fromHostStableId(id)
}
