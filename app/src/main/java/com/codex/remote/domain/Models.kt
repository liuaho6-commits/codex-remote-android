package com.codex.remote.domain

import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.UUID

@Serializable
enum class AuthType { PASSWORD, PRIVATE_KEY }

@Serializable
enum class RemotePlatform { AUTO, POSIX, WINDOWS }

@Serializable
data class SavedConnection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType,
    val encryptedPassword: String = "",
    val encryptedPrivateKey: String = "",
    val encryptedPassphrase: String = "",
    val hostKeyFingerprint: String = "",
    val platform: RemotePlatform = RemotePlatform.AUTO,
    val lastUsedAt: Long = 0,
)

data class ConnectionDraft(
    val id: String? = null,
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    val hostKeyFingerprint: String = "",
    val clearHostKeyFingerprint: Boolean = false,
    val platform: RemotePlatform = RemotePlatform.AUTO,
)

internal enum class ConnectionDraftIssue {
    CONNECTION_NAME,
    HOST,
    PORT,
    USERNAME,
    PASSWORD,
    PRIVATE_KEY,
}

internal fun ConnectionDraft.validationIssues(existing: SavedConnection? = null): List<ConnectionDraftIssue> = buildList {
    if (name.isBlank()) add(ConnectionDraftIssue.CONNECTION_NAME)
    if (host.isBlank()) add(ConnectionDraftIssue.HOST)
    if (port.toIntOrNull()?.let { it in 1..65535 } != true) add(ConnectionDraftIssue.PORT)
    if (username.isBlank()) add(ConnectionDraftIssue.USERNAME)
    if (authType == AuthType.PASSWORD && password.isBlank() && existing?.encryptedPassword.isNullOrBlank()) {
        add(ConnectionDraftIssue.PASSWORD)
    }
    if (authType == AuthType.PRIVATE_KEY && privateKey.isBlank() && existing?.encryptedPrivateKey.isNullOrBlank()) {
        add(ConnectionDraftIssue.PRIVATE_KEY)
    }
}

data class ConnectionSecrets(
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
)

data class RemoteThread(
    val id: String,
    val title: String,
    val cwd: String,
    val updatedAt: Long,
    val status: String,
    val isPinned: Boolean = false,
)

data class RemoteProject(
    val id: String,
    val name: String,
    val path: String,
    val threads: List<RemoteThread>,
    val updatedAt: Long,
)

data class RemotePathEntry(
    val name: String,
    val isDirectory: Boolean,
    val isFile: Boolean,
)

data class RemoteSkill(
    val name: String,
    val displayName: String,
    val description: String,
    val path: String,
    val enabled: Boolean,
    val cwds: Set<String>,
)

data class RemotePlugin(
    val id: String,
    val name: String,
    val displayName: String,
    val description: String,
    val marketplace: String,
    val mentionPath: String,
    val enabled: Boolean,
)

enum class ComposerMentionKind { SKILL, PLUGIN }

data class ComposerMention(
    val kind: ComposerMentionKind,
    val name: String,
    val path: String,
    val token: String,
)

internal fun groupThreadsByProject(threads: List<RemoteThread>): List<RemoteProject> = threads
    .groupBy { remoteProjectIdentity(it.cwd) }
    .map { (identity, projectThreads) ->
        val sortedThreads = projectThreads.sortedWith(
            compareByDescending<RemoteThread> { it.isPinned }
                .thenByDescending { it.updatedAt }
                .thenBy { it.id },
        )
        val path = normalizeRemoteProjectPath(sortedThreads.firstOrNull()?.cwd.orEmpty())
        RemoteProject(
            id = identity,
            name = remoteProjectName(path),
            path = path,
            threads = sortedThreads,
            updatedAt = sortedThreads.maxOfOrNull { it.updatedAt } ?: 0,
        )
    }
    .sortedWith(compareByDescending<RemoteProject> { it.updatedAt }.thenBy { it.name.lowercase(Locale.ROOT) })

internal fun normalizeRemoteProjectPath(path: String): String {
    val trimmed = path.trim()
    if (trimmed.isEmpty() || trimmed == "/") return trimmed
    if (trimmed.length == 3 && trimmed[1] == ':' && (trimmed[2] == '\\' || trimmed[2] == '/')) {
        return trimmed
    }
    return trimmed.trimEnd('/', '\\')
}

private fun remoteProjectIdentity(path: String): String {
    val normalized = normalizeRemoteProjectPath(path)
    if (normalized.isEmpty()) return "unknown"
    val windowsPath = normalized.indexOf('\\') >= 0 || (normalized.length >= 2 && normalized[1] == ':')
    val identity = normalized.replace('\\', '/')
    return if (windowsPath) identity.lowercase(Locale.ROOT) else identity
}

private fun remoteProjectName(path: String): String {
    if (path.isEmpty()) return "Unknown workspace"
    if (path == "/" || (path.length == 3 && path[1] == ':')) return path
    val separator = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
    return path.substring(separator + 1).ifBlank { path }
}

data class RemoteModel(
    val id: String,
    val displayName: String,
    val description: String,
    val isDefault: Boolean,
    val hidden: Boolean = false,
    val supportedReasoningEfforts: List<ReasoningEffortOption> = emptyList(),
    val defaultReasoningEffort: String? = null,
    val inputModalities: Set<String> = emptySet(),
    val serviceTiers: List<RemoteServiceTier> = emptyList(),
    val defaultServiceTier: String? = null,
)

data class ReasoningEffortOption(
    val value: String,
    val description: String = "",
)

data class RemoteServiceTier(
    val id: String,
    val name: String,
    val description: String = "",
)

data class RemoteCollaborationMode(
    val name: String,
    val mode: String,
    val model: String? = null,
    val reasoningEffort: String? = null,
)

data class RemotePermissionProfile(
    val id: String,
    val description: String,
    val allowed: Boolean,
)

data class RemoteThreadSettingsSnapshot(
    val model: String?,
    val reasoningEffort: String?,
    val serviceTier: String?,
    val collaborationMode: String?,
    val permissionProfile: String?,
    val approvalPolicy: String?,
    val approvalsReviewer: String?,
)

data class ComposerImageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val mimeType: String,
    val dataUrl: String,
)

data class RemoteServerInfo(
    val userAgent: String,
    val codexHome: String,
    val platformFamily: String,
    val platformOs: String,
    val codexVersion: String,
)

data class RemoteAccount(
    val type: String?,
    val email: String?,
    val planType: String?,
    val requiresOpenaiAuth: Boolean,
) {
    val canRunCodex: Boolean get() = !requiresOpenaiAuth || type != null
}

data class RemoteDeviceLogin(
    val loginId: String,
    val verificationUrl: String,
    val userCode: String,
)

data class RemoteThreadSession(
    val timeline: List<TimelineItem>,
    val model: String?,
    val reasoningEffort: String?,
    val serviceTier: String?,
    val cwd: String,
    val olderHistoryCursor: String? = null,
    val collaborationMode: String? = null,
    val approvalPolicy: String? = null,
    val approvalsReviewer: String? = null,
    val permissionProfile: String? = null,
)

data class RemoteThreadHistoryPage(
    val timeline: List<TimelineItem>,
    val nextCursor: String?,
)

data class StartedRemoteThread(
    val id: String,
    val model: String?,
    val reasoningEffort: String?,
    val serviceTier: String?,
    val cwd: String,
)

data class ForkedRemoteThread(
    val thread: RemoteThread,
    val session: RemoteThreadSession,
)

enum class ReviewTargetKind { UNCOMMITTED_CHANGES, BASE_BRANCH, CUSTOM }

data class StartedRemoteReview(
    val turnId: String,
    val threadId: String,
)

data class RemoteMcpServerStatus(
    val name: String,
    val authStatus: String,
    val toolCount: Int,
    val resourceCount: Int,
)

data class RateLimitWindowSnapshot(
    val usedPercent: Double,
    val windowDurationMinutes: Long?,
    val resetsAt: Long?,
)

data class RemoteRateLimits(
    val limitName: String?,
    val planType: String?,
    val primary: RateLimitWindowSnapshot?,
    val secondary: RateLimitWindowSnapshot?,
    val creditsBalance: String?,
    val creditsUnlimited: Boolean,
)

data class RemoteThreadTokenUsage(
    val totalTokens: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val modelContextWindow: Long?,
)

enum class ThreadGoalStatus {
    ACTIVE,
    PAUSED,
    BLOCKED,
    USAGE_LIMITED,
    BUDGET_LIMITED,
    COMPLETE,
}

data class ThreadGoal(
    val threadId: String,
    val objective: String,
    val status: ThreadGoalStatus,
    val tokenBudget: Long?,
    val tokensUsed: Long,
    val timeUsedSeconds: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class TimelineKind {
    USER,
    AGENT,
    REASONING,
    PLAN,
    COMMAND,
    FILE_CHANGE,
    TOOL,
    REVIEW,
    COMPACTION,
    ERROR,
}

data class TimelineItem(
    val id: String,
    val kind: TimelineKind,
    val title: String = "",
    val body: String = "",
    val status: String = "",
    val expanded: Boolean = false,
    val fileChanges: List<FileChangeSummary> = emptyList(),
    val isGoal: Boolean = false,
)

data class FileChangeSummary(
    val path: String,
    val kind: String,
    val diff: String,
)

enum class ApprovalKind { COMMAND, FILE_CHANGE, PERMISSION, USER_INPUT, UNKNOWN }

enum class PermissionMode { ASK, AUTO_REVIEW, FULL_ACCESS, READ_ONLY }

data class ApprovalQuestion(
    val id: String,
    val header: String,
    val question: String,
    val options: List<String> = emptyList(),
)

data class ApprovalRequest(
    val requestId: String,
    val kind: ApprovalKind,
    val title: String,
    val detail: String,
    val rawMethod: String,
    val rawParams: String = "{}",
    val questions: List<ApprovalQuestion> = emptyList(),
)

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class AppUiState(
    val savedConnections: List<SavedConnection> = emptyList(),
    val activeConnection: SavedConnection? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionMessage: String = "",
    val threads: List<RemoteThread> = emptyList(),
    val archivedThreads: List<RemoteThread> = emptyList(),
    val isArchivedThreadsLoading: Boolean = false,
    val archivedThreadsError: String? = null,
    val projects: List<RemoteProject> = emptyList(),
    val skills: List<RemoteSkill> = emptyList(),
    val plugins: List<RemotePlugin> = emptyList(),
    val remoteDirectoryPath: String? = null,
    val remoteDirectoryEntries: List<RemotePathEntry> = emptyList(),
    val isRemoteDirectoryLoading: Boolean = false,
    val remoteDirectoryError: String? = null,
    val isComposerCatalogLoading: Boolean = false,
    val composerCatalogError: String? = null,
    val selectedProjectPath: String? = null,
    val selectedThreadId: String? = null,
    val threadGoal: ThreadGoal? = null,
    val isGoalLoading: Boolean = false,
    val goalError: String? = null,
    val timeline: List<TimelineItem> = emptyList(),
    val olderHistoryCursor: String? = null,
    val hasOlderHistory: Boolean = false,
    val isOlderHistoryLoading: Boolean = false,
    val olderHistoryError: String? = null,
    val consumedHistoryCursors: Set<String> = emptySet(),
    val models: List<RemoteModel> = emptyList(),
    val selectedModel: String? = null,
    val selectedReasoningEffort: String? = null,
    val selectedServiceTier: String? = null,
    val collaborationModes: List<RemoteCollaborationMode> = emptyList(),
    val selectedCollaborationMode: String = "default",
    val permissionProfiles: List<RemotePermissionProfile> = emptyList(),
    val selectedPermissionProfile: String? = null,
    val approvalsReviewer: String = "user",
    val remoteServer: RemoteServerInfo? = null,
    val remoteAccount: RemoteAccount? = null,
    val remoteDeviceLogin: RemoteDeviceLogin? = null,
    val isLoginStarting: Boolean = false,
    val mcpServers: List<RemoteMcpServerStatus> = emptyList(),
    val isMcpStatusLoading: Boolean = false,
    val mcpStatusError: String? = null,
    val isMcpLoginStarting: Boolean = false,
    val mcpAuthorizationUrl: String? = null,
    val isFeedbackSubmitting: Boolean = false,
    val feedbackError: String? = null,
    val rateLimits: RemoteRateLimits? = null,
    val threadTokenUsage: RemoteThreadTokenUsage? = null,
    val isStatusLoading: Boolean = false,
    val statusError: String? = null,
    val approvalPolicy: String = "on-request",
    val isTurnRunning: Boolean = false,
    val activeTurnId: String? = null,
    val pendingApproval: ApprovalRequest? = null,
    val pendingHostKeyFingerprint: String? = null,
    val isRestoringLastConnection: Boolean = true,
    val showConnections: Boolean = false,
    val showConnectionEditor: Boolean = false,
    val editingConnection: SavedConnection? = null,
    val isBusy: Boolean = false,
    val notice: String? = null,
)

internal fun AppUiState.withThreadRenamed(threadId: String, name: String): AppUiState {
    val renamed = threads.map { thread ->
        if (thread.id == threadId) thread.copy(title = name) else thread
    }
    return copy(threads = renamed, projects = groupThreadsByProject(renamed))
}

internal fun AppUiState.withThreadArchived(threadId: String): AppUiState {
    val remaining = threads.filterNot { it.id == threadId }
    val remainingProjects = groupThreadsByProject(remaining)
    val archivedSelectedThread = selectedThreadId == threadId
    val nextProjectPath = selectedProjectPath
        ?.takeIf { path -> remainingProjects.any { it.path == path } }
        ?: remainingProjects.firstOrNull()?.path
    return copy(
        threads = remaining,
        projects = remainingProjects,
        selectedProjectPath = nextProjectPath,
        selectedThreadId = selectedThreadId.takeUnless { archivedSelectedThread },
        threadGoal = if (archivedSelectedThread) null else threadGoal,
        isGoalLoading = if (archivedSelectedThread) false else isGoalLoading,
        goalError = if (archivedSelectedThread) null else goalError,
        threadTokenUsage = if (archivedSelectedThread) null else threadTokenUsage,
        timeline = if (archivedSelectedThread) emptyList() else timeline,
        olderHistoryCursor = if (archivedSelectedThread) null else olderHistoryCursor,
        hasOlderHistory = if (archivedSelectedThread) false else hasOlderHistory,
        isOlderHistoryLoading = if (archivedSelectedThread) false else isOlderHistoryLoading,
        olderHistoryError = if (archivedSelectedThread) null else olderHistoryError,
        consumedHistoryCursors = if (archivedSelectedThread) emptySet() else consumedHistoryCursors,
    )
}

internal fun mergeTimelineHistory(
    older: List<TimelineItem>,
    newer: List<TimelineItem>,
): List<TimelineItem> {
    val newerById = newer.associateBy(TimelineItem::id)
    val seen = mutableSetOf<String>()
    return buildList(older.size + newer.size) {
        older.forEach { item ->
            if (seen.add(item.id)) add(newerById[item.id] ?: item)
        }
        newer.forEach { item ->
            if (seen.add(item.id)) add(item)
        }
    }
}
