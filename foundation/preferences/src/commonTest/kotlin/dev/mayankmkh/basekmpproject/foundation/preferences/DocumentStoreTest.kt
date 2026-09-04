package dev.mayankmkh.basekmpproject.foundation.preferences

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class DocumentStoreTest {
    @Test
    fun `update returns and publishes the new document`() = runTest {
        val store = inMemoryDocumentStore(TestDocument(1))

        val updated = store.update { it.copy(count = it.count + 1) }

        assertEquals(TestDocument(2), updated)
        assertEquals(TestDocument(2), store.data.first())
    }

    private data class TestDocument(val count: Int)
}
