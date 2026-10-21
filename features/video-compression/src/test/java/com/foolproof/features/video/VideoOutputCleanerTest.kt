package com.foolproof.features.video

import com.foolproof.base.log.LogUtil
import com.foolproof.features.video.data.VideoOutputCleaner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VideoOutputCleanerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun cleaner(dir: File) = VideoOutputCleaner(dir, LogUtil(enabled = false))

    @Test
    fun `deletes leftover files from previous process`() {
        val dir = tempFolder.newFolder("videos")
        val a = File(dir, "a.mp4").apply { writeText("x") }
        val b = File(dir, "b.mp4").apply { writeText("y") }

        val deleted = cleaner(dir).cleanAll()

        assertEquals(2, deleted)
        assertFalse(a.exists())
        assertFalse(b.exists())
    }

    @Test
    fun `returns zero when directory is missing`() {
        val missing = File(tempFolder.root, "not-created")

        assertEquals(0, cleaner(missing).cleanAll())
    }

    @Test
    fun `returns zero when directory is already empty`() {
        val dir = tempFolder.newFolder("empty")

        assertEquals(0, cleaner(dir).cleanAll())
    }
}
