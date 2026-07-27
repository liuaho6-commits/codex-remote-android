package com.codex.remote.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import com.codex.remote.AppViewModel
import com.codex.remote.domain.ConnectionStatus
import com.codex.remote.ui.screens.ConnectionsScreen
import com.codex.remote.ui.screens.WorkspaceScreen

@Composable
fun CodexRemoteApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val showConnections = state.showConnections || state.activeConnection == null

    if (showConnections) {
        ConnectionsScreen(
            state = state,
            onBack = { viewModel.showConnections(false) },
            onAdd = { viewModel.editConnection() },
            onEdit = viewModel::editConnection,
            onDelete = viewModel::deleteConnection,
            onConnect = viewModel::connect,
            onSave = viewModel::saveConnection,
            onCloseEditor = viewModel::closeEditor,
            onDismissNotice = viewModel::clearNotice,
        )
    } else {
        WorkspaceScreen(
            state = state,
            onOpenConnections = { viewModel.showConnections(true) },
            onNewThread = viewModel::newThread,
            onSelectProject = viewModel::selectProject,
            onSelectThread = viewModel::selectThread,
            onLoadOlderHistory = viewModel::loadOlderHistory,
            onRenameThread = viewModel::renameThread,
            onArchiveThread = viewModel::archiveThread,
            onLoadArchivedThreads = viewModel::loadArchivedThreads,
            onUnarchiveThread = viewModel::unarchiveThread,
            onDeleteArchivedThread = viewModel::deleteArchivedThread,
            onSetThreadPinned = viewModel::setThreadPinned,
            onSend = viewModel::sendMessage,
            onStop = viewModel::interruptTurn,
            onCompactThread = viewModel::compactThread,
            onForkThread = viewModel::forkThread,
            onStartReview = viewModel::startReview,
            onRunInit = viewModel::runInit,
            onLoadMcpStatus = viewModel::loadMcpStatus,
            onReloadMcpServers = viewModel::reloadMcpServers,
            onStartMcpLogin = viewModel::startMcpLogin,
            onMcpAuthorizationHandled = viewModel::clearMcpAuthorizationUrl,
            onSubmitFeedback = viewModel::submitFeedback,
            onShowGoalRequirement = viewModel::showGoalRequirement,
            onSetGoal = viewModel::setThreadGoal,
            onSetGoalStatus = viewModel::setThreadGoalStatus,
            onClearGoal = viewModel::clearThreadGoal,
            onShowStatus = viewModel::showConnectionStatus,
            onSetModel = viewModel::setModel,
            onSetReasoningEffort = viewModel::setReasoningEffort,
            onSetServiceTier = viewModel::setServiceTier,
            onSetCollaborationMode = viewModel::setCollaborationMode,
            onSetPermissionProfile = viewModel::setPermissionProfile,
            onSetApprovalPolicy = viewModel::setApprovalPolicy,
            onLoadRemoteDirectory = viewModel::loadRemoteDirectory,
            onClearRemoteDirectory = viewModel::clearRemoteDirectory,
            onStartLogin = viewModel::startRemoteLogin,
            onCancelLogin = viewModel::cancelRemoteLogin,
            onSetPane = viewModel::setPane,
            onApproval = viewModel::respondToApproval,
            onTrustHostKey = viewModel::trustPendingHostKey,
            onRejectHostKey = viewModel::rejectPendingHostKey,
            onDismissNotice = viewModel::clearNotice,
        )
    }
}
