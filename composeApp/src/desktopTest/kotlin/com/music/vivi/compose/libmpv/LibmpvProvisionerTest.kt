package com.music.vivi.compose.libmpv

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibmpvProvisionerTest {
    private fun tar(work: File, vararg args: String): File {
        val archive = File(work, "a.tar.gz")
        val p = ProcessBuilder(listOf("tar", "-czf", archive.path) + args).directory(work).redirectErrorStream(true).start()
        p.inputStream.readBytes()
        check(p.waitFor() == 0) { "tar failed" }
        return archive
    }

    @Test
    fun unpacksTheBundleFolderWithoutItsName() {
        val work = Files.createTempDirectory("libmpv-test").toFile()
        try {
            File(work, "libmpv").mkdirs()
            File(work, "libmpv/libmpv.so.2").writeText("lib")
            File(work, "libmpv/VERSION").writeText("v")
            val dest = File(work, "out").apply { mkdirs() }
            LibmpvProvisioner.extractTarGz(tar(work, "libmpv"), dest, rootFolder = "libmpv")
            assertEquals("lib", File(dest, "libmpv.so.2").readText())
            assertTrue(File(dest, "VERSION").isFile)
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun ignoresEntriesOutsideTheBundleFolder() {
        val work = Files.createTempDirectory("libmpv-test").toFile()
        try {
            File(work, "libmpv").mkdirs()
            File(work, "libmpv/libmpv.so.2").writeText("lib")
            File(work, "stray.txt").writeText("x")
            val dest = File(work, "out").apply { mkdirs() }
            runCatching { LibmpvProvisioner.extractTarGz(tar(work, "libmpv", "stray.txt"), dest, rootFolder = "libmpv") }
            assertFalse(File(dest, "stray.txt").exists())
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun rejectsPathTraversal() {
        val work = Files.createTempDirectory("libmpv-test").toFile()
        try {
            File(work, "libmpv").mkdirs()
            File(work, "evil.txt").writeText("x")
            val dest = File(work, "out").apply { mkdirs() }
            val archive = tar(work, "--transform", "s,evil.txt,libmpv/../../evil2.txt,", "evil.txt")
            runCatching { LibmpvProvisioner.extractTarGz(archive, dest, rootFolder = "libmpv") }
            assertFalse(File(work, "evil2.txt").exists())
        } finally {
            work.deleteRecursively()
        }
    }
}
