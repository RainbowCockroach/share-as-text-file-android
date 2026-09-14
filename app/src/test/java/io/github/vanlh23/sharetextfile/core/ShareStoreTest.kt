package io.github.vanlh23.sharetextfile.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.UUID

class ShareStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var store: ShareStore

    @Before
    fun setUp() {
        root = File(tmp.root, "share")
        store = ShareStore(root)
    }

    @Test
    fun createWritesFileInUuidDir() {
        val bytes = byteArrayOf(1, 2, 3)
        val entry = store.create("name.txt", bytes)
        assertEquals(File(File(root, entry.id), "name.txt"), entry.file)
        assertArrayEquals(bytes, entry.file.readBytes())
        UUID.fromString(entry.id)
    }

    @Test
    fun sameNameGetsDifferentIds() {
        val a = store.create("same.txt", byteArrayOf(1))
        val b = store.create("same.txt", byteArrayOf(2))
        assertNotEquals(a.id, b.id)
        assertTrue(a.file.exists())
        assertTrue(b.file.exists())
    }

    @Test
    fun markChosen() {
        val entry = store.create("a.txt", byteArrayOf())
        assertFalse(store.isChosen(entry.id))
        store.markChosen(entry.id)
        assertTrue(store.isChosen(entry.id))
    }

    @Test
    fun deleteIsIdempotent() {
        val entry = store.create("a.txt", byteArrayOf())
        store.delete(entry.id)
        assertFalse(File(root, entry.id).exists())
        store.delete(entry.id)
    }

    @Test
    fun markChosenAfterDeleteDoesNotRecreateDir() {
        val entry = store.create("a.txt", byteArrayOf())
        store.delete(entry.id)
        store.markChosen(entry.id)
        assertFalse(File(root, entry.id).exists())
        assertFalse(store.isChosen(entry.id))
    }

    @Test
    fun invalidIdsAreIgnored() {
        root.mkdirs()
        val outside = File(tmp.root, "x").apply { mkdirs() }
        val sibling = File(root, "abc").apply { mkdirs() }
        listOf("../x", "", "abc", "../share", ".").forEach { id ->
            store.markChosen(id)
            assertFalse(store.isChosen(id))
            store.delete(id)
        }
        assertTrue(outside.exists())
        assertTrue(sibling.exists())
        assertTrue(root.exists())
        assertFalse(File(outside, ".chosen").exists())
    }

    @Test
    fun sweepDeletesOnlyOldUuidDirs() {
        val now = 10_000_000L
        val maxAge = 60_000L
        val old = store.create("old.txt", byteArrayOf())
        val fresh = store.create("new.txt", byteArrayOf())
        File(root, old.id).setLastModified(now - maxAge - 1)
        File(root, fresh.id).setLastModified(now - maxAge + 1_000)
        val stranger = File(root, "not-a-uuid").apply { mkdirs(); setLastModified(0) }

        store.sweep(now, maxAge)

        assertFalse(File(root, old.id).exists())
        assertTrue(fresh.file.exists())
        assertTrue(stranger.exists())
    }

    @Test
    fun sweepWithoutRootDoesNothing() {
        store.sweep(System.currentTimeMillis(), 0)
        assertFalse(root.exists())
    }

    @Test
    fun createFailureLeavesNothingBehind() {
        root.parentFile!!.mkdirs()
        root.writeText("I am a file, not a directory")
        try {
            store.create("a.txt", byteArrayOf(1))
            fail("Expected IOException")
        } catch (_: IOException) {
        }
        assertTrue(root.isFile)
    }

    @Test
    fun writeFailureRemovesTheDir() {
        root.mkdirs()
        try {
            // A name with a missing sub-directory makes the write itself fail after the dir exists
            store.create("missing/a.txt", byteArrayOf(1))
            fail("Expected IOException")
        } catch (_: IOException) {
        }
        assertEquals(0, root.listFiles()!!.size)
    }
}
