package dev.mayankmkh.basekmpproject.foundation.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

private const val DefaultViewportBuffer = 3

/**
 * Keys of items inside the lazy-list viewport plus [buffer] items on each side.
 *
 * Feed the result to [KeyedOwnerHost]. The buffer must include at least one item so lazy prefetch
 * does not compose an item outside the host's retention window.
 */
@Composable
public fun <T> rememberViewportKeys(
    listState: LazyListState,
    items: List<T>,
    buffer: Int = DefaultViewportBuffer,
    keyOf: (T) -> FeatureInstanceKey?,
): Set<FeatureInstanceKey> {
    require(buffer >= 1) { "A viewport key buffer must be at least one item" }
    val viewportKeys by
        remember(listState, items, buffer, keyOf) {
            derivedStateOf {
                if (items.isEmpty()) return@derivedStateOf emptySet()

                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val firstVisible =
                    visibleItems.firstOrNull()?.index ?: return@derivedStateOf emptySet()
                val lastVisible =
                    visibleItems.lastOrNull()?.index ?: return@derivedStateOf emptySet()
                val firstIndex = (firstVisible - buffer).coerceAtLeast(items.indices.first)
                val lastIndex = (lastVisible + buffer).coerceAtMost(items.indices.last)

                buildSet {
                    for (index in firstIndex..lastIndex) {
                        keyOf(items[index])?.let(::add)
                    }
                }
            }
        }
    return viewportKeys
}
