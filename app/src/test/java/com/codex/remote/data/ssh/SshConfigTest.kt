package com.codex.remote.data.ssh

import com.codex.remote.domain.RemotePlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshConfigTest {
    @Test
    fun excludesCurve25519ThatAndroidCannotInstantiate() {
        val names = androidCompatibleSshConfig().keyExchangeFactories.map { it.name }

        assertFalse(names.any { it.contains("curve25519", ignoreCase = true) })
        assertTrue(names.any { it.contains("ecdh", ignoreCase = true) || it.contains("group14", ignoreCase = true) })
    }

    @Test
    fun posixCommandsUseTheRemoteLoginShell() {
        assertEquals(
            "exec \"\${SHELL:-/bin/sh}\" -lc 'codex --version'",
            codexVersionCommand(RemotePlatform.POSIX),
        )
        assertTrue(appServerCommand(RemotePlatform.POSIX).contains("-lc 'exec codex app-server"))
    }

    @Test
    fun windowsCommandAllowsTheRemotePowerShellProfile() {
        val command = appServerCommand(RemotePlatform.WINDOWS)

        assertTrue(command.contains("powershell.exe"))
        assertFalse(command.contains("-NoProfile"))
        assertTrue(command.contains("codex app-server --listen stdio://"))
    }
}
