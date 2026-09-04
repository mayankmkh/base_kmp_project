package __PACKAGE__

/** Fixtures live next to the code they describe and are shared by tests and previews. */
internal object __NAME__Fixtures {
    val loading = __NAME__State(id = "__name__-1")
    val state = loading.copy(label = "__name__", isInitialLoading = false)
    val refreshing = state.copy(isRefreshing = true)
}
