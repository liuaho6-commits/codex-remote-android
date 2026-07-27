package com.codex.remote.data.ssh

import android.content.Context
import com.codex.remote.domain.AuthType
import com.codex.remote.domain.ConnectionSecrets
import com.codex.remote.domain.RemotePlatform
import com.codex.remote.domain.SavedConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.concurrent.TimeUnit

class HostKeyChangedException(
    expected: String,
    actual: String,
) : SecurityException("SSH 主机密钥已变更。已保存 $expected，当前为 $actual")

class UnknownHostKeyException(val fingerprint: String) :
    SecurityException("首次连接需要确认 SSH 主机指纹：$fingerprint")

class RemoteCodexUnavailableException(message: String) : IllegalStateException(message)

class ActiveSshTransport internal constructor(
    private val ssh: SSHClient,
    private val session: Session,
    private val command: Session.Command,
    val fingerprint: String,
    val remotePlatform: RemotePlatform,
    val codexVersion: String,
) : Closeable {
    val reader: BufferedReader = BufferedReader(InputStreamReader(command.inputStream, Charsets.UTF_8))
    val writer: BufferedWriter = BufferedWriter(OutputStreamWriter(command.outputStream, Charsets.UTF_8))
    val errorReader: BufferedReader = BufferedReader(InputStreamReader(command.errorStream, Charsets.UTF_8))

    override fun close() {
        runCatching { writer.close() }
        runCatching { command.close() }
        runCatching { session.close() }
        runCatching { ssh.disconnect() }
        runCatching { ssh.close() }
    }
}

class SshAppServerTransportFactory(private val context: Context) {
    suspend fun open(
        connection: SavedConnection,
        secrets: ConnectionSecrets,
    ): ActiveSshTransport = withContext(Dispatchers.IO) {
        var observedFingerprint = ""
        val ssh = authenticatedClient(connection, secrets) { observedFingerprint = it }
        try {
            val remotePlatform = resolvePlatform(ssh, connection.platform)
            val codexVersion = readCodexVersion(ssh, remotePlatform)
            ssh.timeout = 0
            val session = ssh.startSession()
            try {
                val command = session.exec(appServerCommand(remotePlatform))
                ActiveSshTransport(
                    ssh = ssh,
                    session = session,
                    command = command,
                    fingerprint = observedFingerprint,
                    remotePlatform = remotePlatform,
                    codexVersion = codexVersion,
                )
            } catch (error: Throwable) {
                runCatching { session.close() }
                throw error
            }
        } catch (error: Throwable) {
            runCatching { ssh.disconnect() }
            runCatching { ssh.close() }
            throw error
        }
    }

    private fun authenticatedClient(
        connection: SavedConnection,
        secrets: ConnectionSecrets,
        onFingerprint: (String) -> Unit,
    ): SSHClient {
        val ssh = SSHClient(androidCompatibleSshConfig())
        var unknownFingerprint: String? = null
        var changedFingerprint: String? = null
        ssh.connectTimeout = 15_000
        ssh.timeout = 30_000
        ssh.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                val actual = sha256Fingerprint(key)
                onFingerprint(actual)
                val expected = connection.hostKeyFingerprint
                if (expected.isBlank()) {
                    unknownFingerprint = actual
                    return false
                }
                if (expected != actual) {
                    changedFingerprint = actual
                    return false
                }
                return true
            }

            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
        })

        try {
            ssh.connect(connection.host, connection.port)
            when (connection.authType) {
                AuthType.PASSWORD -> ssh.authPassword(connection.username, secrets.password)
                AuthType.PRIVATE_KEY -> authenticatePrivateKey(ssh, connection.username, secrets)
            }
            return ssh
        } catch (error: Throwable) {
            runCatching { ssh.disconnect() }
            runCatching { ssh.close() }
            unknownFingerprint?.let { throw UnknownHostKeyException(it) }
            changedFingerprint?.let { throw HostKeyChangedException(connection.hostKeyFingerprint, it) }
            throw error
        }
    }

    private fun authenticatePrivateKey(
        ssh: SSHClient,
        username: String,
        secrets: ConnectionSecrets,
    ) {
        val keyFile = File.createTempFile("codex_remote_", ".key", context.cacheDir)
        try {
            keyFile.writeText(secrets.privateKey, Charsets.UTF_8)
            val provider = if (secrets.passphrase.isBlank()) {
                ssh.loadKeys(keyFile.absolutePath)
            } else {
                ssh.loadKeys(keyFile.absolutePath, secrets.passphrase.toCharArray())
            }
            ssh.authPublickey(username, provider)
        } finally {
            keyFile.writeText("")
            keyFile.delete()
        }
    }

    private fun sha256Fingerprint(key: PublicKey): String {
        val wireKey = Buffer.PlainBuffer().putPublicKey(key).compactData
        val digest = MessageDigest.getInstance("SHA-256").digest(wireKey)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    private fun resolvePlatform(ssh: SSHClient, configured: RemotePlatform): RemotePlatform {
        if (configured != RemotePlatform.AUTO) return configured
        val probe = runCommand(ssh, "printf '__CODEX_POSIX__'")
        return if (probe.exitStatus == 0 && probe.stdout.contains("__CODEX_POSIX__")) {
            RemotePlatform.POSIX
        } else {
            RemotePlatform.WINDOWS
        }
    }

    private fun readCodexVersion(ssh: SSHClient, platform: RemotePlatform): String {
        val probe = runCommand(ssh, codexVersionCommand(platform))
        val version = probe.stdout.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("codex-cli ") || it.startsWith("codex ") }
        if (probe.exitStatus != 0 || version == null) {
            val detail = probe.stderr.lineSequence().lastOrNull { it.isNotBlank() }
                ?: probe.stdout.lineSequence().lastOrNull { it.isNotBlank() }
                ?: "codex --version 未返回版本"
            throw RemoteCodexUnavailableException(
                "远端登录 shell 找不到可用的 Codex CLI。请先在远端运行 codex --version 并完成安装。$detail",
            )
        }
        return version.substringAfter(' ').trim()
    }

    private fun runCommand(ssh: SSHClient, commandLine: String): ProbeResult {
        val session = ssh.startSession()
        return try {
            val command = session.exec(commandLine)
            try {
                command.join(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (command.isOpen) {
                    throw RemoteCodexUnavailableException("远端 Codex 预检超时")
                }
                ProbeResult(
                    exitStatus = command.exitStatus ?: -1,
                    stdout = command.inputStream.bufferedReader(Charsets.UTF_8).readText(),
                    stderr = command.errorStream.bufferedReader(Charsets.UTF_8).readText(),
                )
            } finally {
                runCatching { command.close() }
            }
        } finally {
            runCatching { session.close() }
        }
    }

    private data class ProbeResult(
        val exitStatus: Int,
        val stdout: String,
        val stderr: String,
    )

    companion object {
        private const val PROBE_TIMEOUT_SECONDS = 15L
    }
}

internal fun codexVersionCommand(platform: RemotePlatform): String = when (platform) {
    RemotePlatform.AUTO -> error("AUTO platform must be resolved before building a Codex command")
    RemotePlatform.POSIX -> "exec \"\${SHELL:-/bin/sh}\" -lc 'codex --version'"
    RemotePlatform.WINDOWS ->
        "powershell.exe -NoLogo -NonInteractive -Command \"& { codex --version }\""
}

internal fun appServerCommand(platform: RemotePlatform): String = when (platform) {
    RemotePlatform.AUTO -> error("AUTO platform must be resolved before building a Codex command")
    RemotePlatform.POSIX ->
        "exec \"\${SHELL:-/bin/sh}\" -lc 'exec codex app-server --listen stdio://'"
    RemotePlatform.WINDOWS ->
        "powershell.exe -NoLogo -NonInteractive -Command \"& { codex app-server --listen stdio:// }\""
}

internal fun androidCompatibleSshConfig() = DefaultSecurityProviderConfig().apply {
    keyExchangeFactories = keyExchangeFactories.filterNot {
        it.name.contains("curve25519", ignoreCase = true)
    }
}
