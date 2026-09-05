package dev.mayankmkh.basekmpproject.foundation.runtime

/**
 * One structured log line: the event name followed by `key=value` fields. Values render with
 * `toString`, null as `null`, and are quoted when they contain whitespace, so every module that
 * logs a decision writes the same shape and a log search can key on any field.
 */
public fun logEvent(event: String, vararg fields: Pair<String, Any?>): String = buildString {
    append(event)
    for ((key, value) in fields) {
        append(' ').append(key).append('=')
        val rendered = value.toString()
        if (rendered.any(Char::isWhitespace)) append('"').append(rendered).append('"')
        else append(rendered)
    }
}

/**
 * The class of the failure underneath a wrapper such as a corruption exception, for a line that
 * must not quote messages because they may contain the bytes or values that failed.
 */
public val Throwable.causeClassName: String?
    get() = (cause ?: this)::class.simpleName
