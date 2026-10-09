package dev.cyclone.cloak.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Reader

class ReadBoundedTest {
    /** A reader that hands out at most 7 characters per read, like a slow content provider. */
    private class Trickle(private val text: String) : Reader() {
        private var at = 0
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            if (at >= text.length) return -1
            val n = minOf(len, 7, text.length - at)
            text.toCharArray(cbuf, off, at, at + n)
            at += n
            return n
        }
        override fun close() {}
    }

    @Test
    fun readsEverythingEvenWhenTheSourceTrickles() {
        val text = "ro.product.model=Pixel 8\n".repeat(5_000)
        assertEquals(text, CloakViewModel.readBounded(Trickle(text)))
    }

    @Test
    fun refusesFilesOverTheLimit() {
        val error = runCatching { CloakViewModel.readBounded(Trickle("x".repeat(101)), limit = 100) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
