package __PACKAGE__

import kotlin.jvm.JvmInline

/** Product models, not transport DTOs. Nothing here knows how the data is fetched or stored. */
@JvmInline public value class __NAME__Id(public val value: String)

public data class __NAME__Record(val id: __NAME__Id, val label: String)
