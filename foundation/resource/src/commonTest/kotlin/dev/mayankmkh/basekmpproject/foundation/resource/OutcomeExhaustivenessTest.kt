package dev.mayankmkh.basekmpproject.foundation.resource

import kotlin.test.Test
import kotlin.test.assertEquals

class OutcomeExhaustivenessTest {
    @Test
    fun `outcome and problem kind are exhaustive without else`() {
        val outcome = describe(Outcome.Completed(Unit))
        val kinds = ProblemKind.entries.map(::describe)

        assertEquals("completed", outcome)
        assertEquals(listOf("offline", "timeout", "server", "forbidden", "unexpected"), kinds)
    }

    private fun describe(outcome: Outcome<Unit>): String =
        when (outcome) {
            is Outcome.Completed -> "completed"
            is Outcome.Failed -> "failed"
        }

    private fun describe(kind: ProblemKind): String =
        when (kind) {
            ProblemKind.OFFLINE -> "offline"
            ProblemKind.TIMEOUT -> "timeout"
            ProblemKind.SERVER -> "server"
            ProblemKind.FORBIDDEN -> "forbidden"
            ProblemKind.UNEXPECTED -> "unexpected"
        }
}
