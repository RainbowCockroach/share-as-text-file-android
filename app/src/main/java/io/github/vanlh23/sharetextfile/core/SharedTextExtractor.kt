package io.github.vanlh23.sharetextfile.core

data class SharedText(val text: String, val subject: String?)

object SharedTextExtractor {
    /** Returns null when there is nothing to save. The text is never trimmed or otherwise changed. */
    fun extract(processText: CharSequence?, sendText: CharSequence?, subject: CharSequence?): SharedText? {
        val text = (processText ?: sendText)?.toString()
        if (text.isNullOrBlank()) return null
        return SharedText(text, subject?.toString()?.takeIf { it.isNotBlank() })
    }
}
