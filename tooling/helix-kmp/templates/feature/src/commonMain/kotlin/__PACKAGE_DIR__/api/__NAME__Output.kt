package __PACKAGE__.api

/** Navigation intents raised by the __name__ Feature; the host decides what they mean. */
public sealed interface __NAME__Output {
    public data object Back : __NAME__Output

    public data class Selected(val id: String) : __NAME__Output
}
