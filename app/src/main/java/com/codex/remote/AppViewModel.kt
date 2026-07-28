package com.codex.remote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codex.remote.data.rpc.AppServerEvent
import com.codex.remote.data.rpc.CodexRpcClient
import com.codex.remote.data.ssh.SshAppServerTransportFactory
import com.codex.remote.data.ssh.UnknownHostKeyException
import com.codex.remote.data.store.ConnectionStore
import com.codex.remote.domain.AppUiState
import com.codex.remote.domain.ApprovalKind
import com.codex.remote.domain.ConnectionDraft
import com.codex.remote.domain.ConnectionStatus
import com.codex.remote.domain.ComposerMention
import com.codex.remote.domain.ComposerMentionKind
import com.codex.remote.domain.ComposerImageAttachment
import com.codex.remote.domain.PermissionMode
import com.codex.remote.domain.RemoteCollaborationMode
import com.codex.remote.domain.RemoteProject
import com.codex.remote.domain.RemoteAccount
import com.codex.remote.domain.RemoteModel
import com.codex.remote.domain.RemotePathEntry
import com.codex.remote.domain.RemoteServerInfo
import com.codex.remote.domain.RemoteThread
import com.codex.remote.domain.ReviewTargetKind
import com.codex.remote.domain.SavedConnection
import com.codex.remote.domain.TimelineItem
import com.codex.remote.domain.TimelineKind
import com.codex.remote.domain.ThreadGoalStatus
import com.codex.remote.domain.groupThreadsByProject
import com.codex.remote.domain.mergeTimelineHistory
import com.codex.remote.domain.composerToken
import com.codex.remote.domain.containsComposerToken
import com.codex.remote.domain.withThreadArchived
import com.codex.remote.domain.withThreadRenamed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ConnectionStore(application)
    private val transportFactory = SshAppServerTransportFactory(application)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var rpc: CodexRpcClient? = null
    private var eventJob: Job? = null
    private var didRestoreLastConnection = false

    init {
        viewModelScope.launch {
            store.connections.collect { connections ->
                val sortedConnections = connections.sortedByDescending { it.lastUsedAt }
                val connectionToRestore = if (!didRestoreLastConnection) {
                    didRestoreLastConnection = true
                    sortedConnections.lastUsedConnectionOrNull()
                } else {
                    null
                }
                val restoreConnection = connectionToRestore?.takeIf { _state.value.activeConnection == null }
                _state.update { current ->
                    val refreshedActive = current.activeConnection?.let { active ->
                        connections.firstOrNull { it.id == active.id } ?: active
                    }
                    current.copy(
                        savedConnections = sortedConnections,
                        activeConnection = restoreConnection ?: refreshedActive,
                        connectionStatus = if (restoreConnection != null) ConnectionStatus.CONNECTING else current.connectionStatus,
                        connectionMessage = if (restoreConnection != null) {
                            "正在连接 ${restoreConnection.host}…"
                        } else {
                            current.connectionMessage
                        },
                        isRestoringLastConnection = false,
                    )
                }
                restoreConnection?.let(::connect)
            }
        }
    }

    fun showConnections(show: Boolean = true) = _state.update {
        it.copy(showConnections = show, showConnectionEditor = false, editingConnection = null)
    }

    fun editConnection(connection: SavedConnection? = null) = _state.update {
        it.copy(showConnections = true, showConnectionEditor = true, editingConnection = connection, notice = null)
    }

    fun closeEditor() = _state.update { it.copy(showConnectionEditor = false, editingConnection = null) }

    fun saveConnection(draft: ConnectionDraft, connectAfterSave: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, notice = null) }
            runCatching {
                store.save(draft, _state.value.editingConnection)
            }.onSuccess { saved ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        showConnectionEditor = false,
                        editingConnection = null,
                        showConnections = !connectAfterSave,
                    )
                }
                if (connectAfterSave) connect(saved)
            }.onFailure(::showError)
        }
    }

    fun deleteConnection(connection: SavedConnection) {
        viewModelScope.launch {
            if (_state.value.activeConnection?.id == connection.id) disconnect()
            store.delete(connection.id)
        }
    }

    fun connect(connection: SavedConnection) {
        viewModelScope.launch {
            disconnectInternal(clearActive = false)
            _state.update {
                it.copy(
                    activeConnection = connection,
                    connectionStatus = ConnectionStatus.CONNECTING,
                    connectionMessage = "正在连接 ${connection.host}…",
                    showConnections = false,
                    timeline = emptyList(),
                    olderHistoryCursor = null,
                    hasOlderHistory = false,
                    isOlderHistoryLoading = false,
                    olderHistoryError = null,
                    consumedHistoryCursors = emptySet(),
                    threads = emptyList(),
                    archivedThreads = emptyList(),
                    isArchivedThreadsLoading = false,
                    archivedThreadsError = null,
                    projects = emptyList(),
                    skills = emptyList(),
                    plugins = emptyList(),
                    isComposerCatalogLoading = true,
                    composerCatalogError = null,
                    selectedProjectPath = null,
                    selectedThreadId = null,
                    threadGoal = null,
                    isGoalLoading = false,
                    goalError = null,
                    models = emptyList(),
                    selectedModel = null,
                    selectedReasoningEffort = null,
                    selectedServiceTier = null,
                    collaborationModes = emptyList(),
                    selectedCollaborationMode = "default",
                    permissionProfiles = emptyList(),
                    selectedPermissionProfile = null,
                    approvalsReviewer = "user",
                    remoteServer = null,
                    remoteAccount = null,
                    remoteDeviceLogin = null,
                    isLoginStarting = false,
                    mcpServers = emptyList(),
                    isMcpStatusLoading = false,
                    mcpStatusError = null,
                    isMcpLoginStarting = false,
                    mcpAuthorizationUrl = null,
                    isFeedbackSubmitting = false,
                    feedbackError = null,
                    rateLimits = null,
                    threadTokenUsage = null,
                    isStatusLoading = false,
                    statusError = null,
                    pendingHostKeyFingerprint = null,
                    notice = null,
                )
            }
            runCatching {
                val secrets = withContext(Dispatchers.IO) { store.decrypt(connection) }
                val transport = transportFactory.open(connection, secrets)
                val client = CodexRpcClient(transport)
                rpc = client
                observeEvents(client)
                val server = withTimeout(20_000) { client.initialize() }
                val account = withTimeout(20_000) { client.readAccount() }
                val models = withTimeout(30_000) { client.listModels() }
                val threads = withTimeout(60_000) { client.listThreads() }
                val collaborationModes = runCatching {
                    withTimeout(20_000) { client.listCollaborationModes() }
                }.getOrDefault(emptyList())
                val permissionProfiles = runCatching {
                    withTimeout(20_000) { client.listPermissionProfiles(null) }
                }.getOrDefault(emptyList())
                store.recordUsed(connection.id)
                ConnectionBootstrap(server, account, models, threads, collaborationModes, permissionProfiles)
            }.onSuccess { bootstrap ->
                val projects = groupThreadsByProject(bootstrap.threads)
                val selectedModel = bootstrap.models.firstOrNull { model -> model.isDefault }
                    ?: bootstrap.models.firstOrNull()
                _state.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.CONNECTED,
                        connectionMessage = connectionSummary(
                            projects.size,
                            bootstrap.threads.size,
                            bootstrap.server.codexVersion,
                        ),
                        models = bootstrap.models,
                        selectedModel = selectedModel?.id,
                        selectedReasoningEffort = selectedModel?.preferredReasoningEffort(),
                        selectedServiceTier = selectedModel?.defaultServiceTier,
                        collaborationModes = bootstrap.collaborationModes,
                        selectedCollaborationMode = bootstrap.collaborationModes
                            .firstOrNull { mode -> mode.mode == "default" }?.mode ?: "default",
                        permissionProfiles = bootstrap.permissionProfiles,
                        selectedPermissionProfile = null,
                        remoteServer = bootstrap.server,
                        remoteAccount = bootstrap.account,
                        threads = bootstrap.threads,
                        projects = projects,
                        skills = emptyList(),
                        plugins = emptyList(),
                        isComposerCatalogLoading = true,
                        composerCatalogError = null,
                        selectedProjectPath = projects.firstOrNull()?.path,
                    )
                }
                refreshComposerCatalog()
            }.onFailure { error ->
                rpc?.close()
                rpc = null
                val unknownHostKey = generateSequence(error) { it.cause }
                    .filterIsInstance<UnknownHostKeyException>()
                    .firstOrNull()
                if (unknownHostKey != null) {
                    _state.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.ERROR,
                            connectionMessage = "请先确认 SSH 主机指纹",
                            pendingHostKeyFingerprint = unknownHostKey.fingerprint,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.ERROR,
                            connectionMessage = friendlyError(error),
                            notice = friendlyError(error),
                        )
                    }
                }
            }
        }
    }

    fun trustPendingHostKey() {
        val connection = _state.value.activeConnection ?: return
        val fingerprint = _state.value.pendingHostKeyFingerprint ?: return
        viewModelScope.launch {
            store.recordFingerprint(connection.id, fingerprint)
            _state.update { it.copy(pendingHostKeyFingerprint = null) }
            connect(connection.copy(hostKeyFingerprint = fingerprint))
        }
    }

    fun rejectPendingHostKey() = _state.update {
        it.copy(
            pendingHostKeyFingerprint = null,
            connectionStatus = ConnectionStatus.ERROR,
            connectionMessage = "已取消未验证主机的连接",
        )
    }

    fun disconnect() {
        viewModelScope.launch { disconnectInternal(clearActive = true) }
    }

    private fun disconnectInternal(clearActive: Boolean) {
        eventJob?.cancel()
        eventJob = null
        rpc?.close()
        rpc = null
        _state.update {
            it.copy(
                activeConnection = if (clearActive) null else it.activeConnection,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                connectionMessage = "",
                threads = if (clearActive) emptyList() else it.threads,
                archivedThreads = if (clearActive) emptyList() else it.archivedThreads,
                isArchivedThreadsLoading = false,
                archivedThreadsError = null,
                projects = if (clearActive) emptyList() else it.projects,
                selectedProjectPath = if (clearActive) null else it.selectedProjectPath,
                selectedThreadId = null,
                threadGoal = null,
                isGoalLoading = false,
                goalError = null,
                timeline = emptyList(),
                olderHistoryCursor = null,
                hasOlderHistory = false,
                isOlderHistoryLoading = false,
                olderHistoryError = null,
                consumedHistoryCursors = emptySet(),
                models = emptyList(),
                selectedModel = null,
                selectedReasoningEffort = null,
                selectedServiceTier = null,
                collaborationModes = emptyList(),
                selectedCollaborationMode = "default",
                permissionProfiles = emptyList(),
                selectedPermissionProfile = null,
                approvalsReviewer = "user",
                remoteServer = null,
                remoteAccount = null,
                remoteDeviceLogin = null,
                isLoginStarting = false,
                mcpServers = emptyList(),
                isMcpStatusLoading = false,
                mcpStatusError = null,
                isMcpLoginStarting = false,
                mcpAuthorizationUrl = null,
                isFeedbackSubmitting = false,
                feedbackError = null,
                rateLimits = null,
                threadTokenUsage = null,
                isStatusLoading = false,
                statusError = null,
                isTurnRunning = false,
                activeTurnId = null,
                pendingApproval = null,
                pendingHostKeyFingerprint = null,
            )
        }
    }

    fun newThread() {
        _state.update { state ->
            val projectPath = state.selectedProjectPath ?: state.projects.firstOrNull()?.path
            if (state.remoteAccount?.canRunCodex != true) {
                state.copy(notice = "请先登录远端 Codex")
            } else if (projectPath.isNullOrBlank()) {
                state.copy(notice = "远端没有可用于新会话的 Codex 项目")
            } else {
                state.copy(
                    selectedProjectPath = projectPath,
                    selectedThreadId = null,
                    threadGoal = null,
                    isGoalLoading = false,
                    goalError = null,
                    threadTokenUsage = null,
                    timeline = emptyList(),
                    olderHistoryCursor = null,
                    hasOlderHistory = false,
                    isOlderHistoryLoading = false,
                    olderHistoryError = null,
                    consumedHistoryCursors = emptySet(),
                    selectedCollaborationMode = state.defaultCollaborationMode(),
                )
            }
        }
    }

    fun selectProject(project: RemoteProject) {
        _state.update {
            it.copy(
                selectedProjectPath = project.path,
                selectedThreadId = null,
                threadGoal = null,
                isGoalLoading = false,
                goalError = null,
                threadTokenUsage = null,
                timeline = emptyList(),
                olderHistoryCursor = null,
                hasOlderHistory = false,
                isOlderHistoryLoading = false,
                olderHistoryError = null,
                consumedHistoryCursors = emptySet(),
                selectedCollaborationMode = it.defaultCollaborationMode(),
            )
        }
    }

    fun selectThread(thread: RemoteThread) {
        if (_state.value.activeConnection == null) return
        val client = rpc ?: return
        viewModelScope.launch {
            val projectPath = _state.value.projects
                .firstOrNull { project -> project.threads.any { it.id == thread.id } }
                ?.path
                ?: thread.cwd
            _state.update {
                it.copy(
                    selectedProjectPath = projectPath,
                    selectedThreadId = thread.id,
                    threadGoal = null,
                    isGoalLoading = true,
                    goalError = null,
                    threadTokenUsage = null,
                    timeline = emptyList(),
                    olderHistoryCursor = null,
                    hasOlderHistory = false,
                    isOlderHistoryLoading = false,
                    olderHistoryError = null,
                    consumedHistoryCursors = emptySet(),
                    isBusy = true,
                    isTurnRunning = thread.status.isRemoteThreadActive(),
                    activeTurnId = null,
                )
            }
            val session = runCatching { client.resumeThread(thread.id, thread.cwd) }
                .getOrElse { error ->
                    _state.update { state ->
                        if (state.selectedThreadId == thread.id) state.copy(isGoalLoading = false) else state
                    }
                    showError(error)
                    return@launch
                }
            _state.update { state ->
                if (state.selectedThreadId != thread.id) return@update state
                val model = state.models.firstOrNull { it.id == session.model }
                state.copy(
                    timeline = mergeTimelineHistory(session.timeline, state.timeline),
                    olderHistoryCursor = session.olderHistoryCursor,
                    hasOlderHistory = session.olderHistoryCursor != null,
                    isOlderHistoryLoading = false,
                    olderHistoryError = null,
                    consumedHistoryCursors = emptySet(),
                    isBusy = false,
                    selectedModel = model?.id ?: state.selectedModel,
                    selectedReasoningEffort = session.reasoningEffort
                        ?.takeIf { effort -> model?.supports(effort) == true }
                        ?: model?.preferredReasoningEffort()
                        ?: state.selectedReasoningEffort,
                    selectedServiceTier = session.serviceTier
                        ?.takeIf { tier -> model?.serviceTiers?.any { it.id == tier } == true }
                        ?: model?.defaultServiceTier,
                    selectedCollaborationMode = session.collaborationMode
                        ?.takeIf { mode -> state.collaborationModes.any { it.mode == mode } }
                        ?: state.defaultCollaborationMode(),
                    selectedPermissionProfile = session.permissionProfile,
                    approvalPolicy = session.approvalPolicy ?: state.approvalPolicy,
                    approvalsReviewer = session.approvalsReviewer ?: state.approvalsReviewer,
                )
            }
            runCatching { client.getThreadGoal(thread.id) }
                .onSuccess { goal ->
                    _state.update { state ->
                        if (state.selectedThreadId == thread.id) {
                            state.copy(
                                threadGoal = goal,
                                isGoalLoading = false,
                                goalError = null,
                            )
                        } else {
                            state
                        }
                    }
                }
                .onFailure { error ->
                    _state.update { state ->
                        if (state.selectedThreadId == thread.id) {
                            if (error.isUnsupportedRpcMethod("thread/goal/get")) {
                                state.copy(
                                    threadGoal = null,
                                    isGoalLoading = false,
                                    goalError = null,
                                )
                            } else {
                                state.copy(
                                    threadGoal = null,
                                    isGoalLoading = false,
                                    goalError = friendlyGoalError(error),
                                )
                            }
                        } else {
                            state
                        }
                    }
                }
        }
    }

    fun loadOlderHistory() {
        val client = rpc ?: return
        val snapshot = _state.value
        val threadId = snapshot.selectedThreadId ?: return
        val cursor = snapshot.olderHistoryCursor ?: return
        if (!snapshot.hasOlderHistory || snapshot.isOlderHistoryLoading || snapshot.isBusy) return
        if (cursor in snapshot.consumedHistoryCursors) {
            _state.update {
                it.copy(
                    olderHistoryCursor = null,
                    hasOlderHistory = false,
                    olderHistoryError = "远端返回了重复的历史游标，已停止继续加载",
                )
            }
            return
        }

        _state.update { state ->
            if (state.selectedThreadId == threadId && state.olderHistoryCursor == cursor) {
                state.copy(isOlderHistoryLoading = true, olderHistoryError = null)
            } else {
                state
            }
        }
        viewModelScope.launch {
            val consumedCursors = snapshot.consumedHistoryCursors + cursor
            runCatching {
                client.loadOlderThreadHistory(threadId, cursor).let { page ->
                    page.copy(
                        nextCursor = CodexRpcClient.checkedNextHistoryCursor(
                            returnedCursor = page.nextCursor,
                            consumedCursors = consumedCursors,
                        ),
                    )
                }
            }.onSuccess { page ->
                _state.update { state ->
                    if (state.selectedThreadId != threadId || state.olderHistoryCursor != cursor) {
                        return@update state
                    }
                    state.copy(
                        timeline = mergeTimelineHistory(page.timeline, state.timeline),
                        olderHistoryCursor = page.nextCursor,
                        hasOlderHistory = page.nextCursor != null,
                        isOlderHistoryLoading = false,
                        olderHistoryError = null,
                        consumedHistoryCursors = consumedCursors,
                    )
                }
            }.onFailure { error ->
                val repeatedCursor = error.message.orEmpty().contains("nextCursor", ignoreCase = true)
                _state.update { state ->
                    if (state.selectedThreadId != threadId || state.olderHistoryCursor != cursor) {
                        return@update state
                    }
                    state.copy(
                        olderHistoryCursor = if (repeatedCursor) null else state.olderHistoryCursor,
                        hasOlderHistory = if (repeatedCursor) false else state.hasOlderHistory,
                        isOlderHistoryLoading = false,
                        olderHistoryError = friendlyError(error),
                        consumedHistoryCursors = consumedCursors,
                    )
                }
            }
        }
    }

    fun sendMessage(
        text: String,
        selectedMentions: List<ComposerMention> = emptyList(),
        attachments: List<ComposerImageAttachment> = emptyList(),
        asGoal: Boolean = false,
    ) {
        val prompt = text.trim()
        if (prompt.isEmpty() && attachments.isEmpty()) return
        if (_state.value.activeConnection == null) return
        val client = rpc ?: return
        val currentState = _state.value
        if (currentState.remoteAccount?.canRunCodex != true) {
            _state.update { it.copy(notice = "远端 Codex 尚未登录") }
            return
        }
        if (asGoal && prompt.isEmpty()) {
            _state.update { it.copy(notice = "Goal 需要包含文字目标") }
            return
        }
        if (asGoal && currentState.isTurnRunning) {
            _state.update { it.copy(notice = "当前任务运行期间不能设置 Goal") }
            return
        }
        val selectedModel = currentState.selectedModel
        if (selectedModel == null) {
            _state.update { it.copy(notice = "远端没有返回可用模型") }
            return
        }
        val selectedReasoningEffort = currentState.selectedReasoningEffort
        val selectedServiceTier = currentState.selectedServiceTier
        val approvalPolicy = currentState.approvalPolicy
        val approvalsReviewer = currentState.approvalsReviewer
        val permissionProfile = currentState.selectedPermissionProfile
        val collaborationMode = currentState.collaborationModes
            .firstOrNull { it.mode == currentState.selectedCollaborationMode }
        val cwd = currentState.threads.firstOrNull { it.id == currentState.selectedThreadId }
            ?.cwd
            ?.takeIf { it.isNotBlank() }
            ?: currentState.selectedProjectPath?.takeIf { it.isNotBlank() }
        if (cwd == null) {
            _state.update { it.copy(notice = "请先选择一个远端项目") }
            return
        }
        val mentions = resolveComposerMentions(prompt, cwd, selectedMentions, currentState)
        val steeringThreadId = currentState.selectedThreadId.takeIf { currentState.isTurnRunning }
        val steeringTurnId = currentState.activeTurnId.takeIf { currentState.isTurnRunning }
        if (currentState.isTurnRunning && (steeringThreadId == null || steeringTurnId == null)) {
            _state.update { it.copy(notice = "正在恢复运行中的任务，请等远端 turn id 同步后再追加消息") }
            return
        }
        viewModelScope.launch {
            val localItemId = "local-${UUID.randomUUID()}"
            val userItem = TimelineItem(
                id = localItemId,
                kind = TimelineKind.USER,
                body = buildString {
                    append(prompt)
                    attachments.forEach { attachment ->
                        if (isNotEmpty()) append('\n')
                        append("[Image: ${attachment.displayName}]")
                    }
                },
                isGoal = asGoal,
            )
            _state.update {
                it.copy(
                    timeline = it.timeline + userItem,
                    isTurnRunning = if (asGoal) it.isTurnRunning else true,
                    isGoalLoading = if (asGoal) true else it.isGoalLoading,
                    goalError = if (asGoal) null else it.goalError,
                    notice = null,
                )
            }
            runCatching {
                if (steeringThreadId != null && steeringTurnId != null) {
                    client.steerTurn(
                        threadId = steeringThreadId,
                        expectedTurnId = steeringTurnId,
                        text = prompt,
                        mentions = mentions,
                        attachments = attachments,
                    )
                    return@runCatching
                }
                val threadId = _state.value.selectedThreadId ?: client.startThread(
                    cwd = cwd,
                    model = selectedModel,
                    serviceTier = selectedServiceTier,
                    approvalPolicy = approvalPolicy,
                    approvalsReviewer = approvalsReviewer,
                    permissionProfile = permissionProfile,
                ).let { started ->
                    _state.update {
                        it.copy(
                            selectedThreadId = started.id,
                            selectedModel = started.model ?: it.selectedModel,
                            selectedReasoningEffort = selectedReasoningEffort
                                ?: started.reasoningEffort
                                ?: it.selectedReasoningEffort,
                            selectedServiceTier = started.serviceTier ?: it.selectedServiceTier,
                        )
                    }
                    started.id
                }
                if (asGoal) {
                    val goal = client.setThreadGoal(
                        threadId = threadId,
                        objective = prompt,
                        status = ThreadGoalStatus.ACTIVE,
                    )
                    _state.update { state ->
                        if (state.selectedThreadId == threadId) {
                            state.copy(threadGoal = goal, isGoalLoading = false, goalError = null)
                        } else {
                            state
                        }
                    }
                    return@runCatching
                }
                client.startTurn(
                    threadId = threadId,
                    text = prompt,
                    cwd = cwd,
                    model = selectedModel,
                    reasoningEffort = selectedReasoningEffort,
                    serviceTier = selectedServiceTier,
                    approvalPolicy = approvalPolicy,
                    approvalsReviewer = approvalsReviewer,
                    permissionProfile = permissionProfile,
                    collaborationMode = collaborationMode,
                    mentions = mentions,
                    attachments = attachments,
                )
            }.onSuccess {
                if (steeringThreadId != null) {
                    _state.update { it.copy(notice = "已追加到当前运行中的任务") }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        timeline = it.timeline.filterNot { item -> item.id == localItemId },
                        isTurnRunning = if (asGoal) it.isTurnRunning else steeringThreadId != null,
                        activeTurnId = if (asGoal || steeringThreadId != null) it.activeTurnId else null,
                        isGoalLoading = if (asGoal) false else it.isGoalLoading,
                        goalError = if (asGoal) friendlyGoalError(error) else it.goalError,
                    )
                }
                if (asGoal) {
                    _state.update {
                        it.copy(notice = "Failed to set goal: ${friendlyGoalError(error)}")
                    }
                } else {
                    showError(error)
                }
            }
        }
    }

    fun renameThread(thread: RemoteThread, name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val client = rpc ?: return
        viewModelScope.launch {
            runCatching { client.renameThread(thread.id, trimmedName) }
                .onSuccess { _state.update { it.withThreadRenamed(thread.id, trimmedName) } }
                .onFailure(::showError)
        }
    }

    fun archiveThread(thread: RemoteThread) {
        val client = rpc ?: return
        if (_state.value.isTurnRunning && _state.value.selectedThreadId == thread.id) {
            _state.update { it.copy(notice = "请先停止当前任务，再归档会话") }
            return
        }
        viewModelScope.launch {
            runCatching { client.archiveThread(thread.id) }
                .onSuccess {
                    _state.update { state ->
                        state.withThreadArchived(thread.id).copy(
                            archivedThreads = (listOf(thread) + state.archivedThreads)
                                .distinctBy { it.id }
                                .sortedByDescending { it.updatedAt },
                        )
                    }
                }
                .onFailure(::showError)
        }
    }

    fun loadArchivedThreads() {
        val client = rpc ?: return
        viewModelScope.launch {
            _state.update { it.copy(isArchivedThreadsLoading = true, archivedThreadsError = null) }
            runCatching { client.listArchivedThreads() }
                .onSuccess { archived ->
                    _state.update {
                        it.copy(
                            archivedThreads = archived,
                            isArchivedThreadsLoading = false,
                            archivedThreadsError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isArchivedThreadsLoading = false,
                            archivedThreadsError = friendlyError(error),
                        )
                    }
                }
        }
    }

    fun unarchiveThread(thread: RemoteThread) {
        val client = rpc ?: return
        viewModelScope.launch {
            runCatching { client.unarchiveThread(thread.id) }
                .onSuccess { restored ->
                    _state.update { state ->
                        val threads = (listOf(restored) + state.threads).distinctBy { it.id }
                        state.copy(
                            threads = threads,
                            projects = groupThreadsByProject(threads),
                            archivedThreads = state.archivedThreads.filterNot { it.id == restored.id },
                            notice = "任务已恢复",
                        )
                    }
                }
                .onFailure(::showError)
        }
    }

    fun deleteArchivedThread(thread: RemoteThread) {
        val client = rpc ?: return
        viewModelScope.launch {
            runCatching { client.deleteThread(thread.id) }
                .onSuccess {
                    _state.update { state ->
                        state.copy(
                            archivedThreads = state.archivedThreads.filterNot { it.id == thread.id },
                            notice = "任务已永久删除",
                        )
                    }
                }
                .onFailure(::showError)
        }
    }

    fun setThreadPinned(thread: RemoteThread, isPinned: Boolean) {
        val client = rpc ?: return
        viewModelScope.launch {
            runCatching { client.setThreadPinned(thread.id, isPinned) }
                .onSuccess { updated ->
                    _state.update { state ->
                        val threads = state.threads.map { current ->
                            if (current.id == updated.id) updated else current
                        }
                        state.copy(threads = threads, projects = groupThreadsByProject(threads))
                    }
                }
                .onFailure(::showError)
        }
    }

    fun compactThread() {
        val client = rpc ?: return
        val threadId = _state.value.selectedThreadId ?: run {
            _state.update { it.copy(notice = "新任务还没有可压缩的上下文") }
            return
        }
        if (_state.value.isTurnRunning) {
            _state.update { it.copy(notice = "任务运行期间不能压缩上下文") }
            return
        }
        viewModelScope.launch {
            runCatching { client.compactThread(threadId) }
                .onSuccess { _state.update { it.copy(notice = "正在压缩任务上下文") } }
                .onFailure(::showError)
        }
    }

    fun forkThread() {
        val client = rpc ?: return
        val snapshot = _state.value
        val threadId = snapshot.selectedThreadId ?: run {
            _state.update { it.copy(notice = "请先打开一个远端任务再继续到新任务") }
            return
        }
        if (snapshot.isTurnRunning) {
            _state.update { it.copy(notice = "请先停止当前任务，再继续到新任务") }
            return
        }
        val cwd = snapshot.threads.firstOrNull { it.id == threadId }?.cwd
            ?.takeIf(String::isNotBlank) ?: snapshot.selectedProjectPath.orEmpty()
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, notice = null) }
            runCatching {
                client.forkThread(
                    threadId = threadId,
                    cwd = cwd,
                    model = snapshot.selectedModel,
                    serviceTier = snapshot.selectedServiceTier,
                    approvalPolicy = snapshot.approvalPolicy,
                    approvalsReviewer = snapshot.approvalsReviewer,
                    permissionProfile = snapshot.selectedPermissionProfile,
                )
            }.onSuccess { forked ->
                _state.update { state ->
                    val threads = (listOf(forked.thread) + state.threads).distinctBy { it.id }
                    val model = state.models.firstOrNull { it.id == forked.session.model }
                    state.copy(
                        threads = threads,
                        projects = groupThreadsByProject(threads),
                        selectedProjectPath = forked.thread.cwd.ifBlank { state.selectedProjectPath.orEmpty() },
                        selectedThreadId = forked.thread.id,
                        threadGoal = null,
                        isGoalLoading = false,
                        goalError = null,
                        threadTokenUsage = null,
                        timeline = forked.session.timeline,
                        olderHistoryCursor = forked.session.olderHistoryCursor,
                        hasOlderHistory = forked.session.olderHistoryCursor != null,
                        isOlderHistoryLoading = false,
                        olderHistoryError = null,
                        consumedHistoryCursors = emptySet(),
                        selectedModel = model?.id ?: state.selectedModel,
                        selectedReasoningEffort = forked.session.reasoningEffort
                            ?.takeIf { effort -> model?.supports(effort) == true }
                            ?: model?.preferredReasoningEffort()
                            ?: state.selectedReasoningEffort,
                        selectedServiceTier = forked.session.serviceTier
                            ?.takeIf { tier -> model?.serviceTiers?.any { it.id == tier } == true }
                            ?: model?.defaultServiceTier,
                        selectedCollaborationMode = forked.session.collaborationMode
                            ?.takeIf { mode -> state.collaborationModes.any { it.mode == mode } }
                            ?: state.defaultCollaborationMode(),
                        selectedPermissionProfile = forked.session.permissionProfile,
                        approvalPolicy = forked.session.approvalPolicy ?: state.approvalPolicy,
                        approvalsReviewer = forked.session.approvalsReviewer ?: state.approvalsReviewer,
                        isBusy = false,
                        notice = "已继续到新的远端任务",
                    )
                }
            }.onFailure(::showError)
        }
    }

    fun startReview(targetKind: ReviewTargetKind, targetValue: String = "") {
        val client = rpc ?: return
        val snapshot = _state.value
        val threadId = snapshot.selectedThreadId ?: run {
            _state.update { it.copy(notice = "请先打开一个远端任务再开始代码审查") }
            return
        }
        if (snapshot.isTurnRunning) {
            _state.update { it.copy(notice = "当前任务仍在运行，暂时不能开始代码审查") }
            return
        }
        if (targetKind != ReviewTargetKind.UNCOMMITTED_CHANGES && targetValue.isBlank()) {
            _state.update { it.copy(notice = "请填写审查目标") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isTurnRunning = true, notice = null) }
            runCatching { client.startReview(threadId, targetKind, targetValue) }
                .onSuccess { review ->
                    _state.update {
                        it.copy(
                            isTurnRunning = true,
                            activeTurnId = review.turnId,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isTurnRunning = false, activeTurnId = null) }
                    showError(error)
                }
        }
    }

    fun runInit() {
        val client = rpc ?: return
        val snapshot = _state.value
        if (snapshot.isTurnRunning) {
            _state.update { it.copy(notice = "当前任务仍在运行，暂时不能执行 /init") }
            return
        }
        val cwd = snapshot.threads.firstOrNull { it.id == snapshot.selectedThreadId }?.cwd
            ?.takeIf(String::isNotBlank)
            ?: snapshot.selectedProjectPath?.takeIf(String::isNotBlank)
            ?: run {
                _state.update { it.copy(notice = "请先选择一个远端项目") }
                return
            }
        viewModelScope.launch {
            val agentsPath = remoteChildPath(cwd, "AGENTS.md")
            runCatching { client.remotePathExists(agentsPath) }
                .onSuccess { exists ->
                    if (exists) {
                        _state.update { it.copy(notice = "AGENTS.md 已存在，已跳过 /init 以避免覆盖") }
                    } else {
                        sendMessage(INIT_PROMPT)
                    }
                }
                .onFailure(::showError)
        }
    }

    fun loadMcpStatus() {
        val client = rpc ?: return
        val threadId = _state.value.selectedThreadId
        viewModelScope.launch {
            _state.update { it.copy(isMcpStatusLoading = true, mcpStatusError = null) }
            runCatching { client.listMcpServerStatuses(threadId) }
                .onSuccess { servers ->
                    _state.update {
                        it.copy(mcpServers = servers, isMcpStatusLoading = false, mcpStatusError = null)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            mcpServers = emptyList(),
                            isMcpStatusLoading = false,
                            mcpStatusError = friendlyError(error),
                        )
                    }
                }
        }
    }

    fun reloadMcpServers() {
        val client = rpc ?: return
        val threadId = _state.value.selectedThreadId
        viewModelScope.launch {
            _state.update { it.copy(isMcpStatusLoading = true, mcpStatusError = null) }
            runCatching {
                client.reloadMcpServers()
                client.listMcpServerStatuses(threadId)
            }.onSuccess { servers ->
                _state.update {
                    it.copy(mcpServers = servers, isMcpStatusLoading = false, mcpStatusError = null)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(isMcpStatusLoading = false, mcpStatusError = friendlyError(error))
                }
            }
        }
    }

    fun startMcpLogin(serverName: String) {
        val client = rpc ?: return
        val threadId = _state.value.selectedThreadId
        viewModelScope.launch {
            _state.update { it.copy(isMcpLoginStarting = true, mcpStatusError = null) }
            runCatching { client.startMcpOauthLogin(serverName, threadId) }
                .onSuccess { authorizationUrl ->
                    _state.update {
                        it.copy(
                            isMcpLoginStarting = false,
                            mcpAuthorizationUrl = authorizationUrl,
                            notice = "请在浏览器中完成 $serverName 授权",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isMcpLoginStarting = false, mcpStatusError = friendlyError(error))
                    }
                }
        }
    }

    fun clearMcpAuthorizationUrl() = _state.update { it.copy(mcpAuthorizationUrl = null) }

    fun submitFeedback(classification: String, reason: String) {
        val client = rpc ?: return
        val threadId = _state.value.selectedThreadId
        viewModelScope.launch {
            _state.update { it.copy(isFeedbackSubmitting = true, feedbackError = null) }
            runCatching { client.submitFeedback(classification, reason, threadId) }
                .onSuccess { feedbackId ->
                    _state.update {
                        it.copy(
                            isFeedbackSubmitting = false,
                            feedbackError = null,
                            notice = if (feedbackId.isBlank()) {
                                "反馈已提交"
                            } else {
                                "反馈已提交：$feedbackId"
                            },
                        )
                    }
                }
                .onFailure { error ->
                    val message = friendlyError(error)
                    _state.update {
                        it.copy(isFeedbackSubmitting = false, feedbackError = message, notice = message)
                    }
                }
        }
    }

    fun showGoalRequirement() = _state.update {
        it.copy(notice = "请先发送第一条消息创建远端任务，再使用 /goal 设置 Goal")
    }

    fun setThreadGoal(objective: String) {
        val trimmedObjective = objective.trim()
        if (trimmedObjective.isEmpty()) {
            _state.update { it.copy(goalError = "Goal 不能为空") }
            return
        }
        val client = rpc ?: return
        val threadId = _state.value.selectedThreadId ?: run {
            showGoalRequirement()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isGoalLoading = true, goalError = null) }
            runCatching {
                client.setThreadGoal(
                    threadId = threadId,
                    objective = trimmedObjective,
                    status = ThreadGoalStatus.ACTIVE,
                )
            }.onSuccess { goal ->
                _state.update { state ->
                    if (state.selectedThreadId == threadId) {
                        state.copy(threadGoal = goal, isGoalLoading = false, goalError = null)
                    } else {
                        state
                    }
                }
            }.onFailure { error ->
                updateGoalFailure(threadId, "Failed to set goal", error)
            }
        }
    }

    fun setThreadGoalStatus(status: ThreadGoalStatus) {
        val client = rpc ?: return
        val threadId = _state.value.selectedThreadId ?: run {
            showGoalRequirement()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isGoalLoading = true, goalError = null) }
            runCatching { client.setThreadGoal(threadId = threadId, status = status) }
                .onSuccess { goal ->
                    _state.update { state ->
                        if (state.selectedThreadId == threadId) {
                            state.copy(threadGoal = goal, isGoalLoading = false, goalError = null)
                        } else {
                            state
                        }
                    }
                }
                .onFailure { error ->
                    updateGoalFailure(threadId, "Failed to update goal", error)
                }
        }
    }

    fun clearThreadGoal() {
        val client = rpc ?: return
        val threadId = _state.value.selectedThreadId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isGoalLoading = true, goalError = null) }
            runCatching { client.clearThreadGoal(threadId) }
                .onSuccess {
                    _state.update { state ->
                        if (state.selectedThreadId == threadId) {
                            state.copy(threadGoal = null, isGoalLoading = false, goalError = null)
                        } else {
                            state
                        }
                    }
                }
                .onFailure { error ->
                    updateGoalFailure(threadId, "Failed to clear goal", error)
                }
        }
    }

    fun showConnectionStatus() {
        val client = rpc ?: return
        viewModelScope.launch {
            _state.update { it.copy(isStatusLoading = true, statusError = null) }
            runCatching { client.readRateLimits() }
                .onSuccess { limits ->
                    _state.update {
                        it.copy(rateLimits = limits, isStatusLoading = false, statusError = null)
                    }
                }
                .onFailure { error ->
                    _state.update { state ->
                        if (error.isUnsupportedRpcMethod("account/rateLimits/read")) {
                            state.copy(rateLimits = null, isStatusLoading = false, statusError = null)
                        } else {
                            state.copy(isStatusLoading = false, statusError = friendlyError(error))
                        }
                    }
                }
        }
    }

    fun interruptTurn() {
        val client = rpc ?: return
        val threadId = _state.value.selectedThreadId ?: return
        val turnId = _state.value.activeTurnId ?: return
        viewModelScope.launch {
            runCatching { client.interruptTurn(threadId, turnId) }.onFailure(::showError)
        }
    }

    fun respondToApproval(decision: String, answers: Map<String, List<String>> = emptyMap()) {
        val approval = _state.value.pendingApproval ?: return
        val client = rpc ?: return
        viewModelScope.launch {
            runCatching { client.respondToApproval(approval, decision, answers) }
                .onSuccess { _state.update { it.copy(pendingApproval = null) } }
                .onFailure(::showError)
        }
    }

    fun startRemoteLogin() {
        val client = rpc ?: return
        if (_state.value.isLoginStarting || _state.value.remoteDeviceLogin != null) return
        viewModelScope.launch {
            _state.update { it.copy(isLoginStarting = true, notice = null) }
            runCatching { client.startDeviceLogin() }
                .onSuccess { login ->
                    _state.update { it.copy(remoteDeviceLogin = login, isLoginStarting = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoginStarting = false) }
                    showError(error)
                }
        }
    }

    fun cancelRemoteLogin() {
        val login = _state.value.remoteDeviceLogin ?: return
        val client = rpc ?: return
        _state.update { it.copy(remoteDeviceLogin = null, isLoginStarting = false) }
        viewModelScope.launch {
            runCatching { client.cancelLogin(login.loginId) }.onFailure(::showError)
        }
    }

    fun setModel(modelId: String) = _state.update { state ->
        val model = state.models.firstOrNull { it.id == modelId } ?: return@update state
        state.copy(
            selectedModel = model.id,
            selectedReasoningEffort = model.preferredReasoningEffort(),
            selectedServiceTier = model.defaultServiceTier,
        )
    }

    fun setReasoningEffort(effort: String) = _state.update { state ->
        val model = state.models.firstOrNull { it.id == state.selectedModel }
        if (model?.supports(effort) == true) state.copy(selectedReasoningEffort = effort) else state
    }

    fun setServiceTier(serviceTier: String?) = _state.update { state ->
        val model = state.models.firstOrNull { it.id == state.selectedModel } ?: return@update state
        if (serviceTier == null || model.serviceTiers.any { it.id == serviceTier }) {
            state.copy(selectedServiceTier = serviceTier)
        } else {
            state
        }
    }

    fun setCollaborationMode(mode: String) = _state.update { state ->
        if (state.collaborationModes.any { it.mode == mode }) {
            state.copy(
                selectedCollaborationMode = mode,
                notice = if (mode == "plan") "已切换到计划模式" else null,
            )
        } else {
            state.copy(notice = "远端 Codex 没有提供 $mode 模式")
        }
    }

    fun setPermissionProfile(profileId: String?) = _state.update { state ->
        if (profileId == null || profileId in BUILT_IN_PERMISSION_PROFILES ||
            state.permissionProfiles.any { it.id == profileId && it.allowed }
        ) {
            state.copy(selectedPermissionProfile = profileId)
        } else {
            state
        }
    }

    fun setPermissionMode(mode: PermissionMode) = _state.update { state ->
        when (mode) {
            PermissionMode.ASK -> state.copy(
                selectedPermissionProfile = ":workspace",
                approvalPolicy = "on-request",
                approvalsReviewer = "user",
            )
            PermissionMode.AUTO_REVIEW -> state.copy(
                selectedPermissionProfile = ":workspace",
                approvalPolicy = "on-request",
                approvalsReviewer = "auto_review",
            )
            PermissionMode.FULL_ACCESS -> state.copy(
                selectedPermissionProfile = ":danger-full-access",
                approvalPolicy = "never",
                approvalsReviewer = "user",
            )
            PermissionMode.READ_ONLY -> state.copy(
                selectedPermissionProfile = ":read-only",
                approvalPolicy = "on-request",
                approvalsReviewer = "user",
            )
        }
    }
    fun loadRemoteDirectory(path: String) {
        val client = rpc ?: return
        if (path.isBlank()) return
        _state.update {
            it.copy(
                remoteDirectoryPath = path,
                remoteDirectoryEntries = emptyList(),
                isRemoteDirectoryLoading = true,
                remoteDirectoryError = null,
            )
        }
        viewModelScope.launch {
            runCatching { client.readRemoteDirectory(path) }
                .onSuccess { entries ->
                    _state.update { state ->
                        if (state.remoteDirectoryPath != path) return@update state
                        state.copy(
                            remoteDirectoryEntries = entries.sortedWith(
                                compareBy<RemotePathEntry> { !it.isDirectory }
                                    .thenBy { it.name.lowercase() },
                            ),
                            isRemoteDirectoryLoading = false,
                            remoteDirectoryError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { state ->
                        if (state.remoteDirectoryPath != path) return@update state
                        state.copy(
                            remoteDirectoryEntries = emptyList(),
                            isRemoteDirectoryLoading = false,
                            remoteDirectoryError = friendlyError(error),
                        )
                    }
                }
        }
    }
    fun clearRemoteDirectory() = _state.update {
        it.copy(
            remoteDirectoryPath = null,
            remoteDirectoryEntries = emptyList(),
            isRemoteDirectoryLoading = false,
            remoteDirectoryError = null,
        )
    }
    fun clearNotice() = _state.update { it.copy(notice = null) }

    private fun observeEvents(client: CodexRpcClient) {
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            client.events.collect { event ->
                when (event) {
                    is AppServerEvent.ItemUpsert -> upsertItem(event.threadId, event.item)
                    is AppServerEvent.AgentDelta -> appendDelta(event.threadId, event.itemId, event.delta, TimelineKind.AGENT)
                    is AppServerEvent.PlanDelta -> appendDelta(event.threadId, event.itemId, event.delta, TimelineKind.PLAN)
                    is AppServerEvent.ReasoningDelta -> appendDelta(event.threadId, event.itemId, event.delta, TimelineKind.REASONING)
                    is AppServerEvent.OutputDelta -> appendDelta(event.threadId, event.itemId, event.delta, TimelineKind.COMMAND)
                    is AppServerEvent.TurnRunning -> {
                        if (!_state.value.acceptsThreadEvent(event.threadId)) return@collect
                        _state.update { state ->
                            state.copy(
                                isTurnRunning = event.running,
                                activeTurnId = if (event.running) event.turnId else null,
                                timeline = if (event.running) {
                                    state.timeline
                                } else {
                                    state.timeline.withRunningItemsCompleted()
                                },
                            )
                        }
                        if (!event.running) {
                            refreshThreads()
                        }
                    }
                    is AppServerEvent.Approval -> _state.update { state ->
                        if (state.acceptsThreadEvent(event.threadId)) state.copy(pendingApproval = event.request) else state
                    }
                    AppServerEvent.AccountChanged -> refreshRemoteAccount()
                    AppServerEvent.ThreadsChanged -> refreshThreads()
                    AppServerEvent.SkillsChanged -> refreshComposerCatalog(forceReload = true)
                    is AppServerEvent.GoalUpdated -> _state.update { state ->
                        if (state.selectedThreadId == event.threadId) {
                            state.copy(threadGoal = event.goal, isGoalLoading = false, goalError = null)
                        } else {
                            state
                        }
                    }
                    is AppServerEvent.GoalCleared -> {
                        _state.update { state ->
                            if (state.selectedThreadId == event.threadId) {
                                state.copy(threadGoal = null, isGoalLoading = false, goalError = null)
                            } else {
                                state
                            }
                        }
                    }
                    is AppServerEvent.TokenUsageUpdated -> _state.update { state ->
                        if (state.selectedThreadId == event.threadId) {
                            state.copy(threadTokenUsage = event.usage)
                        } else {
                            state
                        }
                    }
                    is AppServerEvent.RateLimitsUpdated -> _state.update {
                        it.copy(rateLimits = event.rateLimits, isStatusLoading = false, statusError = null)
                    }
                    is AppServerEvent.ContextCompacted -> {
                        if (_state.value.selectedThreadId == event.threadId) {
                            _state.update { state ->
                                val marker = TimelineItem(
                                    id = "compaction-${UUID.randomUUID()}",
                                    kind = TimelineKind.COMPACTION,
                                    title = "Context compacted",
                                )
                                state.copy(timeline = state.timeline + marker, notice = "任务上下文已压缩")
                            }
                        }
                    }
                    is AppServerEvent.McpLoginCompleted -> {
                        _state.update {
                            it.copy(
                                isMcpLoginStarting = false,
                                notice = if (event.success) {
                                    "${event.name} 授权完成"
                                } else {
                                    event.error ?: "${event.name} 授权未完成"
                                },
                            )
                        }
                        if (event.success) reloadMcpServers()
                    }
                    is AppServerEvent.ThreadSettingsUpdated -> _state.update { state ->
                        if (state.selectedThreadId != event.threadId) return@update state
                        val model = state.models.firstOrNull { it.id == event.settings.model }
                        state.copy(
                            selectedModel = model?.id ?: state.selectedModel,
                            selectedReasoningEffort = event.settings.reasoningEffort
                                ?.takeIf { effort -> model?.supports(effort) == true }
                                ?: state.selectedReasoningEffort,
                            selectedServiceTier = event.settings.serviceTier,
                            selectedCollaborationMode = event.settings.collaborationMode
                                ?.takeIf { mode -> state.collaborationModes.any { it.mode == mode } }
                                ?: state.selectedCollaborationMode,
                            selectedPermissionProfile = event.settings.permissionProfile,
                            approvalPolicy = event.settings.approvalPolicy ?: state.approvalPolicy,
                            approvalsReviewer = event.settings.approvalsReviewer ?: state.approvalsReviewer,
                        )
                    }
                    is AppServerEvent.LoginCompleted -> {
                        if (event.success) {
                            _state.update { it.copy(remoteDeviceLogin = null, isLoginStarting = false) }
                            refreshRemoteAccount()
                        } else {
                            _state.update {
                                it.copy(
                                    remoteDeviceLogin = null,
                                    isLoginStarting = false,
                                    notice = event.error ?: "远端 Codex 登录失败",
                                )
                            }
                        }
                    }
                    is AppServerEvent.Failure -> _state.update { state ->
                        if (state.acceptsThreadEvent(event.threadId)) {
                            state.copy(
                                notice = event.message,
                                isTurnRunning = false,
                                activeTurnId = null,
                                timeline = state.timeline.withRunningItemsCompleted(),
                            )
                        } else {
                            state
                        }
                    }
                    is AppServerEvent.Warning -> _state.update { it.copy(notice = event.message) }
                    is AppServerEvent.Diagnostic -> {
                        if (event.message.contains("not found", ignoreCase = true) ||
                            event.message.contains("not recognized", ignoreCase = true)
                        ) {
                            _state.update { it.copy(notice = "远端登录 shell 找不到 codex 命令：${event.message}") }
                        }
                    }
                }
            }
        }
    }

    private fun refreshRemoteAccount() {
        val client = rpc ?: return
        viewModelScope.launch {
            runCatching {
                val account = client.readAccount()
                val models = client.listModels()
                account to models
            }.onSuccess { (account, models) ->
                _state.update { state ->
                    val selected = models.firstOrNull { it.id == state.selectedModel }
                        ?: models.firstOrNull { it.isDefault }
                        ?: models.firstOrNull()
                    val effort = state.selectedReasoningEffort
                        ?.takeIf { selected?.supports(it) == true }
                        ?: selected?.preferredReasoningEffort()
                    val serviceTier = state.selectedServiceTier
                        ?.takeIf { tier -> selected?.serviceTiers?.any { it.id == tier } == true }
                        ?: selected?.defaultServiceTier
                    state.copy(
                        remoteAccount = account,
                        models = models,
                        selectedModel = selected?.id,
                        selectedReasoningEffort = effort,
                        selectedServiceTier = serviceTier,
                    )
                }
            }.onFailure(::showError)
        }
    }

    private fun updateGoalFailure(
        threadId: String,
        action: String,
        error: Throwable,
        loading: Boolean = false,
    ) {
        val message = friendlyGoalError(error)
        _state.update { state ->
            if (state.selectedThreadId == threadId) {
                state.copy(
                    isGoalLoading = loading,
                    goalError = message,
                    notice = "$action: $message",
                )
            } else {
                state
            }
        }
    }

    private fun friendlyGoalError(error: Throwable): String {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .firstOrNull { it.isNotBlank() }
            ?: error::class.java.simpleName
        return when {
            message.contains("goals feature is disabled", ignoreCase = true) ->
                "远端 Codex 未启用 Goals；请在远端 config.toml 的 [features] 下设置 goals = true 后重连"
            message.contains("ephemeral thread does not support goals", ignoreCase = true) ->
                "该任务尚未持久化，发送第一条消息后才能设置 Goal"
            error.isUnsupportedRpcMethod("thread/goal/get") ||
                error.isUnsupportedRpcMethod("thread/goal/set") ||
                error.isUnsupportedRpcMethod("thread/goal/clear") ->
                "远端 Codex 版本不支持 Goal，请先更新远端 Codex"
            message.contains("method not found", ignoreCase = true) ||
                message.contains("unknown method", ignoreCase = true) ->
                "远端 Codex 版本不支持 Goal，请先更新远端 Codex"
            else -> friendlyError(error)
        }
    }

    private fun upsertItem(threadId: String?, item: TimelineItem) = _state.update { state ->
        if (!state.acceptsThreadEvent(threadId)) return@update state
        val index = state.timeline.indexOfFirst { it.id == item.id }
        val localUserIndex = if (item.kind == TimelineKind.USER) {
            state.timeline.indexOfLast { it.id.startsWith("local-") && it.kind == TimelineKind.USER && it.body == item.body }
        } else -1
        if (index < 0 && localUserIndex >= 0) {
            state.copy(timeline = state.timeline.toMutableList().also { it[localUserIndex] = item })
        } else if (index < 0) state.copy(timeline = state.timeline + item)
        else state.copy(timeline = state.timeline.toMutableList().also { it[index] = item })
    }

    private fun appendDelta(threadId: String?, id: String, delta: String, kind: TimelineKind) = _state.update { state ->
        if (!state.acceptsThreadEvent(threadId)) return@update state
        val index = state.timeline.indexOfFirst { it.id == id }
        if (index < 0) {
            state.copy(timeline = state.timeline + TimelineItem(id, kind, body = delta, status = "inProgress"))
        } else {
            state.copy(timeline = state.timeline.toMutableList().also { items ->
                val current = items[index]
                items[index] = current.copy(body = current.body + delta)
            })
        }
    }

    private fun refreshThreads() {
        val client = rpc ?: return
        if (_state.value.activeConnection == null) return
        viewModelScope.launch {
            runCatching { client.listThreads() }
                .onSuccess { threads ->
                    val projects = groupThreadsByProject(threads)
                    _state.update { state ->
                        val selectedPath = state.selectedProjectPath
                            ?.takeIf { path -> projects.any { it.path == path } }
                            ?: projects.firstOrNull()?.path
                        val selectedThreadId = state.selectedThreadId
                            ?.takeIf { id -> threads.any { it.id == id } }
                        state.copy(
                            threads = threads,
                            projects = projects,
                            selectedProjectPath = selectedPath,
                            selectedThreadId = selectedThreadId,
                            threadGoal = if (selectedThreadId == null) null else state.threadGoal,
                            isGoalLoading = if (selectedThreadId == null) false else state.isGoalLoading,
                            goalError = if (selectedThreadId == null) null else state.goalError,
                            threadTokenUsage = if (selectedThreadId == null) null else state.threadTokenUsage,
                            timeline = if (selectedThreadId == null && state.selectedThreadId != null) {
                                emptyList()
                            } else {
                                state.timeline
                            },
                            olderHistoryCursor = if (selectedThreadId == null) null else state.olderHistoryCursor,
                            hasOlderHistory = if (selectedThreadId == null) false else state.hasOlderHistory,
                            isOlderHistoryLoading = if (selectedThreadId == null) false else state.isOlderHistoryLoading,
                            olderHistoryError = if (selectedThreadId == null) null else state.olderHistoryError,
                            consumedHistoryCursors = if (selectedThreadId == null) emptySet() else state.consumedHistoryCursors,
                            connectionMessage = connectionSummary(
                                projects.size,
                                threads.size,
                                state.remoteServer?.codexVersion.orEmpty(),
                            ),
                        )
                    }
                }
        }
    }

    private suspend fun loadComposerCatalog(
        client: CodexRpcClient,
        cwds: List<String>,
        forceReload: Boolean = false,
    ): ComposerCatalog {
        val distinctCwds = cwds.distinct()
        val skills = runCatching {
            withTimeout(45_000) { client.listSkills(distinctCwds, forceReload) }
        }
        val plugins = runCatching {
            withTimeout(45_000) { client.listInstalledPlugins(distinctCwds) }
        }
        val errors = listOfNotNull(
            skills.exceptionOrNull()?.message?.let { "Skills: $it" },
            plugins.exceptionOrNull()?.message?.let { "Plugins: $it" },
        )
        return ComposerCatalog(
            skills = skills.getOrDefault(emptyList()),
            plugins = plugins.getOrDefault(emptyList()),
            error = errors.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        )
    }

    private fun refreshComposerCatalog(forceReload: Boolean = false) {
        val client = rpc ?: return
        val cwds = _state.value.projects.map { it.path }.filter(String::isNotBlank)
        _state.update { it.copy(isComposerCatalogLoading = true, composerCatalogError = null) }
        viewModelScope.launch {
            val catalog = loadComposerCatalog(client, cwds, forceReload)
            _state.update {
                it.copy(
                    skills = catalog.skills,
                    plugins = catalog.plugins,
                    isComposerCatalogLoading = false,
                    composerCatalogError = catalog.error,
                )
            }
        }
    }

    private fun showError(error: Throwable) {
        _state.update { it.copy(isBusy = false, notice = friendlyError(error)) }
    }

    private fun friendlyError(error: Throwable): String {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .firstOrNull { it.isNotBlank() }
            ?: error::class.java.simpleName
        return when {
            message.contains("not logged in", ignoreCase = true) ||
                message.contains("OpenAI authentication", ignoreCase = true) ->
                "远端 Codex 尚未登录。请在应用中登录，或在远端运行 codex login。"
            message.contains("auth", ignoreCase = true) -> "SSH 认证失败，请检查用户名和凭据。$message"
            message.contains("timed out", ignoreCase = true) -> "连接超时，请检查主机、端口、VPN 和防火墙。"
            message.contains("refused", ignoreCase = true) -> "SSH 连接被拒绝，请确认 sshd 正在监听。"
            else -> message
        }
    }

    override fun onCleared() {
        rpc?.close()
        super.onCleared()
    }
}

private data class ConnectionBootstrap(
    val server: RemoteServerInfo,
    val account: RemoteAccount,
    val models: List<RemoteModel>,
    val threads: List<RemoteThread>,
    val collaborationModes: List<RemoteCollaborationMode>,
    val permissionProfiles: List<com.codex.remote.domain.RemotePermissionProfile>,
)

internal fun AppUiState.acceptsThreadEvent(threadId: String?): Boolean =
    selectedThreadId != null && selectedThreadId == threadId

internal fun List<SavedConnection>.lastUsedConnectionOrNull(): SavedConnection? =
    maxByOrNull(SavedConnection::lastUsedAt)?.takeIf { it.lastUsedAt > 0 }

internal fun Throwable.isUnsupportedRpcMethod(method: String): Boolean =
    generateSequence(this) { it.cause }.any { error ->
        val message = error.message.orEmpty()
        (error as? com.codex.remote.data.rpc.RpcException)?.code == -32601 ||
            (message.contains(method, ignoreCase = true) &&
                listOf("unsupported method", "method not found", "unknown method", "not implemented")
                    .any { marker -> message.contains(marker, ignoreCase = true) })
    }

private data class ComposerCatalog(
    val skills: List<com.codex.remote.domain.RemoteSkill>,
    val plugins: List<com.codex.remote.domain.RemotePlugin>,
    val error: String?,
)

internal fun resolveComposerMentions(
    prompt: String,
    cwd: String,
    selectedMentions: List<ComposerMention>,
    state: AppUiState,
): List<ComposerMention> {
    val selected = selectedMentions.filter { prompt.containsComposerToken(it.token) }
    val skills = state.skills
        .filter { skill -> skill.enabled && (skill.cwds.isEmpty() || cwd in skill.cwds) }
        .filter { prompt.containsComposerToken(it.composerToken()) }
        .map { skill ->
            ComposerMention(ComposerMentionKind.SKILL, skill.name, skill.path, skill.composerToken())
        }
    val plugins = state.plugins
        .filter { it.enabled && prompt.containsComposerToken(it.composerToken()) }
        .map { plugin ->
            ComposerMention(ComposerMentionKind.PLUGIN, plugin.displayName, plugin.mentionPath, plugin.composerToken())
        }
    return (selected + skills + plugins).distinctBy { "${it.kind}:${it.path}" }
}

private fun RemoteModel.supports(effort: String): Boolean =
    supportedReasoningEfforts.any { it.value == effort }

private fun RemoteModel.preferredReasoningEffort(): String? =
    defaultReasoningEffort?.takeIf(::supports)
        ?: supportedReasoningEfforts.firstOrNull()?.value

private fun AppUiState.defaultCollaborationMode(): String =
    collaborationModes.firstOrNull { it.mode == "default" }?.mode
        ?: collaborationModes.firstOrNull()?.mode
        ?: "default"

private fun String.isActiveTimelineStatus(): Boolean =
    equals("inProgress", ignoreCase = true) ||
        equals("in_progress", ignoreCase = true) ||
        equals("running", ignoreCase = true) ||
    equals("started", ignoreCase = true)

internal fun List<TimelineItem>.withRunningItemsCompleted(): List<TimelineItem> = map { item ->
    if (item.status.isActiveTimelineStatus()) item.copy(status = "completed") else item
}

private fun String.isRemoteThreadActive(): Boolean =
    equals("active", ignoreCase = true) || equals("inProgress", ignoreCase = true)

private fun connectionSummary(projects: Int, threads: Int, codexVersion: String): String = buildString {
    append("已导入 $projects 个项目、$threads 个会话")
    if (codexVersion.isNotBlank()) append(" · Codex $codexVersion")
}

internal fun remoteChildPath(root: String, child: String): String {
    val separator = if ('\\' in root && '/' !in root) '\\' else '/'
    val normalizedRoot = root.trimEnd('/', '\\')
    return if (normalizedRoot.isEmpty() && separator == '/') "/$child" else "$normalizedRoot$separator$child"
}

private val INIT_PROMPT = """
    Generate a file named AGENTS.md that serves as a contributor guide for this repository.
    Produce a clear, concise, and well-structured document with descriptive headings and actionable explanations.

    Requirements:
    - Title the document "Repository Guidelines".
    - Use Markdown headings for structure and keep the document around 200-400 words.
    - Describe project structure, build/test commands, coding style, testing guidelines, and commit/PR conventions.
    - Keep guidance specific to this repository and include concise examples where useful.
    - Add other relevant sections such as security, configuration, architecture, or agent instructions when appropriate.
""".trimIndent()

private val BUILT_IN_PERMISSION_PROFILES = setOf(":workspace", ":danger-full-access", ":read-only")
