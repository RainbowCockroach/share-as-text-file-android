package io.github.vanlh23.sharetextfile.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TextEncodingTest {
    private val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    @Test
    fun emptyTextIsJustTheBom() {
        assertArrayEquals(bom, TextEncoding.encode(""))
    }

    @Test
    fun singleAsciiChar() {
        assertArrayEquals(bom + byteArrayOf(0x61), TextEncoding.encode("a"))
    }

    @Test
    fun roundTripsEveryScript() {
        listOf(
            "Tiếng Việt có dấu",
            "中文日本語한국어",
            "مرحبا بالعالم",
            "👨‍👩‍👧 🇻🇳 👍🏽",
            "a\r\nb\nc",
        ).forEach { s ->
            val bytes = TextEncoding.encode(s)
            assertArrayEquals(bom, bytes.copyOfRange(0, 3))
            assertEquals(s, String(bytes, 3, bytes.size - 3, Charsets.UTF_8))
        }
    }

    @Test
    fun lineEndingsPreservedByteForByte() {
        assertArrayEquals(bom + byteArrayOf(0x61, 0x0D, 0x0A, 0x62, 0x0A, 0x63), TextEncoding.encode("a\r\nb\nc"))
    }
}
