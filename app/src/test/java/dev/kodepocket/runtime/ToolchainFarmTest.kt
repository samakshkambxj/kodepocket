package dev.kodepocket.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolchainFarmTest {

    private val manifest = Toolchain.Manifest(
        version = "test",
        bin = mapOf(
            "bash" to "libkp_bash.so",
            "busybox" to "libkp_busybox.so",
            "git" to "libkp_git.so",
        ),
        lib = mapOf(
            "libz.so.1" to "libz.so",
            "libcrypto.so.3" to "libcrypto.so",
        ),
        gitExec = mapOf(
            "git-add" to "libkp_git.so",
            "git-remote-https" to "libkp_git_remote_http.so",
        ),
        applets = listOf("awk", "sed", "git"),
    )

    @Test
    fun linksEveryLibraryByItsSoname() {
        val libs = planFarm(manifest).filter { it.farm == Farm.Lib }
        assertEquals(
            setOf("libz.so.1" to "libz.so", "libcrypto.so.3" to "libcrypto.so"),
            libs.map { it.name to it.lib }.toSet(),
        )
    }

    @Test
    fun letsRealToolsWinOverBusyboxApplets() {
        val bin = planFarm(manifest).filter { it.farm == Farm.Bin }
        val git = bin.single { it.name == "git" }
        assertEquals("libkp_git.so", git.lib)
        assertEquals(1, bin.count { it.name == "git" })
    }

    @Test
    fun pointsAppletsAtTheBusyboxBinary() {
        val bin = planFarm(manifest).filter { it.farm == Farm.Bin }
        assertEquals("libkp_busybox.so", bin.single { it.name == "awk" }.lib)
        assertEquals("libkp_busybox.so", bin.single { it.name == "sed" }.lib)
    }

    @Test
    fun keepsGitSubcommandsInTheExecPathFarm() {
        val exec = planFarm(manifest).filter { it.farm == Farm.GitExec }
        assertTrue(exec.any { it.name == "git-add" && it.lib == "libkp_git.so" })
        assertTrue(
            exec.any { it.name == "git-remote-https" && it.lib == "libkp_git_remote_http.so" },
        )
        assertFalse(exec.any { it.farm == Farm.Bin })
    }
}
