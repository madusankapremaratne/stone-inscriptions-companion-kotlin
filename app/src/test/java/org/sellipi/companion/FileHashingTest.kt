package org.sellipi.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.sellipi.companion.data.llm.FileHashing
import java.io.File

class FileHashingTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun sha256MatchesKnownVector() {
        val file = tmp.newFile("abc.txt").apply { writeText("abc") }
        // FIPS 180-2 test vector for "abc".
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", FileHashing.sha256(file))
    }

    @Test
    fun sha256ReportsProgressOverLargeFiles() {
        val file = tmp.newFile("big.bin").apply { writeBytes(ByteArray(3 * (1 shl 20) + 7)) }
        var last = 0L
        FileHashing.sha256(file) { last = it }
        assertEquals(file.length(), last)
    }

    @Test
    fun moveReplacesTargetAndRemovesSource() {
        val src = tmp.newFile("src.bin").apply { writeText("new") }
        val dst = File(tmp.newFolder("models"), "model.litertlm").apply { writeText("old") }
        FileHashing.moveReplacing(src, dst)
        assertFalse(src.exists())
        assertEquals("new", dst.readText())
        assertTrue(dst.parentFile!!.listFiles()!!.none { it.name.endsWith(".tmp") })
    }
}
