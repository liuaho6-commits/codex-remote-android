package com.codex.remote.ui.screens

import android.content.Context
import android.text.method.LinkMovementMethod
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.remote.domain.AppUiState
import com.codex.remote.domain.ApprovalKind
import com.codex.remote.domain.ConnectionStatus
import com.codex.remote.domain.ComposerMention
import com.codex.remote.domain.ComposerMentionKind
import com.codex.remote.domain.ComposerImageAttachment
import com.codex.remote.domain.ComposerTriggerKind
import com.codex.remote.domain.FileChangeSummary
import com.codex.remote.domain.PermissionMode
import com.codex.remote.domain.RemoteProject
import com.codex.remote.domain.RemoteDeviceLogin
import com.codex.remote.domain.RemoteThreadTokenUsage
import com.codex.remote.domain.RemoteThread
import com.codex.remote.domain.ReviewTargetKind
import com.codex.remote.domain.TimelineItem
import com.codex.remote.domain.TimelineKind
import com.codex.remote.domain.ThreadGoal
import com.codex.remote.domain.ThreadGoalStatus
import com.codex.remote.domain.composerToken
import com.codex.remote.domain.findComposerTrigger
import com.codex.remote.domain.replaceComposerTrigger
import com.codex.remote.ui.theme.CodexGreen
import com.codex.remote.ui.theme.DiffGreen
import com.codex.remote.ui.theme.DiffRed
import com.codex.remote.ui.theme.MonoText
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    state: AppUiState,
    onOpenConnections: () -> Unit,
    onNewThread: () -> Unit,
    onSelectProject: (RemoteProject) -> Unit,
    onSelectThread: (RemoteThread) -> Unit,
    onLoadOlderHistory: () -> Unit,
    onRenameThread: (RemoteThread, String) -> Unit,
    onArchiveThread: (RemoteThread) -> Unit,
    onLoadArchivedThreads: () -> Unit,
    onUnarchiveThread: (RemoteThread) -> Unit,
    onDeleteArchivedThread: (RemoteThread) -> Unit,
    onSetThreadPinned: (RemoteThread, Boolean) -> Unit,
    onSend: (String, List<ComposerMention>, List<ComposerImageAttachment>, Boolean) -> Unit,
    onStop: () -> Unit,
    onCompactThread: () -> Unit,
    onForkThread: () -> Unit,
    onStartReview: (ReviewTargetKind, String) -> Unit,
    onRunInit: () -> Unit,
    onLoadMcpStatus: () -> Unit,
    onReloadMcpServers: () -> Unit,
    onStartMcpLogin: (String) -> Unit,
    onMcpAuthorizationHandled: () -> Unit,
    onSubmitFeedback: (String, String) -> Unit,
    onSetGoal: (String) -> Unit,
    onSetGoalStatus: (ThreadGoalStatus) -> Unit,
    onClearGoal: () -> Unit,
    onShowStatus: () -> Unit,
    onSetModel: (String) -> Unit,
    onSetReasoningEffort: (String) -> Unit,
    onSetServiceTier: (String?) -> Unit,
    onSetCollaborationMode: (String) -> Unit,
    onSetPermissionProfile: (String?) -> Unit,
    onSetPermissionMode: (PermissionMode) -> Unit,
    onLoadRemoteDirectory: (String) -> Unit,
    onClearRemoteDirectory: () -> Unit,
    onStartLogin: () -> Unit,
    onCancelLogin: () -> Unit,
    onApproval: (String, Map<String, List<String>>) -> Unit,
    onTrustHostKey: () -> Unit,
    onRejectHostKey: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var renameTarget by remember { mutableStateOf<RemoteThread?>(null) }
    var archiveTarget by remember { mutableStateOf<RemoteThread?>(null) }
    var showArchivedTasks by remember { mutableStateOf(false) }
    var deleteArchivedTarget by remember { mutableStateOf<RemoteThread?>(null) }
    LaunchedEffect(state.mcpAuthorizationUrl) {
        val authorizationUrl = state.mcpAuthorizationUrl ?: return@LaunchedEffect
        try {
            runCatching { uriHandler.openUri(authorizationUrl) }
        } finally {
            onMcpAuthorizationHandled()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                WorkspaceSidebar(
                    state = state,
                    modifier = Modifier.width(286.dp).fillMaxHeight(),
                    onNewThread = onNewThread,
                    onSelectProject = onSelectProject,
                    onSelectThread = onSelectThread,
                    onRenameThread = { renameTarget = it },
                    onArchiveThread = { archiveTarget = it },
                    onSetThreadPinned = onSetThreadPinned,
                    onOpenArchivedTasks = {
                        showArchivedTasks = true
                        onLoadArchivedThreads()
                    },
                    onOpenConnections = onOpenConnections,
                )
                HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
                WorkspaceContent(
                    state = state,
                    showMenu = false,
                    onMenu = {},
                    onSend = onSend,
                    onStop = onStop,
                    onLoadOlderHistory = onLoadOlderHistory,
                    onCompactThread = onCompactThread,
                    onForkThread = onForkThread,
                    onStartReview = onStartReview,
                    onRunInit = onRunInit,
                    onLoadMcpStatus = onLoadMcpStatus,
                    onReloadMcpServers = onReloadMcpServers,
                    onStartMcpLogin = onStartMcpLogin,
                    onSubmitFeedback = onSubmitFeedback,
                    onSetGoal = onSetGoal,
                    onSetGoalStatus = onSetGoalStatus,
                    onClearGoal = onClearGoal,
                    onShowStatus = onShowStatus,
                    onNewThread = onNewThread,
                    onRenameCurrentThread = { state.selectedThreadId?.let { id -> state.threads.firstOrNull { it.id == id } }?.let { renameTarget = it } },
                    onArchiveCurrentThread = { state.selectedThreadId?.let { id -> state.threads.firstOrNull { it.id == id } }?.let { archiveTarget = it } },
                    onSetModel = onSetModel,
                    onSetReasoningEffort = onSetReasoningEffort,
                    onSetServiceTier = onSetServiceTier,
                    onSetCollaborationMode = onSetCollaborationMode,
                    onSetPermissionProfile = onSetPermissionProfile,
                    onSetPermissionMode = onSetPermissionMode,
                    onLoadRemoteDirectory = onLoadRemoteDirectory,
                    onClearRemoteDirectory = onClearRemoteDirectory,
                    onStartLogin = onStartLogin,
                    onOpenConnections = onOpenConnections,
                    onDismissNotice = onDismissNotice,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(304.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        WorkspaceSidebar(
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                            onNewThread = {
                                onNewThread()
                                scope.launch { drawerState.close() }
                            },
                            onSelectProject = {
                                onSelectProject(it)
                                scope.launch { drawerState.close() }
                            },
                            onSelectThread = {
                                onSelectThread(it)
                                scope.launch { drawerState.close() }
                            },
                            onRenameThread = { renameTarget = it },
                            onArchiveThread = { archiveTarget = it },
                            onSetThreadPinned = onSetThreadPinned,
                            onOpenArchivedTasks = {
                                showArchivedTasks = true
                                onLoadArchivedThreads()
                                scope.launch { drawerState.close() }
                            },
                            onOpenConnections = {
                                scope.launch { drawerState.close() }
                                onOpenConnections()
                            },
                        )
                    }
                },
            ) {
                WorkspaceContent(
                    state = state,
                    showMenu = true,
                    onMenu = { scope.launch { drawerState.open() } },
                    onSend = onSend,
                    onStop = onStop,
                    onLoadOlderHistory = onLoadOlderHistory,
                    onCompactThread = onCompactThread,
                    onForkThread = onForkThread,
                    onStartReview = onStartReview,
                    onRunInit = onRunInit,
                    onLoadMcpStatus = onLoadMcpStatus,
                    onReloadMcpServers = onReloadMcpServers,
                    onStartMcpLogin = onStartMcpLogin,
                    onSubmitFeedback = onSubmitFeedback,
                    onSetGoal = onSetGoal,
                    onSetGoalStatus = onSetGoalStatus,
                    onClearGoal = onClearGoal,
                    onShowStatus = onShowStatus,
                    onNewThread = onNewThread,
                    onRenameCurrentThread = { state.selectedThreadId?.let { id -> state.threads.firstOrNull { it.id == id } }?.let { renameTarget = it } },
                    onArchiveCurrentThread = { state.selectedThreadId?.let { id -> state.threads.firstOrNull { it.id == id } }?.let { archiveTarget = it } },
                    onSetModel = onSetModel,
                    onSetReasoningEffort = onSetReasoningEffort,
                    onSetServiceTier = onSetServiceTier,
                    onSetCollaborationMode = onSetCollaborationMode,
                    onSetPermissionProfile = onSetPermissionProfile,
                    onSetPermissionMode = onSetPermissionMode,
                    onLoadRemoteDirectory = onLoadRemoteDirectory,
                    onClearRemoteDirectory = onClearRemoteDirectory,
                    onStartLogin = onStartLogin,
                    onOpenConnections = onOpenConnections,
                    onDismissNotice = onDismissNotice,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    state.pendingApproval?.let { approval ->
        ApprovalDialog(
            approval = approval,
            onDecision = onApproval,
        )
    }
    state.pendingHostKeyFingerprint?.let { fingerprint ->
        HostKeyConfirmationDialog(
            host = state.activeConnection?.let { "${it.host}:${it.port}" }.orEmpty(),
            fingerprint = fingerprint,
            onTrust = onTrustHostKey,
            onReject = onRejectHostKey,
        )
    }
    state.remoteDeviceLogin?.let { login ->
        RemoteDeviceLoginDialog(
            login = login,
            onOpen = { uriHandler.openUri(login.verificationUrl) },
            onCancel = onCancelLogin,
        )
    }
    renameTarget?.let { thread ->
        RenameThreadDialog(
            thread = thread,
            onDismiss = { renameTarget = null },
            onRename = { name ->
                onRenameThread(thread, name)
                renameTarget = null
            },
        )
    }
    archiveTarget?.let { thread ->
        ArchiveThreadDialog(
            thread = thread,
            onDismiss = { archiveTarget = null },
            onArchive = {
                onArchiveThread(thread)
                archiveTarget = null
            },
        )
    }
    if (showArchivedTasks) {
        ArchivedTasksDialog(
            state = state,
            onDismiss = { showArchivedTasks = false },
            onRefresh = onLoadArchivedThreads,
            onUnarchive = onUnarchiveThread,
            onDelete = { deleteArchivedTarget = it },
        )
    }
    deleteArchivedTarget?.let { thread ->
        DeleteArchivedThreadDialog(
            thread = thread,
            onDismiss = { deleteArchivedTarget = null },
            onDelete = {
                onDeleteArchivedThread(thread)
                deleteArchivedTarget = null
            },
        )
    }
}

@Composable
private fun WorkspaceSidebar(
    state: AppUiState,
    modifier: Modifier,
    onNewThread: () -> Unit,
    onSelectProject: (RemoteProject) -> Unit,
    onSelectThread: (RemoteThread) -> Unit,
    onRenameThread: (RemoteThread) -> Unit,
    onArchiveThread: (RemoteThread) -> Unit,
    onSetThreadPinned: (RemoteThread, Boolean) -> Unit,
    onOpenArchivedTasks: () -> Unit,
    onOpenConnections: () -> Unit,
) {
    val expandedProjects = remember(state.activeConnection?.id) { mutableStateMapOf<String, Boolean>() }
    var searchQuery by remember(state.activeConnection?.id) { mutableStateOf("") }
    val displayedProjects = remember(state.projects, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            state.projects
        } else {
            state.projects.mapNotNull { project ->
                val projectMatches = project.name.contains(query, true) || project.path.contains(query, true)
                val matchingThreads = if (projectMatches) project.threads else project.threads.filter {
                    it.title.contains(query, true) || it.cwd.contains(query, true)
                }
                project.copy(threads = matchingThreads).takeIf { matchingThreads.isNotEmpty() || projectMatches }
            }
        }
    }
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(9.dp))
            Text("Codex", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        SidebarAction(Icons.Outlined.Add, "New task", onNewThread)
        SidebarAction(Icons.Outlined.Archive, "Archived tasks", onOpenArchivedTasks)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            placeholder = { Text("Search tasks") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search", modifier = Modifier.size(17.dp))
                    }
                }
            },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "PROJECTS",
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
            if (displayedProjects.isEmpty()) {
                item(key = "empty-projects") {
                    Text(
                        if (searchQuery.isBlank()) "No Codex conversations found" else "No matching tasks",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            displayedProjects.forEach { project ->
                item(key = "project-${project.id}") {
                    val expanded = searchQuery.isNotBlank() || expandedProjects[project.id] == true
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp))
                            .clickable { expandedProjects[project.id] = !expanded }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                            contentDescription = if (expanded) "Collapse project" else "Expand project",
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                project.name,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (expanded) Text(
                                project.path,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            project.threads.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (searchQuery.isNotBlank() || expandedProjects[project.id] == true) {
                    item(key = "new-${project.id}") {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp))
                                .clickable {
                                    onSelectProject(project)
                                    onNewThread()
                                }
                                .padding(start = 38.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("New task", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(project.threads, key = { "thread-${it.id}" }) { thread ->
                        ThreadSidebarRow(
                            thread = thread,
                            selected = thread.id == state.selectedThreadId,
                            onSelect = { onSelectThread(thread) },
                            onRename = { onRenameThread(thread) },
                            onArchive = { onArchiveThread(thread) },
                            onSetPinned = { onSetThreadPinned(thread, it) },
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenConnections).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Computer, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.activeConnection?.name ?: "Remote host", style = MaterialTheme.typography.labelLarge)
                Text(
                    state.activeConnection?.let { "${it.username}@${it.host}" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    state.remoteAccount?.let { account ->
                        account.email ?: account.planType ?: account.type ?: "Codex sign-in required"
                    }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.remoteAccount?.canRunCodex == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Outlined.Settings, contentDescription = "连接设置", modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun ThreadSidebarRow(
    thread: RemoteThread,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onSetPinned: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(start = 38.dp, end = 3.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                thread.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatThreadTime(thread.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (thread.isPinned) {
            Icon(
                Icons.Outlined.PushPin,
                contentDescription = "Pinned",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
        }
        if (thread.status.contains("active", true)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Task actions", modifier = Modifier.size(17.dp))
            }
            ThreadActionsMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onRename = {
                    menuOpen = false
                    onRename()
                },
                onArchive = {
                    menuOpen = false
                    onArchive()
                },
                isPinned = thread.isPinned,
                onSetPinned = {
                    menuOpen = false
                    onSetPinned(it)
                },
            )
        }
    }
}

@Composable
private fun ThreadActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onCompact: (() -> Unit)? = null,
    compactEnabled: Boolean = true,
    onFork: (() -> Unit)? = null,
    onReview: (() -> Unit)? = null,
    isPinned: Boolean? = null,
    onSetPinned: (Boolean) -> Unit = {},
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            onClick = onRename,
        )
        onCompact?.let { compact ->
            DropdownMenuItem(
                text = { Text("Compact context") },
                leadingIcon = { Icon(Icons.Outlined.Compress, contentDescription = null) },
                enabled = compactEnabled,
                onClick = compact,
            )
        }
        onFork?.let { fork ->
            DropdownMenuItem(
                text = { Text("Fork task") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CallSplit, contentDescription = null) },
                onClick = fork,
            )
        }
        onReview?.let { review ->
            DropdownMenuItem(
                text = { Text("Review changes") },
                leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                onClick = review,
            )
        }
        if (isPinned != null) {
            DropdownMenuItem(
                text = { Text(if (isPinned) "Unpin" else "Pin") },
                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                onClick = { onSetPinned(!isPinned) },
            )
        }
        DropdownMenuItem(
            text = { Text("Archive") },
            leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
            onClick = onArchive,
        )
    }
}

@Composable
private fun SidebarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(5.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceContent(
    state: AppUiState,
    showMenu: Boolean,
    onMenu: () -> Unit,
    onSend: (String, List<ComposerMention>, List<ComposerImageAttachment>, Boolean) -> Unit,
    onStop: () -> Unit,
    onLoadOlderHistory: () -> Unit,
    onCompactThread: () -> Unit,
    onForkThread: () -> Unit,
    onStartReview: (ReviewTargetKind, String) -> Unit,
    onRunInit: () -> Unit,
    onLoadMcpStatus: () -> Unit,
    onReloadMcpServers: () -> Unit,
    onStartMcpLogin: (String) -> Unit,
    onSubmitFeedback: (String, String) -> Unit,
    onSetGoal: (String) -> Unit,
    onSetGoalStatus: (ThreadGoalStatus) -> Unit,
    onClearGoal: () -> Unit,
    onShowStatus: () -> Unit,
    onNewThread: () -> Unit,
    onRenameCurrentThread: () -> Unit,
    onArchiveCurrentThread: () -> Unit,
    onSetModel: (String) -> Unit,
    onSetReasoningEffort: (String) -> Unit,
    onSetServiceTier: (String?) -> Unit,
    onSetCollaborationMode: (String) -> Unit,
    onSetPermissionProfile: (String?) -> Unit,
    onSetPermissionMode: (PermissionMode) -> Unit,
    onLoadRemoteDirectory: (String) -> Unit,
    onClearRemoteDirectory: () -> Unit,
    onStartLogin: () -> Unit,
    onOpenConnections: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showGoalEditor by remember(state.selectedThreadId) { mutableStateOf(false) }
    var pendingGoalObjective by remember(state.selectedThreadId) { mutableStateOf<String?>(null) }
    var showReviewDialog by remember(state.selectedThreadId) { mutableStateOf(false) }
    var showMcpDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    val conversationKey = state.selectedThreadId ?: "project:${state.selectedProjectPath.orEmpty()}"
    val conversationListState = remember(conversationKey) { LazyListState() }
    var followLatest by remember(conversationKey) { mutableStateOf(true) }
    var hasPositionedConversation by remember(conversationKey) { mutableStateOf(false) }
    val currentThread = state.threads.firstOrNull { it.id == state.selectedThreadId }
    val canCompose = state.connectionStatus == ConnectionStatus.CONNECTED &&
        state.remoteAccount?.canRunCodex == true &&
        state.models.isNotEmpty() &&
        (state.selectedProjectPath?.isNotBlank() == true || state.selectedThreadId != null)
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            onDismissNotice()
        }
    }
    LaunchedEffect(state.threadGoal?.objective, state.isGoalLoading, state.goalError) {
        val pending = pendingGoalObjective ?: return@LaunchedEffect
        if (!state.isGoalLoading && state.goalError == null && state.threadGoal?.objective == pending) {
            showGoalEditor = false
            pendingGoalObjective = null
        }
    }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                modifier = Modifier.statusBarsPadding(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showMenu) {
                            IconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, contentDescription = "打开会话") }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                currentThread?.title
                                    ?: state.projects.firstOrNull { it.path == state.selectedProjectPath }?.name
                                    ?: state.activeConnection?.name.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            ConnectionIndicator(state)
                        }
                        if (currentThread != null) {
                            ThreadHeaderOverflow(
                                onRename = onRenameCurrentThread,
                                onCompact = onCompactThread,
                                compactEnabled = !state.isTurnRunning,
                                onFork = onForkThread,
                                onReview = { showReviewDialog = true },
                                onArchive = onArchiveCurrentThread,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
    ) { padding ->
        when (state.connectionStatus) {
            ConnectionStatus.CONNECTING -> ConnectionState(
                icon = null,
                title = "Connecting over SSH",
                detail = state.connectionMessage,
                loading = true,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            ConnectionStatus.ERROR -> ConnectionState(
                icon = Icons.Outlined.ErrorOutline,
                title = "Connection failed",
                detail = state.connectionMessage,
                loading = false,
                action = onOpenConnections,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            ConnectionStatus.CONNECTED -> when {
                state.remoteAccount?.canRunCodex != true -> RemoteAuthenticationState(
                    loading = state.isLoginStarting,
                    onLogin = onStartLogin,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
                state.models.isEmpty() -> ConnectionState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = "No remote models",
                    detail = "远端 app-server 没有返回可用模型。请检查远端 Codex 版本和模型提供方配置。",
                    loading = false,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
                else -> Column(Modifier.fillMaxSize().padding(padding)) {
                    Conversation(
                        state = state,
                        listState = conversationListState,
                        followLatest = followLatest,
                        onFollowLatestChange = { followLatest = it },
                        hasPositionedConversation = hasPositionedConversation,
                        onConversationPositioned = { hasPositionedConversation = true },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onLoadOlderHistory = onLoadOlderHistory,
                    )
                    if (canCompose) {
                        Composer(
                            state = state,
                            onSend = onSend,
                            onStop = onStop,
                            onCompactThread = onCompactThread,
                            onForkThread = onForkThread,
                            onOpenReview = {
                                if (state.selectedThreadId == null) {
                                    onStartReview(ReviewTargetKind.UNCOMMITTED_CHANGES, "")
                                } else {
                                    showReviewDialog = true
                                }
                            },
                            onRunInit = onRunInit,
                            onOpenMcpStatus = {
                                showMcpDialog = true
                                onLoadMcpStatus()
                            },
                            onOpenFeedback = { showFeedbackDialog = true },
                            onEditGoal = { showGoalEditor = true },
                            onSetGoalStatus = onSetGoalStatus,
                            onClearGoal = onClearGoal,
                            onShowStatus = {
                                showStatusDialog = true
                                onShowStatus()
                            },
                            onNewThread = onNewThread,
                            onRenameCurrentThread = onRenameCurrentThread,
                            onArchiveCurrentThread = onArchiveCurrentThread,
                            onSetModel = onSetModel,
                            onSetReasoningEffort = onSetReasoningEffort,
                            onSetServiceTier = onSetServiceTier,
                            onSetCollaborationMode = onSetCollaborationMode,
                            onSetPermissionProfile = onSetPermissionProfile,
                            onSetPermissionMode = onSetPermissionMode,
                            onLoadRemoteDirectory = onLoadRemoteDirectory,
                            onClearRemoteDirectory = onClearRemoteDirectory,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            ConnectionStatus.DISCONNECTED -> ConnectionState(
                icon = Icons.Outlined.Computer,
                title = "Disconnected",
                detail = state.activeConnection?.name.orEmpty(),
                loading = false,
                action = onOpenConnections,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
    if (showGoalEditor) {
        GoalEditorDialog(
            initialObjective = state.threadGoal?.objective.orEmpty(),
            isSaving = state.isGoalLoading,
            error = state.goalError,
            onDismiss = {
                showGoalEditor = false
                pendingGoalObjective = null
            },
            onSave = { objective ->
                pendingGoalObjective = objective.trim()
                onSetGoal(objective)
            },
        )
    }
    if (showReviewDialog) {
        ReviewDialog(
            onDismiss = { showReviewDialog = false },
            onStart = { kind, value ->
                showReviewDialog = false
                onStartReview(kind, value)
            },
        )
    }
    if (showMcpDialog) {
        McpStatusDialog(
            state = state,
            onDismiss = { showMcpDialog = false },
            onRefresh = onReloadMcpServers,
            onLogin = onStartMcpLogin,
        )
    }
    if (showStatusDialog) {
        RemoteStatusDialog(state = state, onDismiss = { showStatusDialog = false })
    }
    if (showFeedbackDialog) {
        FeedbackDialog(
            isSubmitting = state.isFeedbackSubmitting,
            error = state.feedbackError,
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { classification, reason ->
                showFeedbackDialog = false
                onSubmitFeedback(classification, reason)
            },
        )
    }
}

@Composable
private fun ThreadHeaderOverflow(
    onRename: () -> Unit,
    onCompact: () -> Unit,
    compactEnabled: Boolean,
    onFork: () -> Unit,
    onReview: () -> Unit,
    onArchive: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Task actions")
        }
        ThreadActionsMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            onRename = {
                expanded = false
                onRename()
            },
            onCompact = {
                expanded = false
                onCompact()
            },
            compactEnabled = compactEnabled,
            onFork = {
                expanded = false
                onFork()
            },
            onReview = {
                expanded = false
                onReview()
            },
            onArchive = {
                expanded = false
                onArchive()
            },
        )
    }
}

@Composable
private fun ConnectionIndicator(state: AppUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val color = when (state.connectionStatus) {
            ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.secondary
            ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            state.activeConnection?.host.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

internal data class TimelinePresentationItem(
    val item: TimelineItem,
    val sourceItemIds: List<String>,
)

private data class HistoryScrollAnchor(val itemId: String, val itemOffset: Int)

internal fun groupConsecutiveCommands(timeline: List<TimelineItem>): List<TimelinePresentationItem> = buildList {
    var index = 0
    while (index < timeline.size) {
        val item = timeline[index]
        if (item.kind != TimelineKind.COMMAND) {
            add(TimelinePresentationItem(item, listOf(item.id)))
            index++
            continue
        }
        var end = index + 1
        while (end < timeline.size && timeline[end].kind == TimelineKind.COMMAND) end++
        val commands = timeline.subList(index, end)
        if (commands.size == 1) {
            add(TimelinePresentationItem(item, listOf(item.id)))
        } else {
            val status = when {
                commands.any { it.status.isTimelineItemRunning() } -> "inProgress"
                commands.any { it.status.equals("failed", ignoreCase = true) } -> "failed"
                else -> "completed"
            }
            val body = commands.mapIndexed { commandIndex, command ->
                buildString {
                    append(commandIndex + 1)
                    append(". ")
                    append(command.title.ifBlank { "Command" })
                    if (command.body.isNotBlank()) {
                        append('\n')
                        append(command.body.trimEnd())
                    }
                }
            }.joinToString("\n\n")
            add(
                TimelinePresentationItem(
                    item = TimelineItem(
                        id = "command-group:${commands.first().id}",
                        kind = TimelineKind.COMMAND,
                        title = "运行了多个命令",
                        body = body,
                        status = status,
                    ),
                    sourceItemIds = commands.map(TimelineItem::id),
                ),
            )
        }
        index = end
    }
}

@Composable
private fun Conversation(
    state: AppUiState,
    listState: LazyListState,
    followLatest: Boolean,
    onFollowLatestChange: (Boolean) -> Unit,
    hasPositionedConversation: Boolean,
    onConversationPositioned: () -> Unit,
    modifier: Modifier,
    onLoadOlderHistory: () -> Unit,
) {
    val conversationKey = state.selectedThreadId ?: "project:${state.selectedProjectPath.orEmpty()}"
    val scrollScope = rememberCoroutineScope()
    val presentationTimeline = remember(state.timeline) { groupConsecutiveCommands(state.timeline) }
    var pendingHistoryAnchor by remember(conversationKey) { mutableStateOf<HistoryScrollAnchor?>(null) }
    var programmaticScroll by remember(conversationKey) { mutableStateOf(false) }
    val historyHeaderVisible = state.hasOlderHistory ||
        state.isOlderHistoryLoading ||
        state.olderHistoryError != null

    fun visibleHistoryAnchor(): HistoryScrollAnchor? {
        val firstTimelineItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key != HISTORY_CONTROL_KEY &&
                item.key != WORKING_ITEM_KEY &&
                item.key != BOTTOM_SENTINEL_KEY
        } ?: return null
        val itemId = firstTimelineItem.key as? String ?: return null
        val sourceItemId = presentationTimeline
            .firstOrNull { it.item.id == itemId }
            ?.sourceItemIds
            ?.lastOrNull()
            ?: itemId
        return HistoryScrollAnchor(sourceItemId, firstTimelineItem.offset)
    }

    if (state.timeline.isEmpty()) {
        val project = state.projects.firstOrNull { it.path == state.selectedProjectPath }
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Outlined.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (state.isBusy) "正在加载最近消息" else project?.name ?: "No remote Codex history",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    project?.path ?: state.connectionMessage,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.hasOlderHistory && !state.isBusy) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onLoadOlderHistory) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("加载更早消息")
                    }
                }
            }
        }
        return
    }

    val requestOlderHistory = {
        if (state.hasOlderHistory && !state.isOlderHistoryLoading && pendingHistoryAnchor == null) {
            pendingHistoryAnchor = visibleHistoryAnchor()
            onLoadOlderHistory()
        }
    }

    LaunchedEffect(
        conversationKey,
        presentationTimeline.firstOrNull()?.item?.id,
        presentationTimeline.lastOrNull()?.item?.id,
        hasPositionedConversation,
    ) {
        if (hasPositionedConversation || presentationTimeline.isEmpty()) return@LaunchedEffect
        val leadingItems = if (historyHeaderVisible) 1 else 0
        val workingItems = if (state.isTurnRunning && presentationTimeline.lastOrNull()?.item?.kind != TimelineKind.AGENT) 1 else 0
        programmaticScroll = true
        try {
            listState.scrollToAbsoluteBottom(leadingItems + presentationTimeline.size + workingItems)
            onFollowLatestChange(true)
            onConversationPositioned()
        } finally {
            programmaticScroll = false
        }
    }
    LaunchedEffect(listState, conversationKey, hasPositionedConversation) {
        if (!hasPositionedConversation) return@LaunchedEffect
        snapshotFlow {
            listState.isScrollInProgress to listState.canScrollForward
        }.distinctUntilChanged().collect { (isScrolling, canScrollForward) ->
            if (!programmaticScroll && isScrolling) onFollowLatestChange(!canScrollForward)
        }
    }
    LaunchedEffect(listState, conversationKey, hasPositionedConversation, followLatest) {
        if (!hasPositionedConversation || !followLatest) return@LaunchedEffect
        snapshotFlow {
            Triple(
                listState.canScrollForward,
                listState.isScrollInProgress,
                listState.layoutInfo.totalItemsCount,
            )
        }.distinctUntilChanged().collect { (canScrollForward, isScrolling, totalItemsCount) ->
            if (!canScrollForward || isScrolling || programmaticScroll || totalItemsCount == 0) {
                return@collect
            }
            // Markwon's AndroidView can grow after the LazyColumn's first positioning pass.
            withFrameNanos { }
            if (!listState.canScrollForward || listState.isScrollInProgress || programmaticScroll) {
                return@collect
            }
            programmaticScroll = true
            try {
                listState.scrollToAbsoluteBottom(totalItemsCount - 1)
            } finally {
                programmaticScroll = false
            }
        }
    }
    LaunchedEffect(
        listState,
        state.selectedThreadId,
        state.hasOlderHistory,
        state.isOlderHistoryLoading,
        pendingHistoryAnchor,
    ) {
        if (!state.hasOlderHistory || state.isOlderHistoryLoading || pendingHistoryAnchor != null) {
            return@LaunchedEffect
        }
        snapshotFlow { listState.firstVisibleItemIndex to listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (firstVisibleIndex, isScrolling) ->
                if (isScrolling && firstVisibleIndex <= HISTORY_PREFETCH_INDEX) requestOlderHistory()
            }
    }
    LaunchedEffect(
        listState,
        state.selectedThreadId,
        state.isOlderHistoryLoading,
        pendingHistoryAnchor != null,
        presentationTimeline.firstOrNull()?.item?.id,
    ) {
        if (!state.isOlderHistoryLoading || pendingHistoryAnchor == null) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                item.key != HISTORY_CONTROL_KEY &&
                    item.key != WORKING_ITEM_KEY &&
                    item.key != BOTTOM_SENTINEL_KEY
            }?.let { item -> item.key to item.offset }
        }.distinctUntilChanged().collect {
            visibleHistoryAnchor()?.let { anchor -> pendingHistoryAnchor = anchor }
        }
    }
    LaunchedEffect(
        state.selectedThreadId,
        state.isOlderHistoryLoading,
        state.olderHistoryError,
        presentationTimeline.firstOrNull()?.item?.id,
    ) {
        val anchor = pendingHistoryAnchor ?: return@LaunchedEffect
        if (state.isOlderHistoryLoading) return@LaunchedEffect
        val timelineIndex = presentationTimeline.indexOfFirst { anchor.itemId in it.sourceItemIds }
        if (timelineIndex >= 0) {
            val leadingItems = if (historyHeaderVisible) 1 else 0
            programmaticScroll = true
            try {
                listState.scrollToItem(timelineIndex + leadingItems)
                withFrameNanos { }
                listState.scrollBy(-anchor.itemOffset.toFloat())
            } finally {
                programmaticScroll = false
            }
        }
        pendingHistoryAnchor = null
    }
    LaunchedEffect(
        state.selectedThreadId,
        presentationTimeline.size,
        presentationTimeline.lastOrNull()?.item?.body?.length,
        state.isTurnRunning,
        historyHeaderVisible,
        pendingHistoryAnchor,
        followLatest,
        hasPositionedConversation,
    ) {
        if (!hasPositionedConversation || presentationTimeline.isEmpty() || !followLatest ||
            state.isOlderHistoryLoading || pendingHistoryAnchor != null
        ) {
            return@LaunchedEffect
        }
        val leadingItems = if (historyHeaderVisible) 1 else 0
        val workingItems = if (state.isTurnRunning && presentationTimeline.lastOrNull()?.item?.kind != TimelineKind.AGENT) 1 else 0
        programmaticScroll = true
        try {
            listState.scrollToAbsoluteBottom(leadingItems + presentationTimeline.size + workingItems)
        } finally {
            programmaticScroll = false
        }
    }

    Box(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("conversation-list"),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        if (historyHeaderVisible) {
            item(key = HISTORY_CONTROL_KEY) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("history-control"),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        state.isOlderHistoryLoading -> CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        state.olderHistoryError != null && state.hasOlderHistory -> TextButton(onClick = requestOlderHistory) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("重试加载更早消息", maxLines = 1)
                        }
                        state.olderHistoryError != null -> Text(
                            "无法继续加载更早消息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        state.hasOlderHistory -> TextButton(onClick = requestOlderHistory) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("加载更早消息")
                        }
                    }
                }
            }
        }
            items(presentationTimeline, key = { it.item.id }) { presentation ->
                Box(
                    Modifier.fillMaxWidth().testTag("timeline-item-${presentation.item.id}"),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    TimelineRow(presentation.item, Modifier.fillMaxWidth().widthIn(max = 820.dp))
                }
            }
            if (state.isTurnRunning && presentationTimeline.lastOrNull()?.item?.kind != TimelineKind.AGENT) {
                item(key = WORKING_ITEM_KEY) {
                    Row(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(9.dp))
                        Text("Working", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = BOTTOM_SENTINEL_KEY) {
                Spacer(Modifier.fillMaxWidth().height(1.dp).testTag("conversation-bottom"))
            }
        }
        if (!followLatest) {
            SmallFloatingActionButton(
                onClick = {
                    scrollScope.launch {
                        val lastIndex = listState.layoutInfo.totalItemsCount - 1
                        if (lastIndex >= 0) {
                            programmaticScroll = true
                            try {
                                listState.scrollToAbsoluteBottom(lastIndex, animate = true)
                                onFollowLatestChange(true)
                            } finally {
                                programmaticScroll = false
                            }
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                    .testTag("conversation-bottom-button"),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "回到最新消息")
            }
        }
    }
}

private const val HISTORY_CONTROL_KEY = "history-control"
private const val WORKING_ITEM_KEY = "working"
private const val BOTTOM_SENTINEL_KEY = "bottom-sentinel"
private const val HISTORY_PREFETCH_INDEX = 2

private suspend fun LazyListState.scrollToAbsoluteBottom(lastIndex: Int, animate: Boolean = false) {
    if (lastIndex < 0) return
    if (animate) animateScrollToItem(lastIndex) else scrollToItem(lastIndex)
    var attempts = 0
    var settledFrames = 0
    while (attempts++ < MAX_BOTTOM_SCROLL_STEPS) {
        withFrameNanos { }
        if (!canScrollForward) {
            settledFrames++
            if (settledFrames >= BOTTOM_LAYOUT_SETTLE_FRAMES) return
            continue
        }
        settledFrames = 0
        val layout = layoutInfo
        val viewportHeight = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)
        scrollBy(viewportHeight.toFloat())
    }
}

private const val MAX_BOTTOM_SCROLL_STEPS = 1_000
private const val BOTTOM_LAYOUT_SETTLE_FRAMES = 8

@Composable
private fun TimelineRow(item: TimelineItem, modifier: Modifier) {
    when (item.kind) {
        TimelineKind.USER -> Row(modifier, horizontalArrangement = Arrangement.End) {
            Surface(
                modifier = Modifier.widthIn(max = 620.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(7.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    SelectionContainer { Text(item.body, style = MaterialTheme.typography.bodyLarge) }
                    if (item.isGoal) {
                        Spacer(Modifier.height(7.dp))
                        Row(
                            modifier = Modifier.testTag("timeline-goal-${item.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "作为目标发送",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        TimelineKind.AGENT -> Column(modifier) {
            MarkdownBody(item.body, Modifier.testTag("timeline-agent-${item.id}"))
        }
        TimelineKind.REASONING -> ExpandableTool(
            modifier,
            Icons.Outlined.SmartToy,
            item.title.ifBlank { "Reasoning" },
            item.body,
            item.status,
            expansionKey = item.id,
            autoCollapseOnComplete = true,
        )
        TimelineKind.PLAN -> ExpandableTool(
            modifier,
            Icons.AutoMirrored.Outlined.List,
            item.title.ifBlank { "Plan" },
            item.body,
            item.status,
            expansionKey = item.id,
            autoCollapseOnComplete = true,
        )
        TimelineKind.COMMAND -> ExpandableTool(
            modifier, Icons.Outlined.Terminal, item.title.ifBlank { "Command" }, item.body, item.status,
            expansionKey = item.id, mono = true, autoCollapseOnComplete = true,
        )
        TimelineKind.FILE_CHANGE -> FileChangesTool(item, modifier)
        TimelineKind.TOOL -> ExpandableTool(
            modifier, Icons.Outlined.Build, item.title.ifBlank { "Tool" }, item.body, item.status,
            expansionKey = item.id, mono = true, autoCollapseOnComplete = true,
        )
        TimelineKind.REVIEW -> ExpandableTool(
            modifier, Icons.Outlined.Code, item.title.ifBlank { "Code review" }, item.body, item.status,
            expansionKey = item.id, autoCollapseOnComplete = true,
        )
        TimelineKind.COMPACTION -> Row(
            modifier = modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(Modifier.weight(1f))
            Icon(
                Icons.Outlined.Compress,
                contentDescription = null,
                modifier = Modifier.padding(horizontal = 8.dp).size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                item.title.ifBlank { "Context compacted" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.weight(1f).padding(start = 8.dp))
        }
        TimelineKind.ERROR -> ExpandableTool(
            modifier, Icons.Outlined.ErrorOutline, item.title.ifBlank { "Error" }, item.body, item.status,
            expansionKey = item.id,
        )
    }
}

@Composable
private fun FileChangesTool(item: TimelineItem, modifier: Modifier) {
    var expanded by remember(item.id) { mutableStateOf(item.status.isTimelineItemRunning()) }
    LaunchedEffect(item.id, item.status) {
        expanded = item.status.isTimelineItemRunning()
    }
    val counts = item.fileChanges.fold(0 to 0) { total, change ->
        val changeCounts = diffLineCounts(change.diff)
        total.first + changeCounts.first to total.second + changeCounts.second
    }
    val title = when (item.fileChanges.size) {
        0 -> item.title.ifBlank { "File changes" }
        1 -> item.fileChanges.first().path
        else -> "已编辑 ${item.fileChanges.size} 个文件"
    }
    Surface(
        modifier = modifier.animateContentSize().testTag("file-changes-${item.id}"),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (counts.first > 0) {
                    Text("+${counts.first}", style = MaterialTheme.typography.labelMedium, color = CodexGreen)
                    Spacer(Modifier.width(6.dp))
                }
                if (counts.second > 0) {
                    Text("-${counts.second}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(7.dp))
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                    contentDescription = if (expanded) "收起文件修改" else "展开文件修改",
                    modifier = Modifier.size(18.dp),
                )
            }
            if (expanded) {
                HorizontalDivider()
                if (item.fileChanges.isEmpty()) {
                    Text(
                        item.body.ifBlank { "No file details" },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        style = MonoText,
                    )
                } else {
                    item.fileChanges.forEachIndexed { index, change ->
                        FileChangeBlock(change)
                        if (index != item.fileChanges.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FileChangeBlock(change: FileChangeSummary) {
    val counts = diffLineCounts(change.diff)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                change.path,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    change.kind.displayFileChangeKind(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (counts.first > 0 || counts.second > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "+${counts.first}  -${counts.second}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (change.diff.isNotBlank()) {
            Column(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                change.diff.lineSequence().forEach { line ->
                    val background = when {
                        line.startsWith("+") && !line.startsWith("+++") -> DiffGreen.copy(alpha = 0.55f)
                        line.startsWith("-") && !line.startsWith("---") -> DiffRed.copy(alpha = 0.55f)
                        else -> Color.Transparent
                    }
                    Text(
                        line.ifEmpty { " " },
                        modifier = Modifier.widthIn(min = 680.dp).background(background)
                            .padding(horizontal = 12.dp, vertical = 1.dp),
                        style = MonoText,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

internal fun diffLineCounts(diff: String): Pair<Int, Int> {
    var additions = 0
    var deletions = 0
    diff.lineSequence().forEach { line ->
        when {
            line.startsWith("+") && !line.startsWith("+++") -> additions++
            line.startsWith("-") && !line.startsWith("---") -> deletions++
        }
    }
    return additions to deletions
}

private fun String.displayFileChangeKind(): String = when (lowercase()) {
    "add", "added", "create", "created" -> "Added"
    "delete", "deleted", "remove", "removed" -> "Deleted"
    "rename", "renamed", "move", "moved" -> "Renamed"
    else -> "Modified"
}

@Composable
private fun ExpandableTool(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    body: String,
    status: String,
    expansionKey: String,
    mono: Boolean = false,
    autoCollapseOnComplete: Boolean = false,
) {
    var expanded by remember(expansionKey) {
        mutableStateOf(autoCollapseOnComplete && status.isTimelineItemRunning())
    }
    LaunchedEffect(expansionKey, status, autoCollapseOnComplete) {
        if (autoCollapseOnComplete) expanded = status.isTimelineItemRunning()
    }
    Surface(
        modifier = modifier.animateContentSize().testTag("timeline-tool-$expansionKey"),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(9.dp))
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(18.dp),
                )
            }
            if (expanded && body.isNotBlank()) {
                HorizontalDivider()
                SelectionContainer {
                    Text(
                        body,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                            .testTag("timeline-tool-body-$expansionKey"),
                        style = if (mono) MonoText else MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

internal fun String.isTimelineItemRunning(): Boolean =
    equals("inProgress", ignoreCase = true) ||
        equals("in_progress", ignoreCase = true) ||
        equals("running", ignoreCase = true) ||
        equals("started", ignoreCase = true)

@Composable
private fun MarkdownBody(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val latexTextSize = with(density) { MaterialTheme.typography.bodyLarge.fontSize.toPx() }
    val markwon = remember(context, latexTextSize) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(latexTextSize) { builder -> builder.inlinesEnabled(true) })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .build()
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val rendered = remember(text) { normalizeLatexMarkdown(text) }
    AndroidView(
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                includeFontPadding = false
                setLineSpacing(0f, 1.12f)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            markwon.setMarkdown(textView, rendered)
        },
        modifier = modifier.fillMaxWidth(),
    )
}

internal fun normalizeLatexMarkdown(markdown: String): String {
    if ('$' !in markdown) return markdown
    val output = StringBuilder(markdown.length + 16)
    var index = 0
    var fenceCharacter: Char? = null
    var fenceLength = 0
    var inlineBackticks = 0
    while (index < markdown.length) {
        val character = markdown[index]
        if (character == '`' || character == '~') {
            var runEnd = index + 1
            while (runEnd < markdown.length && markdown[runEnd] == character) runEnd++
            val runLength = runEnd - index
            if (fenceCharacter != null) {
                if (character == fenceCharacter && runLength >= fenceLength) {
                    fenceCharacter = null
                    fenceLength = 0
                }
            } else if (inlineBackticks == 0 && runLength >= 3) {
                fenceCharacter = character
                fenceLength = runLength
            } else if (character == '`') {
                inlineBackticks = when {
                    inlineBackticks == 0 -> runLength
                    inlineBackticks == runLength -> 0
                    else -> inlineBackticks
                }
            }
            output.append(markdown, index, runEnd)
            index = runEnd
            continue
        }
        if (fenceCharacter != null || inlineBackticks > 0 || character != '$' || markdown.isEscapedAt(index)) {
            output.append(character)
            index++
            continue
        }
        if (markdown.startsWith("$$", index)) {
            output.append("$$")
            index += 2
            continue
        }
        if (markdown.getOrNull(index + 1)?.isWhitespace() != false) {
            output.append(character)
            index++
            continue
        }
        var closing = index + 1
        while (closing < markdown.length && markdown[closing] != '\n') {
            if (markdown[closing] == '$' && !markdown.isEscapedAt(closing) &&
                markdown.getOrNull(closing - 1)?.isWhitespace() == false &&
                markdown.getOrNull(closing + 1) != '$'
            ) {
                break
            }
            closing++
        }
        if (closing < markdown.length && markdown[closing] == '$') {
            output.append("$$")
            output.append(markdown, index + 1, closing)
            output.append("$$")
            index = closing + 1
        } else {
            output.append(character)
            index++
        }
    }
    return output.toString()
}

private fun String.isEscapedAt(index: Int): Boolean {
    var slashes = 0
    var cursor = index - 1
    while (cursor >= 0 && this[cursor] == '\\') {
        slashes++
        cursor--
    }
    return slashes % 2 == 1
}

@Composable
private fun Composer(
    state: AppUiState,
    onSend: (String, List<ComposerMention>, List<ComposerImageAttachment>, Boolean) -> Unit,
    onStop: () -> Unit,
    onCompactThread: () -> Unit,
    onForkThread: () -> Unit,
    onOpenReview: () -> Unit,
    onRunInit: () -> Unit,
    onOpenMcpStatus: () -> Unit,
    onOpenFeedback: () -> Unit,
    onEditGoal: () -> Unit,
    onSetGoalStatus: (ThreadGoalStatus) -> Unit,
    onClearGoal: () -> Unit,
    onShowStatus: () -> Unit,
    onNewThread: () -> Unit,
    onRenameCurrentThread: () -> Unit,
    onArchiveCurrentThread: () -> Unit,
    onSetModel: (String) -> Unit,
    onSetReasoningEffort: (String) -> Unit,
    onSetServiceTier: (String?) -> Unit,
    onSetCollaborationMode: (String) -> Unit,
    onSetPermissionProfile: (String?) -> Unit,
    onSetPermissionMode: (PermissionMode) -> Unit,
    onLoadRemoteDirectory: (String) -> Unit,
    onClearRemoteDirectory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(state.selectedThreadId) { mutableStateOf(TextFieldValue()) }
    var selectedMentions by remember(state.selectedThreadId) { mutableStateOf(emptyList<ComposerMention>()) }
    var attachments by remember(state.selectedThreadId) { mutableStateOf(emptyList<ComposerImageAttachment>()) }
    var attachmentError by remember(state.selectedThreadId) { mutableStateOf<String?>(null) }
    var mentionKindFilter by remember(state.selectedThreadId) { mutableStateOf<ComposerMentionKind?>(null) }
    var modelSettingsMenu by remember(state.selectedThreadId) { mutableStateOf(false) }
    var modelSettingsPage by remember(state.selectedThreadId) { mutableStateOf(ModelSettingsPage.ROOT) }
    var addMenuOpen by remember(state.selectedThreadId) { mutableStateOf(false) }
    var showRemotePathPicker by remember(state.selectedThreadId) { mutableStateOf(false) }
    var goalModeActive by remember(state.selectedThreadId) { mutableStateOf(false) }
    var policyMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val composerScope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        composerScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { readComposerImageAttachment(context, uri) } }
            result.onSuccess { attachment ->
                attachments = (attachments + attachment).takeLast(MAX_COMPOSER_IMAGES)
                attachmentError = null
            }.onFailure { error ->
                attachmentError = error.message ?: "Unable to attach image"
            }
        }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val composerMenuProperties = remember { PopupProperties(focusable = false) }
    val selectedModel = state.models.firstOrNull { it.id == state.selectedModel }
    val selectedServiceTier = selectedModel?.serviceTiers?.firstOrNull { it.id == state.selectedServiceTier }
    val supportsImages = selectedModel?.inputModalities?.any { it.equals("image", true) } == true
    val selectedThread = state.threads.firstOrNull { it.id == state.selectedThreadId }
    val cwd = selectedThread?.cwd?.takeIf(String::isNotBlank) ?: state.selectedProjectPath.orEmpty()
    val addMenuSkills = state.skills.filter { it.enabled && (it.cwds.isEmpty() || cwd in it.cwds) }
    val addMenuPlugins = state.plugins.filter { it.enabled }
    val planModeAvailable = state.collaborationModes.any { it.mode == "plan" }
    val trigger = findComposerTrigger(text.text, text.selection.start)
    val inlineGoalObjective = extractGoalSlashObjective(text.text)?.takeIf(String::isNotBlank)
    val allSlashCommands = (
        desktopSlashCommands(hasThread = state.selectedThreadId != null) +
            selectedModel?.serviceTiers.orEmpty().map { tier ->
                SlashCommand(
                    name = tier.id.lowercase(),
                    description = tier.description.ifBlank { "Use ${tier.name} service tier" },
                    icon = Icons.Outlined.SmartToy,
                    action = SlashCommandAction.SERVICE_TIER,
                    value = tier.id,
                )
            }
        ).distinctBy { it.name }
    val slashCommands = allSlashCommands.filter { command ->
        trigger?.kind == ComposerTriggerKind.SLASH_COMMAND &&
            (trigger.query.isBlank() || command.name.contains(trigger.query, ignoreCase = true) ||
                command.description.contains(trigger.query, ignoreCase = true))
    }
    val mentionOptions = if (trigger?.kind == ComposerTriggerKind.MENTION) {
        val query = trigger.query.trim()
        buildList {
            if (mentionKindFilter != ComposerMentionKind.PLUGIN) {
                state.skills.asSequence()
                    .filter { it.enabled && (it.cwds.isEmpty() || cwd in it.cwds) }
                    .filter {
                        query.isEmpty() || it.name.contains(query, true) ||
                            it.displayName.contains(query, true) || it.description.contains(query, true)
                    }
                    .forEach { skill ->
                        add(
                            ComposerMentionOption(
                                kind = ComposerMentionKind.SKILL,
                                title = skill.displayName,
                                name = skill.name,
                                description = skill.description,
                                path = skill.path,
                                token = skill.composerToken(),
                            ),
                        )
                    }
            }
            if (mentionKindFilter != ComposerMentionKind.SKILL) {
                state.plugins.asSequence()
                    .filter { it.enabled }
                    .filter {
                        query.isEmpty() || it.name.contains(query, true) ||
                            it.displayName.contains(query, true) || it.description.contains(query, true)
                    }
                    .forEach { plugin ->
                        add(
                            ComposerMentionOption(
                                kind = ComposerMentionKind.PLUGIN,
                                title = plugin.displayName,
                                name = plugin.displayName,
                                description = plugin.description.ifBlank { plugin.marketplace },
                                path = plugin.mentionPath,
                                token = plugin.composerToken(),
                            ),
                        )
                    }
            }
        }
    } else {
        emptyList()
    }

    fun replaceTrigger(replacement: String) {
        val activeTrigger = trigger ?: return
        val update = replaceComposerTrigger(text.text, activeTrigger, replacement)
        text = TextFieldValue(update.text, TextRange(update.cursor))
    }

    fun insertAtCursor(value: String) {
        val start = minOf(text.selection.start, text.selection.end).coerceIn(0, text.text.length)
        val end = maxOf(text.selection.start, text.selection.end).coerceIn(start, text.text.length)
        val updated = text.text.replaceRange(start, end, value)
        text = TextFieldValue(updated, TextRange(start + value.length))
    }

    fun insertMention(option: ComposerMentionOption) {
        insertAtCursor("${option.token} ")
        selectedMentions = (selectedMentions + ComposerMention(
            kind = option.kind,
            name = option.name,
            path = option.path,
            token = option.token,
        )).distinctBy { "${it.kind}:${it.path}" }
    }

    fun activateGoalMode() {
        goalModeActive = true
        if (state.selectedCollaborationMode == "plan") onSetCollaborationMode("default")
    }

    fun executeSlashCommand(command: SlashCommand) {
        when (command.action) {
            SlashCommandAction.MODEL -> {
                replaceTrigger("")
                modelSettingsPage = ModelSettingsPage.MODEL
                modelSettingsMenu = true
            }
            SlashCommandAction.REASONING -> {
                replaceTrigger("")
                modelSettingsPage = ModelSettingsPage.REASONING
                modelSettingsMenu = true
            }
            SlashCommandAction.SERVICE_TIER -> {
                replaceTrigger("")
                onSetServiceTier(command.value.takeUnless { it == state.selectedServiceTier })
            }
            SlashCommandAction.PLAN_MODE -> {
                replaceTrigger("")
                goalModeActive = false
                onSetCollaborationMode("plan")
            }
            SlashCommandAction.PERMISSIONS -> {
                replaceTrigger("")
                policyMenu = true
            }
            SlashCommandAction.SKILLS -> {
                replaceTrigger("\$")
                mentionKindFilter = ComposerMentionKind.SKILL
            }
            SlashCommandAction.PLUGINS -> {
                replaceTrigger("\$")
                mentionKindFilter = ComposerMentionKind.PLUGIN
            }
            SlashCommandAction.RENAME -> {
                replaceTrigger("")
                onRenameCurrentThread()
            }
            SlashCommandAction.ARCHIVE -> {
                replaceTrigger("")
                onArchiveCurrentThread()
            }
            SlashCommandAction.COMPACT -> {
                replaceTrigger("")
                onCompactThread()
            }
            SlashCommandAction.FORK -> {
                replaceTrigger("")
                onForkThread()
            }
            SlashCommandAction.REVIEW -> {
                replaceTrigger("")
                onOpenReview()
            }
            SlashCommandAction.INIT -> {
                replaceTrigger("")
                onRunInit()
            }
            SlashCommandAction.MCP -> {
                replaceTrigger("")
                onOpenMcpStatus()
            }
            SlashCommandAction.FEEDBACK -> {
                replaceTrigger("")
                onOpenFeedback()
            }
            SlashCommandAction.GOAL -> {
                replaceTrigger("")
                activateGoalMode()
            }
            SlashCommandAction.NEW_TASK -> {
                replaceTrigger("")
                onNewThread()
            }
            SlashCommandAction.STATUS -> {
                replaceTrigger("")
                onShowStatus()
            }
        }
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (trigger != null) {
                ComposerSuggestionPopup(
                    triggerKind = trigger.kind,
                    slashCommands = slashCommands,
                    mentionOptions = mentionOptions,
                    loading = state.isComposerCatalogLoading,
                    error = state.composerCatalogError,
                    mentionKindFilter = mentionKindFilter,
                    onSlashCommand = ::executeSlashCommand,
                    onMention = { option ->
                        replaceTrigger("${option.token} ")
                        selectedMentions = (selectedMentions + ComposerMention(
                            kind = option.kind,
                            name = option.name,
                            path = option.path,
                            token = option.token,
                        )).distinctBy { "${it.kind}:${it.path}" }
                        mentionKindFilter = null
                    },
                )
            }
            state.threadGoal?.let { goal ->
                GoalBar(
                    goal = goal,
                    loading = state.isGoalLoading,
                    onEdit = onEditGoal,
                    onSetStatus = onSetGoalStatus,
                    onClear = onClearGoal,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 860.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    if (attachments.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                .padding(bottom = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            attachments.forEach { attachment ->
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Row(
                                        modifier = Modifier.height(30.dp).padding(start = 8.dp, end = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            attachment.displayName,
                                            modifier = Modifier.widthIn(max = 140.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        IconButton(
                                            onClick = { attachments = attachments.filterNot { it.id == attachment.id } },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Remove image", modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    attachmentError?.let { error ->
                        Text(
                            error,
                            modifier = Modifier.padding(bottom = 5.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { updated ->
                            val previousTrigger = findComposerTrigger(text.text, text.selection.start)
                            val nextTrigger = findComposerTrigger(updated.text, updated.selection.start)
                            text = updated
                            selectedMentions = selectedMentions.filter { updated.text.contains(it.token) }
                            if (nextTrigger?.kind != ComposerTriggerKind.MENTION ||
                                previousTrigger?.kind != ComposerTriggerKind.MENTION
                            ) {
                                mentionKindFilter = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("composer-input"),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        minLines = 1,
                        maxLines = 7,
                        enabled = true,
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxWidth()) {
                                if (text.text.isEmpty()) {
                                    Text(
                                        if (goalModeActive) {
                                            "描述你的目标，最好包含可衡量的结果"
                                        } else {
                                            "Ask Codex"
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().height(34.dp).padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ComposerAddMenu(
                                modifier = Modifier.testTag("composer-add"),
                                expanded = addMenuOpen,
                                properties = composerMenuProperties,
                                skills = addMenuSkills.map { skill ->
                                    ComposerMentionOption(
                                        kind = ComposerMentionKind.SKILL,
                                        title = skill.displayName,
                                        name = skill.name,
                                        description = skill.description,
                                        path = skill.path,
                                        token = skill.composerToken(),
                                    )
                                },
                                plugins = addMenuPlugins.map { plugin ->
                                    ComposerMentionOption(
                                        kind = ComposerMentionKind.PLUGIN,
                                        title = plugin.displayName,
                                        name = plugin.displayName,
                                        description = plugin.description.ifBlank { plugin.marketplace },
                                        path = plugin.mentionPath,
                                        token = plugin.composerToken(),
                                    )
                                },
                                canBrowseRemoteFiles = cwd.isNotBlank(),
                                canAttachImage = supportsImages && attachments.size < MAX_COMPOSER_IMAGES,
                                planModeAvailable = planModeAvailable,
                                onOpen = { addMenuOpen = true },
                                onDismiss = { addMenuOpen = false },
                                onBrowseRemoteFiles = {
                                    addMenuOpen = false
                                    showRemotePathPicker = true
                                    onLoadRemoteDirectory(cwd)
                                },
                                onAttachImage = {
                                    addMenuOpen = false
                                    imagePicker.launch("image/*")
                                },
                                onOpenGoal = {
                                    addMenuOpen = false
                                    activateGoalMode()
                                },
                                onPlanMode = {
                                    addMenuOpen = false
                                    goalModeActive = false
                                    onSetCollaborationMode("plan")
                                },
                                onMention = { option ->
                                    addMenuOpen = false
                                    insertMention(option)
                                },
                            )
                            Box {
                                CompactComposerMenuButton(
                                    modifier = Modifier.testTag("composer-permissions"),
                                    label = permissionModeLabel(state),
                                    maxLabelWidth = 88.dp,
                                    leadingIcon = Icons.Outlined.Tune,
                                    showChevron = false,
                                    onClick = { policyMenu = true },
                                )
                                PermissionDropdown(
                                    state = state,
                                    expanded = policyMenu,
                                    properties = composerMenuProperties,
                                    onDismiss = { policyMenu = false },
                                    onSetPermissionMode = {
                                        onSetPermissionMode(it)
                                        policyMenu = false
                                    },
                                    onSetPermissionProfile = {
                                        onSetPermissionProfile(it)
                                        policyMenu = false
                                    },
                                )
                            }
                            if (goalModeActive) {
                                Spacer(Modifier.width(5.dp))
                                Box(
                                    Modifier.width(1.dp).height(18.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                                Spacer(Modifier.width(3.dp))
                                GoalModeIndicator(
                                    onClear = { goalModeActive = false },
                                    modifier = Modifier.testTag("composer-goal-marker"),
                                )
                            }
                        }
                        if (isKeyboardVisible) {
                            IconButton(
                                onClick = { keyboardController?.hide() },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.KeyboardHide,
                                    contentDescription = "Hide keyboard",
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        if (state.isTurnRunning) {
                            IconButton(
                                onClick = onStop,
                                enabled = state.activeTurnId != null,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(Icons.Outlined.Stop, contentDescription = "停止")
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        ContextUsageRing(
                            usage = state.threadTokenUsage,
                            onClick = onShowStatus,
                            modifier = Modifier.testTag("composer-context"),
                        )
                        Spacer(Modifier.width(2.dp))
                        Box {
                            CompactComposerMenuButton(
                                modifier = Modifier.testTag("composer-model"),
                                label = modelSettingsSummary(
                                    modelId = selectedModel?.id,
                                    effort = state.selectedReasoningEffort,
                                    serviceTier = selectedServiceTier?.name,
                                ),
                                maxLabelWidth = 144.dp,
                                enabled = state.models.isNotEmpty(),
                                onClick = {
                                    modelSettingsPage = ModelSettingsPage.ROOT
                                    modelSettingsMenu = true
                                },
                            )
                            ModelSettingsDropdown(
                                state = state,
                                expanded = modelSettingsMenu,
                                page = modelSettingsPage,
                                properties = composerMenuProperties,
                                onDismiss = {
                                    modelSettingsMenu = false
                                    modelSettingsPage = ModelSettingsPage.ROOT
                                },
                                onPageChange = { modelSettingsPage = it },
                                onSetModel = onSetModel,
                                onSetReasoningEffort = onSetReasoningEffort,
                                onSetServiceTier = onSetServiceTier,
                            )
                        }
                        Spacer(Modifier.width(3.dp))
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            IconButton(
                                onClick = {
                                    if (text.text.isNotBlank() || attachments.isNotEmpty()) {
                                        val exactCommand = text.text.trim().takeIf { attachments.isEmpty() }
                                            ?.takeIf { it.startsWith('/') && it.drop(1).none(Char::isWhitespace) }
                                            ?.drop(1)
                                            ?.let { name -> allSlashCommands.firstOrNull { it.name.equals(name, true) } }
                                        if (exactCommand != null) {
                                            executeSlashCommand(exactCommand)
                                            text = TextFieldValue()
                                        } else {
                                            val submittedText = inlineGoalObjective ?: text.text
                                            val submittedMentions = selectedMentions
                                            val submittedAttachments = attachments
                                            val submitAsGoal = goalModeActive || inlineGoalObjective != null
                                            text = TextFieldValue()
                                            selectedMentions = emptyList()
                                            attachments = emptyList()
                                            attachmentError = null
                                            mentionKindFilter = null
                                            goalModeActive = false
                                            onSend(
                                                submittedText,
                                                submittedMentions,
                                                submittedAttachments,
                                                submitAsGoal,
                                            )
                                        }
                                    }
                                },
                                enabled = if (goalModeActive || inlineGoalObjective != null) {
                                    text.text.isNotBlank() && !state.isTurnRunning && !state.isGoalLoading
                                } else {
                                    (text.text.isNotBlank() || attachments.isNotEmpty()) &&
                                        (!state.isTurnRunning || state.activeTurnId != null)
                                },
                                modifier = Modifier.testTag("composer-send"),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Send,
                                    contentDescription = if (state.isTurnRunning) "追加到当前任务" else "发送",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showRemotePathPicker) {
        RemotePathPickerDialog(
            state = state,
            projectRoot = cwd,
            onNavigate = onLoadRemoteDirectory,
            onSelect = { path ->
                insertAtCursor("${remotePathComposerToken(cwd, path)} ")
                showRemotePathPicker = false
                onClearRemoteDirectory()
            },
            onDismiss = {
                showRemotePathPicker = false
                onClearRemoteDirectory()
            },
        )
    }
}

@Composable
private fun ComposerAddMenu(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    properties: PopupProperties,
    skills: List<ComposerMentionOption>,
    plugins: List<ComposerMentionOption>,
    canBrowseRemoteFiles: Boolean,
    canAttachImage: Boolean,
    planModeAvailable: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onBrowseRemoteFiles: () -> Unit,
    onAttachImage: () -> Unit,
    onOpenGoal: () -> Unit,
    onPlanMode: () -> Unit,
    onMention: (ComposerMentionOption) -> Unit,
) {
    Box {
        IconButton(onClick = onOpen, modifier = modifier.size(32.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = "添加", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.width(330.dp).heightIn(max = 440.dp),
            properties = properties,
        ) {
            Text(
                "添加",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AddMenuItem(
                icon = Icons.Outlined.FolderOpen,
                title = "文件和文件夹",
                description = "选择远端项目中的路径",
                enabled = canBrowseRemoteFiles,
                onClick = onBrowseRemoteFiles,
            )
            AddMenuItem(
                icon = Icons.Outlined.Image,
                title = "图片",
                description = "从 Android 设备添加图片",
                enabled = canAttachImage,
                onClick = onAttachImage,
            )
            AddMenuItem(
                icon = Icons.Outlined.Flag,
                title = "目标",
                description = "设置要持续追求的目标",
                onClick = onOpenGoal,
            )
            AddMenuItem(
                icon = Icons.AutoMirrored.Outlined.List,
                title = "计划模式",
                description = "开启远端计划模式",
                enabled = planModeAvailable,
                onClick = onPlanMode,
            )
            if (skills.isNotEmpty() || plugins.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    "技能和插件",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                skills.forEach { option ->
                    AddMenuItem(
                        icon = Icons.Outlined.Psychology,
                        title = option.title,
                        description = option.description,
                        onClick = { onMention(option) },
                    )
                }
                plugins.forEach { option ->
                    AddMenuItem(
                        icon = Icons.Outlined.Extension,
                        title = option.title,
                        description = option.description,
                        onClick = { onMention(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddMenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    if (description.isNotBlank()) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun RemotePathPickerDialog(
    state: AppUiState,
    projectRoot: String,
    onNavigate: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentPath = state.remoteDirectoryPath ?: projectRoot
    val parentPath = remoteParentPath(currentPath)?.takeIf { parent ->
        remotePathIsWithinRoot(projectRoot, parent)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件和文件夹") },
        text = {
            Column {
                Text(
                    currentPath,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                when {
                    state.isRemoteDirectoryLoading -> Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    state.remoteDirectoryError != null -> Column(
                        Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.remoteDirectoryError, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { onNavigate(currentPath) }) { Text("重试") }
                    }
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        if (parentPath != null) {
                            item(key = "parent") {
                                RemotePathRow("..", true) { onNavigate(parentPath) }
                            }
                        }
                        items(state.remoteDirectoryEntries, key = { "${it.name}:${it.isDirectory}" }) { entry ->
                            val childPath = appendRemotePath(currentPath, entry.name)
                            RemotePathRow(entry.name, entry.isDirectory) {
                                if (entry.isDirectory) onNavigate(childPath) else onSelect(childPath)
                            }
                        }
                        if (state.remoteDirectoryEntries.isEmpty() && parentPath == null) {
                            item { Text("该文件夹为空", Modifier.padding(vertical = 24.dp)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(currentPath) }, enabled = !state.isRemoteDirectoryLoading) {
                Text("选择此文件夹")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RemotePathRow(name: String, directory: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (directory) Icons.Outlined.FolderOpen else Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (directory) Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

internal fun appendRemotePath(parent: String, child: String): String {
    val separator = if ('\\' in parent && '/' !in parent) '\\' else '/'
    return parent.trimEnd('/', '\\') + separator + child.trimStart('/', '\\')
}

internal fun remoteParentPath(path: String): String? {
    val trimmed = path.trim().trimEnd('/', '\\')
    if (trimmed.isEmpty() || trimmed == "/" || (trimmed.length == 2 && trimmed[1] == ':')) return null
    val separatorIndex = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
    if (separatorIndex < 0) return null
    if (separatorIndex == 0) return "/"
    if (separatorIndex == 2 && trimmed.getOrNull(1) == ':') return trimmed.substring(0, 3)
    return trimmed.substring(0, separatorIndex)
}

private fun remotePathIsWithinRoot(root: String, path: String): Boolean {
    val windows = '\\' in root || root.getOrNull(1) == ':'
    val normalizedRoot = root.trimEnd('/', '\\')
    val normalizedPath = path.trimEnd('/', '\\')
    return if (windows) normalizedPath.startsWith(normalizedRoot, ignoreCase = true) else normalizedPath.startsWith(normalizedRoot)
}

internal fun remotePathComposerToken(root: String, path: String): String {
    val windows = '\\' in root || root.getOrNull(1) == ':'
    val separator = if (windows) '\\' else '/'
    val rootPrefix = root.trimEnd('/', '\\') + separator
    val relative = if (path.startsWith(rootPrefix, ignoreCase = windows)) path.substring(rootPrefix.length) else path
    val normalized = relative.replace('\\', '/')
    return if (' ' in normalized) "@\"$normalized\"" else "@$normalized"
}

@Composable
private fun GoalBar(
    goal: ThreadGoal,
    loading: Boolean,
    onEdit: () -> Unit,
    onSetStatus: (ThreadGoalStatus) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 860.dp)
            .animateContentSize()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Flag,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                goal.objective,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Goal · ${goal.status.displayGoalStatus()}",
                style = MaterialTheme.typography.labelSmall,
                color = goal.status.goalStatusColor(),
            )
        }
        if (loading) {
            CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(7.dp))
        }
        IconButton(onClick = onEdit, enabled = !loading, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit goal", modifier = Modifier.size(18.dp))
        }
        when (goal.status) {
            ThreadGoalStatus.ACTIVE -> IconButton(
                onClick = { onSetStatus(ThreadGoalStatus.PAUSED) },
                enabled = !loading,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Outlined.Pause, contentDescription = "Pause goal", modifier = Modifier.size(18.dp))
            }
            ThreadGoalStatus.PAUSED,
            ThreadGoalStatus.BLOCKED,
            ThreadGoalStatus.USAGE_LIMITED,
            -> IconButton(
                onClick = { onSetStatus(ThreadGoalStatus.ACTIVE) },
                enabled = !loading,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "Resume goal", modifier = Modifier.size(19.dp))
            }
            ThreadGoalStatus.BUDGET_LIMITED,
            ThreadGoalStatus.COMPLETE,
            -> Unit
        }
        IconButton(onClick = onClear, enabled = !loading, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Clear goal", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun GoalEditorDialog(
    initialObjective: String,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var objective by remember(initialObjective) { mutableStateOf(initialObjective) }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (initialObjective.isBlank()) "Add goal" else "Edit goal") },
        text = {
            Column {
                OutlinedTextField(
                    value = objective,
                    onValueChange = { objective = it },
                    modifier = Modifier.fillMaxWidth().testTag("goal-editor-input"),
                    label = { Text("Goal") },
                    minLines = 2,
                    maxLines = 5,
                    enabled = !isSaving,
                )
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(objective) },
                enabled = objective.isNotBlank() && !isSaving,
            ) {
                Text(if (isSaving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        },
    )
}

@Composable
private fun ReviewDialog(
    onDismiss: () -> Unit,
    onStart: (ReviewTargetKind, String) -> Unit,
) {
    var target by remember { mutableStateOf(ReviewTargetKind.UNCOMMITTED_CHANGES) }
    var targetValue by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Code review") },
        text = {
            Column {
                ReviewTargetOption(
                    label = "Uncommitted changes",
                    selected = target == ReviewTargetKind.UNCOMMITTED_CHANGES,
                    onSelect = { target = ReviewTargetKind.UNCOMMITTED_CHANGES },
                )
                ReviewTargetOption(
                    label = "Compare with base branch",
                    selected = target == ReviewTargetKind.BASE_BRANCH,
                    onSelect = { target = ReviewTargetKind.BASE_BRANCH },
                )
                ReviewTargetOption(
                    label = "Custom review",
                    selected = target == ReviewTargetKind.CUSTOM,
                    onSelect = { target = ReviewTargetKind.CUSTOM },
                )
                if (target != ReviewTargetKind.UNCOMMITTED_CHANGES) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetValue,
                        onValueChange = { targetValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(if (target == ReviewTargetKind.BASE_BRANCH) "Base branch" else "Instructions")
                        },
                        minLines = if (target == ReviewTargetKind.CUSTOM) 2 else 1,
                        maxLines = 4,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onStart(target, targetValue) },
                enabled = target == ReviewTargetKind.UNCOMMITTED_CHANGES || targetValue.isNotBlank(),
            ) { Text("Start review") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReviewTargetOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FeedbackDialog(
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    val classifications = listOf(
        "bug" to "Bug",
        "bad" to "Result was wrong",
        "good" to "Result was helpful",
        "other" to "Other",
    )
    var classification by remember { mutableStateOf("bug") }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null) },
        title = { Text("Send feedback") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                classifications.forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = classification == value,
                            onClick = { classification = value },
                        ).padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = classification == value,
                            onClick = { classification = value },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Details") },
                    minLines = 3,
                    maxLines = 6,
                )
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(classification, reason) }, enabled = !isSubmitting) {
                Text("Send")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun McpStatusDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onLogin: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MCP servers") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                when {
                    state.isMcpStatusLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Loading remote MCP status…")
                    }
                    !state.mcpStatusError.isNullOrBlank() -> Text(
                        state.mcpStatusError,
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.mcpServers.isEmpty() -> Text("No MCP servers configured")
                    else -> state.mcpServers.forEachIndexed { index, server ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Hub, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(server.name, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "${server.toolCount} tools · ${server.resourceCount} resources",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (server.authStatus == "notLoggedIn") {
                                TextButton(
                                    onClick = { onLogin(server.name) },
                                    enabled = !state.isMcpLoginStarting,
                                ) {
                                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Sign in")
                                }
                            } else {
                                Text(
                                    server.authStatus.displayMcpAuthStatus(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = onRefresh, enabled = !state.isMcpStatusLoading && !state.isMcpLoginStarting) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Reload")
            }
        },
    )
}

@Composable
private fun ArchivedTasksDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onUnarchive: (RemoteThread) -> Unit,
    onDelete: (RemoteThread) -> Unit,
) {
    var searchQuery by remember(state.activeConnection?.id) { mutableStateOf("") }
    val filteredThreads = remember(state.archivedThreads, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) state.archivedThreads else state.archivedThreads.filter { thread ->
            thread.title.contains(query, true) || thread.cwd.contains(query, true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archived tasks") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search archived tasks") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                when {
                    state.isArchivedThreadsLoading -> Row(
                        modifier = Modifier.padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Loading archived tasks…")
                    }
                    !state.archivedThreadsError.isNullOrBlank() -> Text(
                        state.archivedThreadsError,
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                    filteredThreads.isEmpty() -> Text(
                        if (searchQuery.isBlank()) "No archived tasks" else "No matching archived tasks",
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    ) {
                        items(filteredThreads, key = { "archived-${it.id}" }) { thread ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        thread.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${thread.cwd} · ${formatThreadTime(thread.updatedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { onUnarchive(thread) }) {
                                    Icon(Icons.Outlined.Unarchive, contentDescription = "Restore task")
                                }
                                IconButton(onClick = { onDelete(thread) }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete task permanently",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = onRefresh, enabled = !state.isArchivedThreadsLoading) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Refresh")
            }
        },
    )
}

@Composable
private fun DeleteArchivedThreadDialog(
    thread: RemoteThread,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
        title = { Text("Delete task permanently?") },
        text = { Text("${thread.title}\n\nThis removes the remote Codex history and cannot be undone.") },
        confirmButton = {
            Button(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RemoteStatusDialog(state: AppUiState, onDismiss: () -> Unit) {
    val usage = state.threadTokenUsage
    val contextPercent = if (usage?.modelContextWindow != null && usage.modelContextWindow > 0) {
        usage.totalTokens * 100.0 / usage.modelContextWindow
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remote status") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                StatusLine("Task ID", state.selectedThreadId ?: "New task", monospace = true)
                StatusLine("Codex", state.remoteServer?.codexVersion?.ifBlank { "Unknown" } ?: "Unknown")
                StatusLine("Model", state.selectedModel ?: "Unknown")
                StatusLine(
                    "Context",
                    contextPercent?.let { "${it.toInt().coerceIn(0, 100)}% · ${usage?.totalTokens ?: 0} tokens" }
                        ?: "Waiting for usage data",
                )
                if (state.isStatusLoading) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    state.rateLimits?.let { limits ->
                        limits.planType?.let { StatusLine("Plan", it) }
                        limits.primary?.let { StatusLine("Primary limit", it.displayRateLimit()) }
                        limits.secondary?.let { StatusLine("Secondary limit", it.displayRateLimit()) }
                        when {
                            limits.creditsUnlimited -> StatusLine("Credits", "Unlimited")
                            !limits.creditsBalance.isNullOrBlank() -> StatusLine("Credits", limits.creditsBalance)
                        }
                    }
                    state.statusError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun StatusLine(label: String, value: String, monospace: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            modifier = Modifier.width(112.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            ),
        )
    }
}

private fun String.displayMcpAuthStatus(): String = when (this) {
    "notLoggedIn" -> "Not authenticated"
    "bearerToken" -> "API key"
    "oAuth" -> "OAuth"
    "unsupported" -> "No auth"
    else -> ifBlank { "Unknown" }
}

private fun com.codex.remote.domain.RateLimitWindowSnapshot.displayRateLimit(): String {
    val reset = resetsAt?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it * 1000)) }
    return buildString {
        append("${usedPercent.toInt().coerceIn(0, 100)}% used")
        if (windowDurationMinutes != null) append(" · ${windowDurationMinutes} min")
        if (reset != null) append(" · resets $reset")
    }
}

@Composable
private fun ThreadGoalStatus.goalStatusColor(): Color = when (this) {
    ThreadGoalStatus.ACTIVE, ThreadGoalStatus.COMPLETE -> MaterialTheme.colorScheme.secondary
    ThreadGoalStatus.BLOCKED, ThreadGoalStatus.USAGE_LIMITED, ThreadGoalStatus.BUDGET_LIMITED ->
        MaterialTheme.colorScheme.error
    ThreadGoalStatus.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun ThreadGoalStatus.displayGoalStatus(): String = when (this) {
    ThreadGoalStatus.ACTIVE -> "Active"
    ThreadGoalStatus.PAUSED -> "Paused"
    ThreadGoalStatus.BLOCKED -> "Blocked"
    ThreadGoalStatus.USAGE_LIMITED -> "Usage limited"
    ThreadGoalStatus.BUDGET_LIMITED -> "Budget limited"
    ThreadGoalStatus.COMPLETE -> "Complete"
}

private val builtInPermissionProfiles = setOf(":workspace", ":danger-full-access", ":read-only")

internal fun permissionModeFor(
    permissionProfile: String?,
    approvalPolicy: String,
    approvalsReviewer: String,
): PermissionMode? {
    if (permissionProfile != null && permissionProfile !in builtInPermissionProfiles) return null
    return when {
        approvalsReviewer == "auto_review" -> PermissionMode.AUTO_REVIEW
        permissionProfile == ":read-only" -> PermissionMode.READ_ONLY
        permissionProfile == ":danger-full-access" || approvalPolicy == "never" -> PermissionMode.FULL_ACCESS
        else -> PermissionMode.ASK
    }
}

private fun permissionModeLabel(state: AppUiState): String = when (
    permissionModeFor(state.selectedPermissionProfile, state.approvalPolicy, state.approvalsReviewer)
) {
    PermissionMode.ASK -> "询问"
    PermissionMode.AUTO_REVIEW -> "替我审批"
    PermissionMode.FULL_ACCESS -> "完全访问"
    PermissionMode.READ_ONLY -> "只读"
    null -> state.selectedPermissionProfile?.removePrefix(":") ?: "询问"
}

@Composable
private fun PermissionDropdown(
    state: AppUiState,
    expanded: Boolean,
    properties: PopupProperties,
    onDismiss: () -> Unit,
    onSetPermissionMode: (PermissionMode) -> Unit,
    onSetPermissionProfile: (String) -> Unit,
) {
    val selectedMode = permissionModeFor(
        state.selectedPermissionProfile,
        state.approvalPolicy,
        state.approvalsReviewer,
    )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(310.dp),
        properties = properties,
    ) {
        PermissionDropdownItem(
            title = "询问",
            description = "需要执行命令或修改文件时询问",
            selected = selectedMode == PermissionMode.ASK,
            onClick = { onSetPermissionMode(PermissionMode.ASK) },
        )
        PermissionDropdownItem(
            title = "替我审批",
            description = "由 Codex 自动审查需要批准的操作",
            selected = selectedMode == PermissionMode.AUTO_REVIEW,
            onClick = { onSetPermissionMode(PermissionMode.AUTO_REVIEW) },
        )
        PermissionDropdownItem(
            title = "完全访问",
            description = "无需询问即可访问远端工作区和网络",
            selected = selectedMode == PermissionMode.FULL_ACCESS,
            onClick = { onSetPermissionMode(PermissionMode.FULL_ACCESS) },
        )
        PermissionDropdownItem(
            title = "只读",
            description = "允许读取，但不允许修改远端文件",
            selected = selectedMode == PermissionMode.READ_ONLY,
            onClick = { onSetPermissionMode(PermissionMode.READ_ONLY) },
        )
        val customProfiles = state.permissionProfiles.filter { it.id !in builtInPermissionProfiles }
        if (customProfiles.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            customProfiles.forEach { profile ->
                PermissionDropdownItem(
                    title = profile.id,
                    description = profile.description,
                    selected = selectedMode == null && state.selectedPermissionProfile == profile.id,
                    enabled = profile.allowed,
                    onClick = { onSetPermissionProfile(profile.id) },
                )
            }
        }
    }
}

@Composable
private fun PermissionDropdownItem(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        enabled = enabled,
        onClick = onClick,
        trailingIcon = {
            if (selected) Icon(Icons.Outlined.Check, contentDescription = null)
        },
    )
}

@Composable
private fun ContextUsageRing(
    usage: RemoteThreadTokenUsage?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (usage?.modelContextWindow != null && usage.modelContextWindow > 0) {
        (usage.totalTokens.toFloat() / usage.modelContextWindow.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val percent = (fraction * 100).toInt()
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val progress = when {
        fraction >= 0.9f -> MaterialTheme.colorScheme.error
        fraction >= 0.7f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp).semantics {
            contentDescription = if (usage == null) "上下文用量尚不可用" else "上下文已使用 $percent%"
        },
    ) {
        Canvas(Modifier.size(21.dp)) {
            val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(track, style = stroke)
            if (fraction > 0f) {
                drawArc(
                    color = progress,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    style = stroke,
                )
            }
        }
    }
}

private enum class ModelSettingsPage { ROOT, MODEL, REASONING, SERVICE_TIER }

@Composable
private fun ModelSettingsDropdown(
    state: AppUiState,
    expanded: Boolean,
    page: ModelSettingsPage,
    properties: PopupProperties,
    onDismiss: () -> Unit,
    onPageChange: (ModelSettingsPage) -> Unit,
    onSetModel: (String) -> Unit,
    onSetReasoningEffort: (String) -> Unit,
    onSetServiceTier: (String?) -> Unit,
) {
    val selectedModel = state.models.firstOrNull { it.id == state.selectedModel }
    val selectedTier = selectedModel?.serviceTiers?.firstOrNull { it.id == state.selectedServiceTier }
    val fastTier = selectedModel?.serviceTiers?.firstOrNull { tier ->
        tier.id.equals("fast", ignoreCase = true) || tier.name.equals("fast", ignoreCase = true)
    }
    val showFastToggle = fastTier != null && selectedModel.serviceTiers.size == 1

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(300.dp),
        properties = properties,
    ) {
        when (page) {
            ModelSettingsPage.ROOT -> {
                ModelSettingsNavigationRow(
                    label = "模型",
                    value = selectedModel?.displayName ?: "未选择",
                    enabled = state.models.isNotEmpty(),
                    onClick = { onPageChange(ModelSettingsPage.MODEL) },
                )
                ModelSettingsNavigationRow(
                    label = "推理强度",
                    value = state.selectedReasoningEffort?.displayEffort() ?: "默认",
                    enabled = selectedModel?.supportedReasoningEfforts?.isNotEmpty() == true,
                    onClick = { onPageChange(ModelSettingsPage.REASONING) },
                )
                if (selectedModel?.serviceTiers?.isNotEmpty() == true) {
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                    if (showFastToggle) {
                        DropdownMenuItem(
                            text = {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Fast", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        if (state.selectedServiceTier == fastTier.id) "开启" else "关闭",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingIcon = {
                                Switch(
                                    checked = state.selectedServiceTier == fastTier.id,
                                    onCheckedChange = null,
                                )
                            },
                            onClick = {
                                onSetServiceTier(fastTier.id.takeUnless { it == state.selectedServiceTier })
                            },
                        )
                    } else {
                        ModelSettingsNavigationRow(
                            label = "速度",
                            value = selectedTier?.name ?: "标准",
                            onClick = { onPageChange(ModelSettingsPage.SERVICE_TIER) },
                        )
                    }
                }
            }
            ModelSettingsPage.MODEL -> {
                ModelSettingsBackRow("模型") { onPageChange(ModelSettingsPage.ROOT) }
                state.models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(model.displayName, style = MaterialTheme.typography.labelLarge)
                                if (model.description.isNotBlank()) {
                                    Text(
                                        model.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSetModel(model.id)
                            onPageChange(ModelSettingsPage.ROOT)
                        },
                        trailingIcon = {
                            if (model.id == state.selectedModel) Icon(Icons.Outlined.Check, contentDescription = null)
                        },
                    )
                }
            }
            ModelSettingsPage.REASONING -> {
                ModelSettingsBackRow("推理强度") { onPageChange(ModelSettingsPage.ROOT) }
                selectedModel?.supportedReasoningEfforts.orEmpty().forEach { effort ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(effort.value.displayEffort(), style = MaterialTheme.typography.labelLarge)
                                if (effort.description.isNotBlank()) {
                                    Text(
                                        effort.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSetReasoningEffort(effort.value)
                            onPageChange(ModelSettingsPage.ROOT)
                        },
                        trailingIcon = {
                            if (effort.value == state.selectedReasoningEffort) {
                                Icon(Icons.Outlined.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }
            ModelSettingsPage.SERVICE_TIER -> {
                ModelSettingsBackRow("速度") { onPageChange(ModelSettingsPage.ROOT) }
                DropdownMenuItem(
                    text = { Text("标准") },
                    onClick = {
                        onSetServiceTier(null)
                        onPageChange(ModelSettingsPage.ROOT)
                    },
                    trailingIcon = {
                        if (state.selectedServiceTier == null) Icon(Icons.Outlined.Check, contentDescription = null)
                    },
                )
                selectedModel?.serviceTiers.orEmpty().forEach { tier ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(tier.name, style = MaterialTheme.typography.labelLarge)
                                if (tier.description.isNotBlank()) {
                                    Text(
                                        tier.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSetServiceTier(tier.id)
                            onPageChange(ModelSettingsPage.ROOT)
                        },
                        trailingIcon = {
                            if (tier.id == state.selectedServiceTier) Icon(Icons.Outlined.Check, contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelSettingsNavigationRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(
                    value,
                    modifier = Modifier.widthIn(max = 138.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingIcon = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun ModelSettingsBackRow(title: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") },
        onClick = onClick,
    )
    HorizontalDivider(Modifier.padding(horizontal = 12.dp))
}

internal fun modelSettingsSummary(
    modelId: String?,
    effort: String?,
    serviceTier: String?,
): String = listOfNotNull(
    modelId?.removePrefix("gpt-")?.removePrefix("GPT-") ?: "模型",
    effort?.displayEffort(),
    serviceTier?.takeUnless { it.equals("standard", ignoreCase = true) },
).joinToString(" · ")

@Composable
private fun CompactComposerMenuButton(
    label: String,
    maxLabelWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    showChevron: Boolean = true,
) {
    Row(
        modifier = modifier.height(32.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            modifier = Modifier.widthIn(max = maxLabelWidth),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showChevron) {
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun GoalModeIndicator(
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(32.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable(onClick = onClear)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Flag,
            contentDescription = "取消目标标记",
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            "目标",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

internal fun extractGoalSlashObjective(text: String): String? {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("/goal", ignoreCase = true)) return null
    val suffix = trimmed.drop(5)
    if (suffix.isNotEmpty() && !suffix.first().isWhitespace()) return null
    return suffix.trim()
}

internal enum class SlashCommandAction {
    MODEL, REASONING, SERVICE_TIER, PLAN_MODE, PERMISSIONS, SKILLS, PLUGINS, RENAME, ARCHIVE, COMPACT,
    FORK, REVIEW, INIT, MCP, FEEDBACK,
    GOAL, NEW_TASK, STATUS,
}

internal data class SlashCommand(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val action: SlashCommandAction,
    val value: String? = null,
)

private data class ComposerMentionOption(
    val kind: ComposerMentionKind,
    val title: String,
    val name: String,
    val description: String,
    val path: String,
    val token: String,
)

internal fun desktopSlashCommands(hasThread: Boolean): List<SlashCommand> = buildList {
    add(SlashCommand("model", "Choose the model for this task", Icons.Outlined.SmartToy, SlashCommandAction.MODEL))
    add(SlashCommand("reasoning", "Set reasoning effort", Icons.Outlined.Psychology, SlashCommandAction.REASONING))
    add(SlashCommand("plan", "Switch this task to Plan mode", Icons.Outlined.Flag, SlashCommandAction.PLAN_MODE))
    add(SlashCommand("permissions", "Change approval behavior", Icons.Outlined.Tune, SlashCommandAction.PERMISSIONS))
    add(SlashCommand("skills", "Mention a remote skill", Icons.Outlined.Psychology, SlashCommandAction.SKILLS))
    add(SlashCommand("plugins", "Mention an installed remote plugin", Icons.Outlined.Extension, SlashCommandAction.PLUGINS))
    add(SlashCommand("goal", "Set the next message as this task's goal", Icons.Outlined.Flag, SlashCommandAction.GOAL))
    add(SlashCommand("init", "Create AGENTS.md instructions for this project", Icons.Outlined.Description, SlashCommandAction.INIT))
    add(SlashCommand("mcp", "Show remote MCP server status", Icons.Outlined.Hub, SlashCommandAction.MCP))
    add(SlashCommand("feedback", "Send feedback about this task", Icons.Outlined.ErrorOutline, SlashCommandAction.FEEDBACK))
    add(SlashCommand("compact", "Compact this task's context", Icons.Outlined.Description, SlashCommandAction.COMPACT))
    if (hasThread) {
        add(SlashCommand("rename", "Rename the current task", Icons.Outlined.Edit, SlashCommandAction.RENAME))
        add(SlashCommand("archive", "Archive the current task", Icons.Outlined.Archive, SlashCommandAction.ARCHIVE))
        add(SlashCommand("fork", "Continue in a new task", Icons.AutoMirrored.Outlined.CallSplit, SlashCommandAction.FORK))
        add(SlashCommand("review-mode", "Review uncommitted changes or another target", Icons.Outlined.Code, SlashCommandAction.REVIEW))
    }
    add(SlashCommand("new", "Start a new task in this project", Icons.Outlined.Add, SlashCommandAction.NEW_TASK))
    add(SlashCommand("status", "Show remote Codex connection status", Icons.Outlined.Computer, SlashCommandAction.STATUS))
}

@Composable
private fun ComposerSuggestionPopup(
    triggerKind: ComposerTriggerKind,
    slashCommands: List<SlashCommand>,
    mentionOptions: List<ComposerMentionOption>,
    loading: Boolean,
    error: String?,
    mentionKindFilter: ComposerMentionKind?,
    onSlashCommand: (SlashCommand) -> Unit,
    onMention: (ComposerMentionOption) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 836.dp).padding(horizontal = 12.dp),
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 5.dp,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            if (triggerKind == ComposerTriggerKind.SLASH_COMMAND) {
                if (slashCommands.isEmpty()) item { ComposerPopupMessage("No commands found") }
                items(slashCommands, key = { "slash-${it.name}" }) { command ->
                    ComposerPopupRow(
                        icon = command.icon,
                        title = "/${command.name}",
                        description = command.description,
                        onClick = { onSlashCommand(command) },
                    )
                }
            } else {
                item {
                    Text(
                        when (mentionKindFilter) {
                            ComposerMentionKind.SKILL -> "SKILLS"
                            ComposerMentionKind.PLUGIN -> "PLUGINS"
                            null -> "SKILLS & PLUGINS"
                        },
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(mentionOptions, key = { "mention-${it.kind}-${it.path}" }) { option ->
                    val detail = option.description.takeIf(String::isNotBlank)
                        ?.let { "${option.token} · $it" } ?: option.token
                    ComposerPopupRow(
                        icon = if (option.kind == ComposerMentionKind.SKILL) Icons.Outlined.Psychology else Icons.Outlined.Extension,
                        title = option.title,
                        description = detail,
                        onClick = { onMention(option) },
                    )
                }
                if (mentionOptions.isEmpty()) {
                    item {
                        ComposerPopupMessage(
                            when {
                                loading -> "Loading from remote Codex…"
                                !error.isNullOrBlank() -> error
                                else -> "No matching remote skills or installed plugins"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerPopupRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ComposerPopupMessage(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 14.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RemoteAuthenticationState(
    loading: Boolean,
    onLogin: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 440.dp).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(16.dp))
            Text("Sign in to remote Codex", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "SSH 已连接，但远端 Codex 没有可用账号。登录会发生在远端主机，完成后会自动加载模型。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onLogin, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (loading) "Starting sign-in" else "Sign in with ChatGPT")
            }
        }
    }
}

@Composable
private fun ConnectionState(
    icon: ImageVector?,
    title: String,
    detail: String,
    loading: Boolean,
    modifier: Modifier,
    action: (() -> Unit)? = null,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 440.dp).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.5.dp)
            else icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                Spacer(Modifier.height(18.dp))
                OutlinedButton(onClick = action) { Text("Connections") }
            }
        }
    }
}

@Composable
private fun ApprovalDialog(
    approval: com.codex.remote.domain.ApprovalRequest,
    onDecision: (String, Map<String, List<String>>) -> Unit,
) {
    val answers = remember(approval.requestId) {
        mutableStateMapOf<String, String>().apply {
            approval.questions.forEach { question ->
                this[question.id] = question.options.firstOrNull().orEmpty()
            }
        }
    }
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                if (approval.kind == ApprovalKind.FILE_CHANGE) Icons.Outlined.Code else Icons.Outlined.Terminal,
                contentDescription = null,
            )
        },
        title = { Text(approval.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(approval.detail, style = MaterialTheme.typography.bodyMedium)
                if (approval.kind == ApprovalKind.USER_INPUT) {
                    approval.questions.forEach { question ->
                        if (question.header.isNotBlank()) {
                            Text(question.header, style = MaterialTheme.typography.labelLarge)
                        }
                        Text(question.question, style = MaterialTheme.typography.bodyMedium)
                        question.options.forEach { option ->
                            val selected = answers[question.id] == option
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp))
                                    .selectable(selected = selected, onClick = { answers[question.id] = option })
                                    .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (selected) Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                                else Spacer(Modifier.width(17.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(option)
                            }
                        }
                        OutlinedTextField(
                            value = answers[question.id].orEmpty(),
                            onValueChange = { answers[question.id] = it },
                            label = { Text("Response") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(5.dp)) {
                        SelectionContainer {
                            Text(approval.detail, Modifier.fillMaxWidth().padding(10.dp), style = MonoText)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onDecision("accept", answers.mapValues { listOf(it.value) })
            }, enabled = approval.kind != ApprovalKind.USER_INPUT || answers.values.all { it.isNotBlank() }) {
                Text(if (approval.kind == ApprovalKind.USER_INPUT) "Send" else "Allow once")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDecision("decline", emptyMap()) }) { Text("Deny") }
                if (approval.kind == ApprovalKind.COMMAND || approval.kind == ApprovalKind.FILE_CHANGE) {
                    TextButton(onClick = { onDecision("acceptForSession", emptyMap()) }) { Text("Allow session") }
                }
            }
        },
    )
}

@Composable
private fun RenameThreadDialog(
    thread: RemoteThread,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(thread.id) { mutableStateOf(thread.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
        title = { Text("Rename task") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Task name") },
            )
        },
        confirmButton = {
            Button(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ArchiveThreadDialog(
    thread: RemoteThread,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
        title = { Text("Archive task?") },
        text = {
            Text("${thread.title} will be removed from Projects. Its remote Codex history is preserved in the archive.")
        },
        confirmButton = { Button(onClick = onArchive) { Text("Archive") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RemoteDeviceLoginDialog(
    login: RemoteDeviceLogin,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
        title = { Text("Sign in to remote Codex") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("在浏览器中打开登录页并输入设备码。完成后此窗口会自动关闭。")
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(5.dp)) {
                    SelectionContainer {
                        Text(
                            login.userCode,
                            Modifier.fillMaxWidth().padding(14.dp),
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
                SelectionContainer {
                    Text(
                        login.verificationUrl,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onOpen) { Text("Open sign-in page") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun HostKeyConfirmationDialog(
    host: String,
    fingerprint: String,
    onTrust: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
        title = { Text("Verify SSH host") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(host, style = MaterialTheme.typography.titleMedium)
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(5.dp)) {
                    SelectionContainer {
                        Text(fingerprint, Modifier.fillMaxWidth().padding(12.dp), style = MonoText)
                    }
                }
                Text(
                    "请与服务器管理员或 ssh-keygen 输出核对此指纹。确认前不会发送登录凭据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onTrust) { Text("Trust & connect") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Cancel") } },
    )
}

private fun formatThreadTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochSeconds * 1000))
}

private fun String.displayEffort(): String = when (lowercase()) {
    "none" -> "无"
    "minimal" -> "最低"
    "low" -> "低"
    "medium" -> "中"
    "high" -> "高"
    "xhigh" -> "很高"
    "ultra" -> "极高"
    "max" -> "最高"
    else -> replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase() else character.toString()
    }
}

private fun readComposerImageAttachment(context: Context, uri: Uri): ComposerImageAttachment {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("image/") }
        ?: throw IllegalArgumentException("Only image attachments are supported")
    var displayName = "image"
    var declaredSize: Long? = null
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) displayName = cursor.getString(nameIndex)?.takeIf(String::isNotBlank) ?: displayName
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
        }
    }
    if (declaredSize != null && declaredSize!! > MAX_COMPOSER_IMAGE_BYTES) {
        throw IllegalArgumentException("Image must be 20 MB or smaller")
    }
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalArgumentException("Unable to read selected image")
    if (bytes.size > MAX_COMPOSER_IMAGE_BYTES) throw IllegalArgumentException("Image must be 20 MB or smaller")
    return ComposerImageAttachment(
        displayName = displayName,
        mimeType = mimeType,
        dataUrl = "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
    )
}

private const val MAX_COMPOSER_IMAGES = 4
private const val MAX_COMPOSER_IMAGE_BYTES = 20 * 1024 * 1024
