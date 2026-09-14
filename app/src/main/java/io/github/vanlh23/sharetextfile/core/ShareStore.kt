package io.github.vanlh23.sharetextfile.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** Temporary share files, one `root/<uuid>/` directory per share. */
class ShareStore(private val root: File) {
    data class Entry(val id: String, val file: File)

    fun create(fileName: String, bytes: ByteArray): Entry {
        val id = UUID.randomUUID().toString()
        val dir = File(root, id)
        try {
            if (!dir.mkdirs()) throw IOException("Couldn't create directory $dir")
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            return Entry(id, file)
        } catch (e: IOException) {
            dir.deleteRecursively()
            throw e
        }
    }

    fun markChosen(id: String) {
        val dir = dirFor(id)?.takeIf { it.isDirectory } ?: return
        try {
            File(dir, CHOSEN_MARKER).createNewFile()
        } catch (_: IOException) {
            // The directory was deleted in the meantime, so there is nothing left to mark
        }
    }

    fun isChosen(id: String): Boolean = dirFor(id)?.let { File(it, CHOSEN_MARKER).exists() } ?: false

    fun delete(id: String) {
        dirFor(id)?.deleteRecursively()
    }

    fun sweep(nowMillis: Long, maxAgeMillis: Long) {
        val cutoff = nowMillis - maxAgeMillis
        root.listFiles()
            ?.filter { it.isDirectory && UUID_REGEX.matches(it.name) && it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
    }

    // Ids come from intents and WorkManager data; validating them rules out path traversal
    private fun dirFor(id: String): File? = if (UUID_REGEX.matches(id)) File(root, id) else null

    companion object {
        const val DIR_NAME = "share"
        private const val CHOSEN_MARKER = ".chosen"
        private val UUID_REGEX = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
