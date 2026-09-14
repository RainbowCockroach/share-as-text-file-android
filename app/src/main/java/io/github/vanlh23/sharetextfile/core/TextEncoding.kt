package io.github.vanlh23.sharetextfile.core

object TextEncoding {
    val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun encode(text: String): ByteArray = UTF8_BOM + text.toByteArray(Charsets.UTF_8)
}
