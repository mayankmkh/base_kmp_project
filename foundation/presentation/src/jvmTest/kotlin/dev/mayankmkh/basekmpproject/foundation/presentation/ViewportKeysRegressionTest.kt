package dev.mayankmkh.basekmpproject.foundation.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTestApi::class)
class ViewportKeysRegressionTest {
    @Test
    fun itemsInsideTheBufferAreRetainedAcrossScroll() = runComposeUiTest {
        val parent = TestOwner()
        val itemKeys = itemKeys()
        val firstKey = itemKeys.first()
        val models = mutableListOf<ProbeViewModel>()
        var clearCount = 0
        lateinit var listState: LazyListState

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                listState = rememberLazyListState()
                val viewportKeys =
                    rememberViewportKeys(listState, itemKeys, buffer = 1) { item -> item }
                KeyedOwnerHost(viewportKeys) {
                    ProbeList(
                        listState = listState,
                        itemKeys = itemKeys,
                        onModel = { itemKey, model ->
                            if (itemKey == firstKey && models.lastOrNull() !== model)
                                models += model
                        },
                        onClear = { itemKey ->
                            if (itemKey == firstKey) clearCount += 1
                        },
                    )
                }
            }
        }

        lateinit var original: ProbeViewModel
        runOnIdle { original = models.single() }
        runOnIdle { runBlocking { listState.scrollToItem(1) } }
        runOnIdle { assertEquals(0, clearCount) }
        runOnIdle { runBlocking { listState.scrollToItem(0) } }
        runOnIdle {
            assertSame(original, models.last())
            assertEquals(0, clearCount)
        }
    }

    @Test
    fun itemsBeyondTheBufferAreCleared() = runComposeUiTest {
        val parent = TestOwner()
        val itemKeys = itemKeys()
        val firstKey = itemKeys.first()
        val models = mutableListOf<ProbeViewModel>()
        var clearCount = 0
        lateinit var listState: LazyListState

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                listState = rememberLazyListState()
                val viewportKeys =
                    rememberViewportKeys(listState, itemKeys, buffer = 1) { item -> item }
                KeyedOwnerHost(viewportKeys) {
                    ProbeList(
                        listState = listState,
                        itemKeys = itemKeys,
                        onModel = { itemKey, model ->
                            if (itemKey == firstKey && models.lastOrNull() !== model)
                                models += model
                        },
                        onClear = { itemKey ->
                            if (itemKey == firstKey) clearCount += 1
                        },
                    )
                }
            }
        }

        lateinit var original: ProbeViewModel
        runOnIdle { original = models.single() }
        runOnIdle { runBlocking { listState.scrollToItem(20) } }
        runOnIdle { assertEquals(1, clearCount) }
        runOnIdle { runBlocking { listState.scrollToItem(0) } }
        runOnIdle {
            assertEquals(2, models.size)
            assertNotSame(original, models.last())
            assertEquals(1, clearCount)
        }
    }

    @Test
    fun windowTracksVisibleIndicesWithBuffer() = runComposeUiTest {
        val itemKeys = itemKeys()
        lateinit var listState: LazyListState
        var viewportKeys: Set<FeatureInstanceKey> = emptySet()

        setContent {
            listState = rememberLazyListState()
            viewportKeys = rememberViewportKeys(listState, itemKeys, buffer = 1) { item -> item }
            LazyColumn(
                state = listState,
                modifier = Modifier.width(200.dp).height(200.dp),
            ) {
                items(itemKeys, key = { item -> item.value }) {
                    Box(Modifier.fillMaxWidth().height(50.dp))
                }
            }
        }

        runOnIdle { assertEquals(itemKeys.slice(0..4).toSet(), viewportKeys) }
        runOnIdle { runBlocking { listState.scrollToItem(10) } }
        runOnIdle { assertEquals(itemKeys.slice(9..14).toSet(), viewportKeys) }
    }

    private class TestOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private class ProbeViewModel(private val onClear: () -> Unit) : ViewModel() {
        override fun onCleared() {
            onClear()
        }
    }

    private fun itemKeys(): List<FeatureInstanceKey> =
        List(30) { index ->
            FeatureInstanceKey.forPlacement(
                surface = "viewport-test",
                cellType = "probe",
                placement = CellPlacementId.fromHostStableId("item-$index"),
            )
        }

    @Composable
    @Suppress("ViewModelInjection")
    private fun ProbeList(
        listState: LazyListState,
        itemKeys: List<FeatureInstanceKey>,
        onModel: (FeatureInstanceKey, ProbeViewModel) -> Unit,
        onClear: (FeatureInstanceKey) -> Unit,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.width(200.dp).height(200.dp),
        ) {
            items(itemKeys, key = { item -> item.value }) { itemKey ->
                StatefulLazyItem(itemKey) {
                    val model = viewModel { ProbeViewModel { onClear(itemKey) } }
                    onModel(itemKey, model)
                    Box(Modifier.fillMaxWidth().height(50.dp))
                }
            }
        }
    }
}
