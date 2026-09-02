package dev.mayankmkh.basekmpproject.foundation.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner

private const val DefaultHostKey = "default"
private const val FallbackHostKey = "nearest-owner-fallback"
private const val ProviderKeyPrefix =
    "dev.mayankmkh.basekmpproject.foundation.presentation.KeyedOwnerHost"

private data class KeyedOwnerContext(
    val provider: ViewModelStoreProvider,
    val saveableStateHolder: SaveableStateHolder,
)

// This local is the narrow owner hand-off the primitive exists to provide; making it an argument
// to every nested item would let callers accidentally mix providers and saveable-state holders.
@Suppress("CompositionLocalAllowlist")
private val LocalKeyedOwnerContext = staticCompositionLocalOf<KeyedOwnerContext?> { null }

/**
 * Returns keyed presentation ownership retained by the nearest [LocalViewModelStoreOwner].
 *
 * The provider keeps its per-key stores in a ViewModel inside the parent store, so item ViewModels
 * survive configuration changes and are retired when the parent store clears. The provider is
 * constructed directly rather than through `rememberViewModelStoreProvider`: that helper calls
 * `clearAllKeys()` whenever its call site leaves composition while the lifecycle is still alive,
 * which would wipe every hosted Cell when, for example, a Nav3 entry drops beneath the top of the
 * back stack. Re-creating the provider object is harmless because it re-attaches to the same state
 * holder under the same parent key. Distinct keyed-owner hosts under the same parent must pass
 * distinct [hostKey] values.
 */
@Composable
private fun rememberKeyedOwnerContext(hostKey: String): KeyedOwnerContext {
    require(hostKey.isNotBlank()) { "A keyed-owner host key cannot be blank" }
    val parent =
        checkNotNull(LocalViewModelStoreOwner.current) {
            "Keyed presentation ownership requires a LocalViewModelStoreOwner"
        }
    val provider =
        remember(parent, hostKey) { ViewModelStoreProvider(parent, "$ProviderKeyPrefix:$hostKey") }
    val saveableStateHolder = rememberSaveableStateHolder()
    return remember(provider, saveableStateHolder) {
        KeyedOwnerContext(provider, saveableStateHolder)
    }
}

/**
 * Installs keyed presentation ownership for a lazy host.
 *
 * [activeKeys] is the host's logical retention window. Moving an item outside that window requests
 * that its ViewModelStore and saved state be removed at the next successful composition. The
 * first-party provider clears immediately when the item is not composed and defers clearing until
 * disposal when it still holds a composition reference. Clearing the parent host's ViewModelStore
 * likewise retires every child store, while configuration-change disposal alone does not look like
 * logical removal.
 */
@Composable
public fun KeyedOwnerHost(
    activeKeys: Set<FeatureInstanceKey>,
    hostKey: String = DefaultHostKey,
    content: @Composable () -> Unit,
) {
    val context = rememberKeyedOwnerContext(hostKey)
    val knownKeys = remember(context) { mutableSetOf<String>() }

    SideEffect {
        val activeValues = activeKeys.mapTo(mutableSetOf()) { it.value }
        (knownKeys - activeValues).forEach { removed ->
            context.provider.clearKey(removed)
            context.saveableStateHolder.removeState(removed)
        }
        knownKeys.retainAll(activeValues)
        knownKeys += activeValues
    }
    CompositionLocalProvider(LocalKeyedOwnerContext provides context, content = content)
}

/**
 * Gives one logical Feature instance a stable ViewModel and saveable-state owner.
 *
 * Identity is [FeatureInstanceKey], independently of list index, business Resource identity, or
 * navigation Route identity. Use inside [KeyedOwnerHost] so logical removal is observable. When no
 * host is installed, this falls back to a provider retained by the nearest
 * [LocalViewModelStoreOwner]; that fallback retains state until the parent owner clears because it
 * has no logical retention window against which to detect removal. Composing the same key twice at
 * once is rejected by [SaveableStateHolder], catching duplicate active placement keys in
 * tests/debug runs.
 */
@Composable
public fun StatefulLazyItem(
    key: FeatureInstanceKey,
    content: @Composable () -> Unit,
) {
    val context = LocalKeyedOwnerContext.current ?: rememberKeyedOwnerContext(FallbackHostKey)

    key(key.value) {
        context.saveableStateHolder.SaveableStateProvider(key.value) {
            val owner =
                rememberViewModelStoreOwner(
                    key = key.value,
                    provider = context.provider,
                    savedStateRegistryOwner = null,
                )
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
        }
    }
}
