package io.github.vanlh23.sharetextfile.core

import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Builds `<stem>_<yyyy-MM-dd_HH-mm-ss>.txt`. Rules: ARCHITECTURE §5. */
object FileNamer {
    const val MAX_STEM_BYTES = 64
    private const val FALLBACK_STEM = "note"
    private const val ZWJ = 0x200D
    private const val RESERVED_CHARS = "\\/:*?\"<>|"

    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val LINE_BREAK = Regex("[\n\r\u2028\u2029]")

    fun fileName(shared: SharedText, now: LocalDateTime): String =
        "${truncate(chooseStem(shared))}_${TIMESTAMP.format(now)}.txt"

    private fun chooseStem(shared: SharedText): String {
        val subject = shared.subject?.let(::sanitize)?.takeIf { it.isNotEmpty() }
        if (subject != null && isSingleUrl(shared.text.trim())) return subject
        val firstLine = shared.text.split(LINE_BREAK).asSequence().map(::sanitize).firstOrNull { it.isNotEmpty() }
        return firstLine ?: subject ?: FALLBACK_STEM
    }

    private fun isSingleUrl(text: String): Boolean =
        text.none { it.isWhitespace() } &&
            (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true))

    internal fun sanitize(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFC)
        val out = StringBuilder(normalized.length)
        var lastWasSpace = false
        normalized.codePoints().forEach { cp ->
            if (isInvisibleFormatting(cp)) return@forEach
            val isSpace = isReplacedBySpace(cp) || Character.isWhitespace(cp) || Character.isSpaceChar(cp)
            if (isSpace) {
                if (!lastWasSpace) out.append(' ')
            } else {
                out.appendCodePoint(cp)
            }
            lastWasSpace = isSpace
        }
        return out.toString().trim { it == ' ' || it == '.' }
    }

    // BOM and bidi controls could disguise the real extension
    private fun isInvisibleFormatting(cp: Int): Boolean =
        cp == 0xFEFF || cp == 0x200E || cp == 0x200F || cp in 0x202A..0x202E || cp in 0x2066..0x2069

    // Unpaired surrogates can't be encoded in a file name, so they are treated like control characters
    private fun isReplacedBySpace(cp: Int): Boolean =
        Character.isISOControl(cp) ||
            cp in 0xD800..0xDFFF ||
            (cp < 0x80 && RESERVED_CHARS.indexOf(cp.toChar()) >= 0)

    /** Cuts [stem] to at most [MAX_STEM_BYTES] UTF-8 bytes without splitting a character sequence. */
    internal fun truncate(stem: String): String {
        val cps = stem.codePoints().toArray()
        var cut = 0
        var bytes = 0
        while (cut < cps.size && bytes + utf8Length(cps[cut]) <= MAX_STEM_BYTES) {
            bytes += utf8Length(cps[cut])
            cut++
        }
        if (cut < cps.size) {
            while (cut > 0 && (isJoinerOrExtender(cps[cut]) || cps[cut - 1] == ZWJ)) cut--
        }
        var trailingRegionalIndicators = 0
        while (trailingRegionalIndicators < cut && isRegionalIndicator(cps[cut - 1 - trailingRegionalIndicators])) {
            trailingRegionalIndicators++
        }
        if (trailingRegionalIndicators % 2 == 1) cut--

        val result = String(cps, 0, cut).trimEnd { it == ' ' || it == '.' }
        return result.ifEmpty { FALLBACK_STEM }
    }

    private fun isJoinerOrExtender(cp: Int): Boolean {
        val type = Character.getType(cp).toByte()
        return type == Character.NON_SPACING_MARK ||
            type == Character.ENCLOSING_MARK ||
            type == Character.COMBINING_SPACING_MARK ||
            cp == ZWJ ||
            cp in 0xFE00..0xFE0F ||
            cp in 0x1F3FB..0x1F3FF
    }

    private fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF

    private fun utf8Length(cp: Int): Int = when {
        cp < 0x80 -> 1
        cp < 0x800 -> 2
        cp < 0x10000 -> 3
        else -> 4
    }
}
