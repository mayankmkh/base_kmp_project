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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class StatefulLazyItemRegressionTest {
    @Test
    fun differentKeysReceiveIndependentOwners() = runComposeUiTest {
        val parent = TestOwner()
        val first = placementKey("first")
        val second = placementKey("second")
        val stores = mutableListOf<ViewModelStore>()

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                KeyedOwnerHost(setOf(first, second)) {
                    StatefulLazyItem(first) {
                        stores += requireNotNull(LocalViewModelStoreOwner.current).viewModelStore
                    }
                    StatefulLazyItem(second) {
                        stores += requireNotNull(LocalViewModelStoreOwner.current).viewModelStore
                    }
                }
            }
        }

        runOnIdle {
            assertEquals(2, stores.size)
            assertNotSame(stores[0], stores[1])
        }
    }

    @Test
    fun offscreenAndBackRetainsTheSameOwnerViewModelAndSaveableState() = runComposeUiTest {
        val parent = TestOwner()
        val itemKey = placementKey("retained")
        var visible by mutableStateOf(true)
        val stores = mutableListOf<ViewModelStore>()
        val models = mutableListOf<ProbeViewModel>()
        var incrementSaveableState: (() -> Unit)? = null
        var observedSaveableState = -1

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                KeyedOwnerHost(setOf(itemKey)) {
                    if (visible) {
                        StatefulLazyItem(itemKey) {
                            stores +=
                                requireNotNull(LocalViewModelStoreOwner.current).viewModelStore
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
            // The provider may recreate its lightweight owner wrapper; the keyed store is identity.
            assertSame(stores.first(), stores.last())
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
    fun logicalRemovalWhileStillComposedDefersClearUntilDisposal() = runComposeUiTest {
        val parent = TestOwner()
        val itemKey = placementKey("deferred")
        var activeKeys by mutableStateOf(setOf(itemKey))
        var visible by mutableStateOf(true)
        var clearCount = 0

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                KeyedOwnerHost(activeKeys) {
                    if (visible) {
                        StatefulLazyItem(itemKey) {
                            viewModel { ProbeViewModel { clearCount += 1 } }
                        }
                    }
                }
            }
        }

        runOnIdle { activeKeys = emptySet() }
        runOnIdle { assertEquals(0, clearCount) }
        runOnIdle { visible = false }
        runOnIdle { assertEquals(1, clearCount) }
    }

    @Test
    fun hostLeavingCompositionWhileParentStoreLivesRetainsChildStores() = runComposeUiTest {
        val parent = TestOwner()
        val itemKey = placementKey("host-hidden")
        var hostVisible by mutableStateOf(true)
        var clearCount = 0
        val models = mutableListOf<ProbeViewModel>()

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                if (hostVisible) {
                    KeyedOwnerHost(setOf(itemKey)) {
                        StatefulLazyItem(itemKey) {
                            models += viewModel { ProbeViewModel { clearCount += 1 } }
                        }
                    }
                }
            }
        }

        // A Nav3 entry beneath the top of the back stack leaves composition while its
        // ViewModelStore lives on; the Cells it hosts must come back with their state.
        runOnIdle { hostVisible = false }
        runOnIdle { hostVisible = true }
        runOnIdle {
            assertEquals(0, clearCount)
            assertEquals(2, models.size)
            assertSame(models.first(), models.last())
        }
    }

    @Test
    fun reorderKeepsIdentityAttachedToKeys() = runComposeUiTest {
        val parent = TestOwner()
        val first = placementKey("one")
        val second = placementKey("two")
        var order by mutableStateOf(listOf(first, second))
        val before = mutableMapOf<FeatureInstanceKey, ViewModelStore>()
        val after = mutableMapOf<FeatureInstanceKey, ViewModelStore>()
        var reordered = false

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                KeyedOwnerHost(order.toSet()) {
                    order.forEach { itemKey ->
                        StatefulLazyItem(itemKey) {
                            val store =
                                requireNotNull(LocalViewModelStoreOwner.current).viewModelStore
                            if (reordered) after[itemKey] = store else before[itemKey] = store
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
        var visible by mutableStateOf(true)
        var clearCount = 0

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                KeyedOwnerHost(keys) {
                    if (visible) {
                        keys.forEach { itemKey ->
                            StatefulLazyItem(itemKey) {
                                viewModel { ProbeViewModel { clearCount += 1 } }
                            }
                        }
                    }
                }
            }
        }

        runOnIdle { parent.viewModelStore.clear() }
        runOnIdle { assertEquals(0, clearCount) }
        // The provider defers clearing stores that still have a composed owner reference.
        runOnIdle { visible = false }
        runOnIdle { assertEquals(2, clearCount) }
    }

    @Test
    fun fallbackProviderUsesTheNearestOwner() = runComposeUiTest {
        val parent = TestOwner()
        val itemKey = placementKey("fallback")
        var visible by mutableStateOf(true)
        val stores = mutableListOf<ViewModelStore>()

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                if (visible) {
                    StatefulLazyItem(itemKey) {
                        stores += requireNotNull(LocalViewModelStoreOwner.current).viewModelStore
                    }
                }
            }
        }

        runOnIdle { visible = false }
        runOnIdle { visible = true }
        runOnIdle {
            assertEquals(2, stores.size)
            assertSame(stores.first(), stores.last())
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
        assertNotEquals("match-123", placement.value)
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
