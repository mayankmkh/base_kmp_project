package dev.mayankmkh.basekmpproject.foundation.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

private const val DefaultHostKey = "default"
private const val FallbackHostKey = "nearest-owner-fallback"
private const val RegistryViewModelKeyPrefix =
    "dev.mayankmkh.basekmpproject.foundation.presentation.KeyedOwnerRegistry"

/**
 * Registry of presentation owners retained by a parent [ViewModelStoreOwner].
 *
 * Callers normally obtain this from [rememberKeyedOwnerRegistry] and let [KeyedOwnerHost] reconcile
 * logical keys. Its constructor and mutation operations stay internal so a Feature cannot
 * accidentally make ViewModel lifetime depend on composition recycling.
 */
public class KeyedOwnerRegistry internal constructor() {
    private val owners = mutableMapOf<String, ItemViewModelStoreOwner>()

    internal fun ownerFor(key: FeatureInstanceKey): ViewModelStoreOwner =
        owners.getOrPut(key.value) { ItemViewModelStoreOwner() }

    internal fun retainOnly(activeKeys: Set<FeatureInstanceKey>): Set<String> {
        val retainedValues = activeKeys.mapTo(mutableSetOf()) { it.value }
        val removedValues = owners.keys.filterTo(mutableSetOf()) { it !in retainedValues }
        removedValues.forEach { removed -> owners.remove(removed)?.viewModelStore?.clear() }
        return removedValues
    }

    internal fun clear() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
    }

    internal fun ownerCount(): Int = owners.size
}

private class ItemViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

private class KeyedOwnerRegistryViewModel : ViewModel() {
    val registry = KeyedOwnerRegistry()

    override fun onCleared() {
        registry.clear()
    }
}

private data class KeyedOwnerContext(
    val registry: KeyedOwnerRegistry,
    val saveableStateHolder: SaveableStateHolder,
)

// This local is the narrow owner hand-off the primitive exists to provide; making it an argument
// to every nested item would let callers accidentally mix registries and saveable-state holders.
@Suppress("CompositionLocalAllowlist")
private val LocalKeyedOwnerContext = staticCompositionLocalOf<KeyedOwnerContext?> { null }

/**
 * Returns the registry stored in the nearest [LocalViewModelStoreOwner].
 *
 * The parent ViewModel is intentional: item ViewModels survive configuration changes with their
 * host. Distinct keyed-owner hosts under the same parent must pass distinct [hostKey] values.
 */
@Composable
@Suppress("ViewModelInjection")
public fun rememberKeyedOwnerRegistry(hostKey: String = DefaultHostKey): KeyedOwnerRegistry {
    require(hostKey.isNotBlank()) { "A keyed-owner host key cannot be blank" }
    val parentOwner =
        checkNotNull(LocalViewModelStoreOwner.current) {
            "Keyed presentation ownership requires a LocalViewModelStoreOwner"
        }
    val holder =
        viewModel<KeyedOwnerRegistryViewModel>(
            viewModelStoreOwner = parentOwner,
            key = "$RegistryViewModelKeyPrefix:$hostKey",
        ) {
            KeyedOwnerRegistryViewModel()
        }
    return holder.registry
}

/**
 * Installs keyed presentation ownership for a lazy host.
 *
 * [activeKeys] is the host's logical dataset, not merely its currently composed/visible items.
 * Moving an item offscreen therefore retains its ViewModel and saveable state; removing its key
 * from this set clears its [ViewModelStore] and saved state at the next successful composition.
 * Clearing the parent host's ViewModelStore clears every remaining child owner. The registry lives
 * in a parent ViewModel so a configuration-change disposal does not look like logical removal.
 */
@Composable
public fun KeyedOwnerHost(
    activeKeys: Set<FeatureInstanceKey>,
    hostKey: String = DefaultHostKey,
    content: @Composable () -> Unit,
) {
    val registry = rememberKeyedOwnerRegistry(hostKey)
    val saveableStateHolder = rememberSaveableStateHolder()
    val context =
        remember(registry, saveableStateHolder) { KeyedOwnerContext(registry, saveableStateHolder) }

    SideEffect {
        registry.retainOnly(activeKeys).forEach(saveableStateHolder::removeState)
    }
    CompositionLocalProvider(LocalKeyedOwnerContext provides context, content = content)
}

/**
 * Gives one logical Feature instance a stable ViewModel and saveable-state owner.
 *
 * Identity is [FeatureInstanceKey], independently of list index, business Resource identity, or
 * navigation Route identity. Use inside [KeyedOwnerHost] so logical removal is observable. When no
 * host is installed, this falls back to a registry retained by the nearest
 * [LocalViewModelStoreOwner]; that fallback retains state until the parent owner clears because it
 * has no logical dataset against which to detect removal. Composing the same key twice at once is
 * rejected by [SaveableStateHolder], catching duplicate active placement keys in tests/debug runs.
 */
@Composable
public fun StatefulLazyItem(
    key: FeatureInstanceKey,
    content: @Composable () -> Unit,
) {
    val installedContext = LocalKeyedOwnerContext.current
    val fallbackRegistry =
        if (installedContext == null) rememberKeyedOwnerRegistry(FallbackHostKey) else null
    val fallbackStateHolder = if (installedContext == null) rememberSaveableStateHolder() else null
    val context =
        installedContext
            ?: remember(fallbackRegistry, fallbackStateHolder) {
                KeyedOwnerContext(
                    registry = checkNotNull(fallbackRegistry),
                    saveableStateHolder = checkNotNull(fallbackStateHolder),
                )
            }

    key(key.value) {
        context.saveableStateHolder.SaveableStateProvider(key.value) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides context.registry.ownerFor(key),
                content = content,
            )
        }
    }
}
