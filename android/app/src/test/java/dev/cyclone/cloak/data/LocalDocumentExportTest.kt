package dev.cyclone.cloak.data

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LocalDocumentExportTest {
    @Test fun completedWritePublishesOnlyAfterFullBytes() {
        val calls = mutableListOf<String>()
        val result = LocalDocumentExport.pending({ calls += "create"; "owned-file" }, { calls += "write" }, { calls += "publish" }, { calls += "discard" })
        assertEquals("owned-file", result)
        assertEquals(listOf("create", "write", "publish"), calls)
    }
    @Test fun partialWriteFailureDiscardsWithoutPublishing() {
        val calls = mutableListOf<String>()
        assertThrows(IOException::class.java) {
            LocalDocumentExport.pending({ "owned-file" }, { calls += "write"; throw IOException("partial write") }, { calls += "publish" }, { calls += "discard" })
        }
        assertEquals(listOf("write", "discard"), calls)
    }
    @Test fun publicationFailureDiscardsTheUncommittedFileAndKeepsOriginalError() {
        val error = IOException("commit failed")
        val caught = assertThrows(IOException::class.java) {
            LocalDocumentExport.pending({ "owned-file" }, {}, { throw error }, { throw IOException("cleanup failed") })
        }
        assertSame(error, caught)
        assertEquals(1, caught.suppressed.size)
    }
}
