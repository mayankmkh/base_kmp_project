package __PACKAGE__.api

/** Navigation intents raised by the __CELL__ Cell; the host decides what they mean. */
public sealed interface __CELL__Output {
    public data object Back : __CELL__Output

    public data class Selected(val id: String) : __CELL__Output
}
