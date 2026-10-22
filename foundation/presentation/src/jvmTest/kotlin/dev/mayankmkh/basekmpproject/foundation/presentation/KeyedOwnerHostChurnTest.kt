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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * [KeyedOwnerHost]'s ledger under random churn: the host leaves and returns, its keys are added and
 * dropped while it is composed and while it is away, and only some of its active keys are composed
 * at a time, as in a lazy list. Two hosts share one parent store and churn independently.
 *
 * After every step: no ViewModel is cleared twice, none is cleared while its key is active, and
 * once a host is composed every ViewModel whose key it no longer lists has been cleared. A key that
 * comes back after a clear gets a fresh ViewModel and fresh saveable state.
 */
@OptIn(ExperimentalTestApi::class)
class KeyedOwnerHostChurnTest {
    @Test
    fun randomLeaveReturnAndKeyChurnClearsEveryRemovedKeyOnceAndNoLiveKey() {
        repeat(Seeds) { seed -> churn(seed) }
    }

    /**
     * The ledger lives in the parent store beside the hosts' own providers, under a key of its own.
     * A host whose key ends the way a ledger key does must not land on another host's ledger.
     */
    @Test
    fun aHostKeyEndingLikeALedgerKeyKeepsBothHostsApart() = runComposeUiTest {
        val parent = TestOwner()
        val one = placementKey("one")
        var active by mutableStateOf(setOf(one))
        val cleared = mutableListOf<String>()
        val models = mutableListOf<Probe>()

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                KeyedOwnerHost(active, hostKey = "list") {
                    active.forEach { key ->
                        StatefulLazyItem(key) {
                            models += viewModel { Probe("list", key.value, cleared::add) }
                        }
                    }
                }
                KeyedOwnerHost(setOf(one), hostKey = "list:keys") {
                    StatefulLazyItem(one) {
                        models += viewModel { Probe("list:keys", one.value, cleared::add) }
                    }
                }
            }
        }

        runOnIdle { active = emptySet() }
        runOnIdle { assertEquals(listOf("list/${one.value}"), cleared) }
    }

    private class Probe(
        val host: String,
        val key: String,
        private val onClear: (String) -> Unit,
    ) : ViewModel() {
        var clears = 0
        var touched = false

        override fun onCleared() {
            clears++
            onClear("$host/$key")
        }
    }

    private class HostState(val name: String) {
        var visible by mutableStateOf(true)
        var active by mutableStateOf(emptySet<FeatureInstanceKey>())
        var shown by mutableStateOf(emptySet<FeatureInstanceKey>())
        val current = mutableMapOf<String, Probe>()
        val all = mutableListOf<Probe>()
        val saveable = mutableMapOf<String, Int>()
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun churn(seed: Int) = runComposeUiTest {
        val random = Random(seed)
        val parent = TestOwner()
        val universe = (0 until Universe).map { placementKey("k$it") }
        val hosts = listOf(HostState("a"), HostState("b"))
        val violations = mutableListOf<String>()
        var step = 0

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                hosts.forEach { host ->
                    if (host.visible) {
                        KeyedOwnerHost(host.active, hostKey = host.name) {
                            (host.active intersect host.shown)
                                .sortedBy { it.value }
                                .forEach { key ->
                                    StatefulLazyItem(key) {
                                        val probe = viewModel {
                                            Probe(host.name, key.value) {
                                                if (key in host.active) {
                                                    violations +=
                                                        "seed $seed step $step: live $it cleared"
                                                }
                                            }
                                        }
                                        if (probe !in host.all) host.all += probe
                                        var saved by rememberSaveable { mutableIntStateOf(0) }
                                        val previous = host.current.put(key.value, probe)
                                        if (previous != null && previous !== probe && saved != 0) {
                                            violations +=
                                                "seed $seed step $step: ${host.name}/${key.value} fresh model, old saveable $saved"
                                        }
                                        if (saved == 0) saved = 1
                                        host.saveable[key.value] = saved
                                    }
                                }
                        }
                    }
                }
            }
        }

        repeat(Steps) {
            step = it
            runOnIdle {
                val host = hosts[random.nextInt(hosts.size)]
                when (random.nextInt(5)) {
                    0 -> host.visible = !host.visible
                    1,
                    2 ->
                        host.active =
                            universe
                                .filter {
                                    random.nextInt(3) != 0 ||
                                        it in host.active && random.nextBoolean()
                                }
                                .toSet()
                    3 -> host.shown = universe.filter { random.nextBoolean() }.toSet()
                    else -> {
                        host.active = universe.filter { random.nextBoolean() }.toSet()
                        host.visible = random.nextInt(4) != 0
                    }
                }
            }
            runOnIdle {
                hosts.forEach { host ->
                    val activeValues = host.active.map { it.value }.toSet()
                    host.all.forEach { probe ->
                        if (probe.clears > 1)
                            violations +=
                                "seed $seed step $step: ${host.name}/${probe.key} cleared ${probe.clears}x"
                    }
                    if (host.visible) {
                        val composed = (host.active intersect host.shown).map { it.value }.toSet()
                        host.all.forEach { probe ->
                            val removed = probe.key !in activeValues && probe.key !in composed
                            if (removed && probe.clears != 1) {
                                violations +=
                                    "seed $seed step $step: ${host.name}/${probe.key} removed but not cleared"
                            }
                        }
                        // The model a composed item holds now; an item off screen still points at
                        // whatever it held when last composed.
                        host.current.forEach { (key, probe) ->
                            if (key in composed && probe.clears != 0) {
                                violations +=
                                    "seed $seed step $step: ${host.name}/$key is live but its model was cleared"
                            }
                        }
                    }
                }
            }
        }
        if (violations.isNotEmpty()) fail(violations.take(20).joinToString("\n"))
        val made = hosts.sumOf { it.all.size }
        val clears = hosts.sumOf { host -> host.all.sumOf { it.clears } }
        println("STRESS KeyedOwnerHost seed $seed: $Steps steps, $made models, $clears clears")
    }

    private class TestOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private fun placementKey(id: String): FeatureInstanceKey =
        FeatureInstanceKey.forPlacement(
            surface = "churn-host",
            cellType = "probe",
            placement = CellPlacementId.fromHostStableId(id),
        )

    private companion object {
        const val Seeds = 8
        const val Steps = 150
        const val Universe = 6
    }
}
