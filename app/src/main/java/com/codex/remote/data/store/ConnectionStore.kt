package com.codex.remote.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.codex.remote.data.security.SecretCipher
import com.codex.remote.domain.AuthType
import com.codex.remote.domain.ConnectionDraft
import com.codex.remote.domain.ConnectionSecrets
import com.codex.remote.domain.SavedConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

private val Context.connectionDataStore by preferencesDataStore(name = "codex_remote_connections")

class ConnectionStore(
    private val context: Context,
    private val cipher: SecretCipher = SecretCipher(),
) {
    private val key = stringPreferencesKey("connections_v1")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(SavedConnection.serializer())

    val connections: Flow<List<SavedConnection>> = context.connectionDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            preferences[key]?.let { encoded ->
                runCatching { json.decodeFromString(serializer, encoded) }.getOrDefault(emptyList())
            } ?: emptyList()
        }

    suspend fun save(draft: ConnectionDraft, original: SavedConnection? = null): SavedConnection {
        val port = draft.port.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: error("SSH 端口必须在 1 到 65535 之间")
        require(draft.name.isNotBlank()) { "请输入连接名称" }
        require(draft.host.isNotBlank()) { "请输入 SSH 主机" }
        require(draft.username.isNotBlank()) { "请输入用户名" }

        val saved = SavedConnection(
            id = draft.id ?: original?.id ?: UUID.randomUUID().toString(),
            name = draft.name.trim(),
            host = draft.host.trim(),
            port = port,
            username = draft.username.trim(),
            authType = draft.authType,
            encryptedPassword = encryptOrKeep(draft.password, original?.encryptedPassword),
            encryptedPrivateKey = encryptOrKeep(draft.privateKey, original?.encryptedPrivateKey),
            encryptedPassphrase = encryptOrKeep(draft.passphrase, original?.encryptedPassphrase),
            hostKeyFingerprint = if (draft.clearHostKeyFingerprint) ""
                else draft.hostKeyFingerprint.ifBlank { original?.hostKeyFingerprint.orEmpty() },
            platform = draft.platform,
            lastUsedAt = original?.lastUsedAt ?: 0,
        )
        if (saved.authType == AuthType.PASSWORD && saved.encryptedPassword.isBlank()) {
            error("请输入 SSH 密码")
        }
        if (saved.authType == AuthType.PRIVATE_KEY && saved.encryptedPrivateKey.isBlank()) {
            error("请粘贴 OpenSSH 或 PEM 私钥")
        }

        update { current -> current.filterNot { it.id == saved.id } + saved }
        return saved
    }

    suspend fun delete(id: String) = update { current -> current.filterNot { it.id == id } }

    suspend fun recordFingerprint(id: String, fingerprint: String) = update { current ->
        current.map { if (it.id == id) it.copy(hostKeyFingerprint = fingerprint) else it }
    }

    suspend fun recordUsed(id: String) = update { current ->
        current.map { if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it }
    }

    fun decrypt(connection: SavedConnection): ConnectionSecrets = ConnectionSecrets(
        password = cipher.decrypt(connection.encryptedPassword),
        privateKey = cipher.decrypt(connection.encryptedPrivateKey),
        passphrase = cipher.decrypt(connection.encryptedPassphrase),
    )

    private fun encryptOrKeep(plain: String, existing: String?): String =
        if (plain.isNotEmpty()) cipher.encrypt(plain) else existing.orEmpty()

    private suspend fun update(transform: (List<SavedConnection>) -> List<SavedConnection>) {
        context.connectionDataStore.edit { preferences ->
            val current = preferences[key]?.let {
                runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList())
            } ?: emptyList()
            preferences[key] = json.encodeToString(serializer, transform(current))
        }
    }
}
