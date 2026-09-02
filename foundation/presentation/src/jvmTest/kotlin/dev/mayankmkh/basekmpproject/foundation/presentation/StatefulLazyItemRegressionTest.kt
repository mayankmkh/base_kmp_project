package dev.mayankmkh.basekmpproject.foundation.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StatefulLazyItemRegressionTest {
    @Test
    fun differentKeysReceiveIndependentOwners() = runComposeUiTest {
        val parent = TestOwner()
        val first = placementKey("first")
        val second = placementKey("second")
        val owners = mutableListOf<ViewModelStoreOwner>()

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                KeyedOwnerHost(setOf(first, second)) {
                    StatefulLazyItem(first) {
                        owners += requireNotNull(LocalViewModelStoreOwner.current)
                    }
                    StatefulLazyItem(second) {
                        owners += requireNotNull(LocalViewModelStoreOwner.current)
                    }
                }
            }
        }

        runOnIdle {
            assertEquals(2, owners.size)
            assertNotSame(owners[0], owners[1])
        }
    }

    @Test
    fun offscreenAndBackRetainsTheSameOwnerViewModelAndSaveableState() = runComposeUiTest {
        val parent = TestOwner()
        val itemKey = placementKey("retained")
        var visible by mutableStateOf(true)
        val owners = mutableListOf<ViewModelStoreOwner>()
        val models = mutableListOf<ProbeViewModel>()
        var incrementSaveableState: (() -> Unit)? = null
        var observedSaveableState = -1

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                KeyedOwnerHost(setOf(itemKey)) {
                    if (visible) {
                        StatefulLazyItem(itemKey) {
                            owners += requireNotNull(LocalViewModelStoreOwner.current)
                            val model = viewModel<ProbeViewModel> { ProbeViewModel() }
                            models += model
                            var localCount by rememberSaveable { mutableIntStateOf(0) }
                            observedSaveableState = localCount
                            incrementSaveableState = { localCount += 1 }
                        }
                    }
                }
            }
        }

        runOnIdle {
            models.single().count = 7
            requireNotNull(incrementSaveableState).invoke()
        }
        runOnIdle { visible = false }
        runOnIdle { visible = true }
        runOnIdle {
            assertSame(owners.first(), owners.last())
            assertSame(models.first(), models.last())
            assertEquals(7, models.last().count)
            assertEquals(1, observedSaveableState)
        }
    }

    @Test
    fun logicalRemovalClearsImmediatelyAndReaddDoesNotLeakOldState() = runComposeUiTest {
        val parent = TestOwner()
        val itemKey = placementKey("replaceable")
        var activeKeys by mutableStateOf(setOf(itemKey))
        var visible by mutableStateOf(true)
        var clearCount = 0
        val models = mutableListOf<ProbeViewModel>()

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                KeyedOwnerHost(activeKeys) {
                    if (visible) {
                        StatefulLazyItem(itemKey) {
                            models += viewModel { ProbeViewModel { clearCount += 1 } }
                        }
                    }
                }
            }
        }

        runOnIdle { models.single().count = 9 }
        runOnIdle {
            visible = false
            activeKeys = emptySet()
        }
        runOnIdle { assertEquals(1, clearCount) }
        runOnIdle {
            activeKeys = setOf(itemKey)
            visible = true
        }
        runOnIdle {
            assertEquals(2, models.size)
            assertNotSame(models.first(), models.last())
            assertEquals(0, models.last().count)
        }
    }

    @Test
    fun reorderKeepsIdentityAttachedToKeys() = runComposeUiTest {
        val parent = TestOwner()
        val first = placementKey("one")
        val second = placementKey("two")
        var order by mutableStateOf(listOf(first, second))
        val before = mutableMapOf<FeatureInstanceKey, ViewModelStoreOwner>()
        val after = mutableMapOf<FeatureInstanceKey, ViewModelStoreOwner>()
        var reordered = false

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                KeyedOwnerHost(order.toSet()) {
                    order.forEach { itemKey ->
                        StatefulLazyItem(itemKey) {
                            val owner = requireNotNull(LocalViewModelStoreOwner.current)
                            if (reordered) after[itemKey] = owner else before[itemKey] = owner
                        }
                    }
                }
            }
        }

        runOnIdle {
            reordered = true
            order = order.reversed()
        }
        runOnIdle {
            assertSame(before.getValue(first), after.getValue(first))
            assertSame(before.getValue(second), after.getValue(second))
        }
    }

    @Test
    fun clearingHostOwnerClearsEveryChildStore() = runComposeUiTest {
        val parent = TestOwner()
        val keys = setOf(placementKey("one"), placementKey("two"))
        var clearCount = 0

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                KeyedOwnerHost(keys) {
                    keys.forEach { itemKey ->
                        StatefulLazyItem(itemKey) {
                            viewModel { ProbeViewModel { clearCount += 1 } }
                        }
                    }
                }
            }
        }

        runOnIdle { parent.viewModelStore.clear() }
        runOnIdle { assertEquals(2, clearCount) }
    }

    @Test
    fun fallbackRegistryUsesTheNearestOwner() = runComposeUiTest {
        val parent = TestOwner()
        val itemKey = placementKey("fallback")
        var visible by mutableStateOf(true)
        val owners = mutableListOf<ViewModelStoreOwner>()

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                if (visible) {
                    StatefulLazyItem(itemKey) {
                        owners += requireNotNull(LocalViewModelStoreOwner.current)
                    }
                }
            }
        }

        runOnIdle { visible = false }
        runOnIdle { visible = true }
        runOnIdle {
            assertEquals(2, owners.size)
            assertSame(owners.first(), owners.last())
        }
    }

    @Test
    fun keysAreDeterministicAndKeepPresentationIdentitySeparate() {
        val placement = CellPlacementId.fromHostStableId("slot-987")

        assertEquals(
            "home-feed/live-score/slot-987",
            FeatureInstanceKey.forPlacement("home-feed", "live-score", placement).value,
        )
        assertEquals(
            "cricket-details/match-123/live-score",
            FeatureInstanceKey.forScreen("cricket-details/match-123", "live-score").value,
        )
        assertTrue(placement.value != "match-123")
    }

    private class TestOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private class ProbeViewModel(private val onClear: () -> Unit = {}) : ViewModel() {
        var count = 0

        override fun onCleared() {
            onClear()
        }
    }

    private fun placementKey(id: String): FeatureInstanceKey =
        FeatureInstanceKey.forPlacement(
            surface = "test-host",
            cellType = "probe",
            placement = CellPlacementId.fromHostStableId(id),
        )
}
