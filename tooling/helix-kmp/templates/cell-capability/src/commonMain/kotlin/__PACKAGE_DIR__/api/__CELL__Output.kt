package __PACKAGE__.api

import __CAP_PACKAGE__.__CAP_NAME__Id

/** Navigation intents raised by the __CELL__ Cell; the host decides what they mean. */
public sealed interface __CELL__Output {
    public data object Back : __CELL__Output

    public data class Selected(public val id: __CAP_NAME__Id) : __CELL__Output
}
