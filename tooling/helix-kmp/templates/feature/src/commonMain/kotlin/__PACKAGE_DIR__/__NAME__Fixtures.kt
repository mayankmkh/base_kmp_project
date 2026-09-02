package __PACKAGE__

/** Fixtures live next to the code they describe and are shared by tests and previews. */
internal object __NAME__Fixtures {
    val state = __NAME__State(id = "__name__-1")
    val busy = state.copy(isBusy = true)
}
