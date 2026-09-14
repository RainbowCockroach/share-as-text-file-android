package io.github.vanlh23.sharetextfile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SharedTextExtractorTest {
    @Test
    fun processTextWinsOverSendText() {
        assertEquals("selected", SharedTextExtractor.extract("selected", "shared", null)?.text)
    }

    @Test
    fun sendTextUsedWhenNoProcessText() {
        assertEquals("shared", SharedTextExtractor.extract(null, "shared", null)?.text)
    }

    @Test
    fun nothingToSave() {
        assertNull(SharedTextExtractor.extract(null, null, "Subject"))
        assertNull(SharedTextExtractor.extract("", null, null))
        assertNull(SharedTextExtractor.extract(null, "  \n\t ", null))
    }

    @Test
    fun textIsNotTrimmed() {
        assertEquals("  hi \n", SharedTextExtractor.extract(null, "  hi \n", null)?.text)
    }

    @Test
    fun styledCharSequenceBecomesPlainString() {
        val styled = StyledText("héllo 👋")
        val text = SharedTextExtractor.extract(styled, null, null)?.text
        assertEquals("héllo 👋", text)
        assertSame(String::class.java, text!!::class.java)
    }

    @Test
    fun blankSubjectDropped() {
        assertNull(SharedTextExtractor.extract(null, "text", "   ")?.subject)
        assertEquals("Title", SharedTextExtractor.extract(null, "text", "Title")?.subject)
    }

    /** Stands in for android.text.Spanned, which isn't available on the JVM. */
    private class StyledText(private val value: String) : CharSequence {
        override val length get() = value.length
        override fun get(index: Int) = value[index]
        override fun subSequence(startIndex: Int, endIndex: Int) = StyledText(value.substring(startIndex, endIndex))
        override fun toString() = value
    }
}
