package com.codex.remote.data.rpc

import com.codex.remote.BuildConfig
import com.codex.remote.data.ssh.ActiveSshTransport
import com.codex.remote.domain.ApprovalKind
import com.codex.remote.domain.ApprovalQuestion
import com.codex.remote.domain.ApprovalRequest
import com.codex.remote.domain.ComposerMention
import com.codex.remote.domain.ComposerMentionKind
import com.codex.remote.domain.ComposerImageAttachment
import com.codex.remote.domain.FileChangeSummary
import com.codex.remote.domain.ForkedRemoteThread
import com.codex.remote.domain.RateLimitWindowSnapshot
import com.codex.remote.domain.ReasoningEffortOption
import com.codex.remote.domain.RemoteAccount
import com.codex.remote.domain.RemoteCollaborationMode
import com.codex.remote.domain.RemoteDeviceLogin
import com.codex.remote.domain.RemoteModel
import com.codex.remote.domain.RemoteMcpServerStatus
import com.codex.remote.domain.RemotePlugin
import com.codex.remote.domain.RemotePermissionProfile
import com.codex.remote.domain.RemotePathEntry
import com.codex.remote.domain.RemoteRateLimits
import com.codex.remote.domain.RemoteServerInfo
import com.codex.remote.domain.RemoteSkill
import com.codex.remote.domain.RemoteThread
import com.codex.remote.domain.RemoteThreadHistoryPage
import com.codex.remote.domain.RemoteThreadSession
import com.codex.remote.domain.RemoteThreadSettingsSnapshot
import com.codex.remote.domain.RemoteThreadTokenUsage
import com.codex.remote.domain.ReviewTargetKind
import com.codex.remote.domain.StartedRemoteReview
import com.codex.remote.domain.StartedRemoteThread
import com.codex.remote.domain.TimelineItem
import com.codex.remote.domain.TimelineKind
import com.codex.remote.domain.ThreadGoal
import com.codex.remote.domain.ThreadGoalStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

sealed interface AppServerEvent {
    data class ItemUpsert(val threadId: String?, val item: TimelineItem) : AppServerEvent
    data class AgentDelta(val threadId: String?, val itemId: String, val delta: String) : AppServerEvent
    data class PlanDelta(val threadId: String?, val itemId: String, val delta: String) : AppServerEvent
    data class ReasoningDelta(val threadId: String?, val itemId: String, val delta: String) : AppServerEvent
    data class OutputDelta(val threadId: String?, val itemId: String, val delta: String) : AppServerEvent
    data class TurnRunning(
        val threadId: String?,
        val running: Boolean,
        val turnId: String? = null,
    ) : AppServerEvent
    data class Approval(val threadId: String?, val request: ApprovalRequest) : AppServerEvent
    data object AccountChanged : AppServerEvent
    data object ThreadsChanged : AppServerEvent
    data object SkillsChanged : AppServerEvent
    data class GoalUpdated(val threadId: String, val goal: ThreadGoal) : AppServerEvent
    data class GoalCleared(val threadId: String) : AppServerEvent
    data class TokenUsageUpdated(val threadId: String, val usage: RemoteThreadTokenUsage) : AppServerEvent
    data class RateLimitsUpdated(val rateLimits: RemoteRateLimits) : AppServerEvent
    data class ContextCompacted(val threadId: String) : AppServerEvent
    data class McpLoginCompleted(val name: String, val success: Boolean, val error: String?) : AppServerEvent
    data class ThreadSettingsUpdated(
        val threadId: String,
        val settings: RemoteThreadSettingsSnapshot,
    ) : AppServerEvent
    data class LoginCompleted(val success: Boolean, val error: String?) : AppServerEvent
    data class Failure(val message: String, val threadId: String? = null) : AppServerEvent
    data class Warning(val message: String) : AppServerEvent
    data class Diagnostic(val message: String) : AppServerEvent
}

class RpcException(message: String, val code: Int? = null) : Exception(message)

class CodexRpcClient(
    private val transport: ActiveSshTransport,
) : Closeable {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val requestId = AtomicLong(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val writeMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<AppServerEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<AppServerEvent> = _events
    private var readerJob: Job? = null

    suspend fun initialize(): RemoteServerInfo {
        readerJob = scope.launch { readLoop() }
        scope.launch { stderrLoop() }
        val result = request(
            "initialize",
            initializeParams(),
        )
        notify("initialized", buildJsonObject {})
        return RemoteServerInfo(
            userAgent = result.string("userAgent").orEmpty(),
            codexHome = result.string("codexHome").orEmpty(),
            platformFamily = result.string("platformFamily") ?: transport.remotePlatform.name.lowercase(),
            platformOs = result.string("platformOs") ?: transport.remotePlatform.name.lowercase(),
            codexVersion = transport.codexVersion,
        )
    }

    suspend fun listThreads(): List<RemoteThread> = listThreads(archived = false)

    suspend fun listArchivedThreads(): List<RemoteThread> = listThreads(archived = true)

    private suspend fun listThreads(archived: Boolean): List<RemoteThread> = collectAllThreadPages { cursor ->
        val result = request("thread/list", threadListParams(cursor, archived))
        ThreadPage(
            threads = result.array("data").mapNotNull(::parseThread),
            nextCursor = result.string("nextCursor"),
        )
    }

    suspend fun listModels(): List<RemoteModel> = collectAllModelPages { cursor ->
        val result = request("model/list", buildJsonObject {
            put("limit", MODEL_PAGE_SIZE)
            if (!cursor.isNullOrBlank()) put("cursor", cursor)
        })
        ModelPage(
            models = result.array("data").mapNotNull(::parseModel),
            nextCursor = result.string("nextCursor"),
        )
    }

    suspend fun readRemoteDirectory(path: String): List<RemotePathEntry> {
        val result = request("fs/readDirectory", buildJsonObject { put("path", path) })
        return result.array("entries").mapNotNull { element ->
            val entry = element.asObject() ?: return@mapNotNull null
            val name = entry.string("fileName") ?: return@mapNotNull null
            RemotePathEntry(
                name = name,
                isDirectory = entry.boolean("isDirectory"),
                isFile = entry.boolean("isFile"),
            )
        }
    }

    suspend fun listCollaborationModes(): List<RemoteCollaborationMode> {
        val result = request("collaborationMode/list")
        return result.array("data").mapNotNull(::parseCollaborationMode)
    }

    suspend fun listPermissionProfiles(cwd: String?): List<RemotePermissionProfile> {
        val profiles = mutableListOf<RemotePermissionProfile>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        do {
            val result = request("permissionProfile/list", buildJsonObject {
                put("limit", PERMISSION_PROFILE_PAGE_SIZE)
                cwd?.takeIf(String::isNotBlank)?.let { put("cwd", it) }
                cursor?.let { put("cursor", it) }
            })
            profiles += result.array("data").mapNotNull(::parsePermissionProfile)
            cursor = result.string("nextCursor")?.takeIf(String::isNotBlank)
            if (cursor != null && !seenCursors.add(cursor)) {
                throw RpcException("permissionProfile/list returned a repeated nextCursor")
            }
        } while (cursor != null)
        return profiles.distinctBy { it.id }
    }

    suspend fun listSkills(cwds: List<String>, forceReload: Boolean = false): List<RemoteSkill> {
        val result = request("skills/list", skillsListParams(cwds, forceReload))
        return result.array("data")
            .flatMap { entryElement ->
                val entry = entryElement.asObject() ?: return@flatMap emptyList()
                val cwd = entry.string("cwd").orEmpty()
                entry.array("skills").mapNotNull { parseSkill(it, cwd) }
            }
            .groupBy { "${it.name}\u0000${it.path}" }
            .values
            .map { matches -> matches.first().copy(cwds = matches.flatMapTo(linkedSetOf()) { it.cwds }) }
            .sortedWith(compareBy<RemoteSkill> { it.displayName.lowercase() }.thenBy { it.name })
    }

    suspend fun listInstalledPlugins(cwds: List<String>): List<RemotePlugin> {
        val result = request("plugin/installed", pluginInstalledParams(cwds))
        return result.array("marketplaces")
            .flatMap { marketplaceElement ->
                val marketplace = marketplaceElement.asObject() ?: return@flatMap emptyList()
                val marketplaceName = marketplace.string("name").orEmpty()
                marketplace.array("plugins").mapNotNull { parsePlugin(it, marketplaceName) }
            }
            .filter { it.enabled }
            .distinctBy { it.id }
            .sortedWith(compareBy<RemotePlugin> { it.displayName.lowercase() }.thenBy { it.id })
    }

    suspend fun readAccount(): RemoteAccount {
        val result = request("account/read", buildJsonObject { put("refreshToken", false) })
        return parseAccount(result)
    }

    suspend fun startDeviceLogin(): RemoteDeviceLogin {
        val result = request("account/login/start", buildJsonObject { put("type", "chatgptDeviceCode") })
        return RemoteDeviceLogin(
            loginId = result.string("loginId") ?: throw RpcException("远端未返回登录 ID"),
            verificationUrl = result.string("verificationUrl")
                ?: throw RpcException("远端未返回设备登录地址"),
            userCode = result.string("userCode") ?: throw RpcException("远端未返回设备码"),
        )
    }

    suspend fun cancelLogin(loginId: String) {
        request("account/login/cancel", buildJsonObject { put("loginId", loginId) })
    }

    suspend fun renameThread(threadId: String, name: String) {
        request("thread/name/set", threadSetNameParams(threadId, name))
    }

    suspend fun archiveThread(threadId: String) {
        request("thread/archive", threadArchiveParams(threadId))
    }

    suspend fun unarchiveThread(threadId: String): RemoteThread {
        val result = request("thread/unarchive", threadMutationParams(threadId))
        return result["thread"]?.let(::parseThread)
            ?: throw RpcException("thread/unarchive did not return thread")
    }

    suspend fun deleteThread(threadId: String) {
        request("thread/delete", threadMutationParams(threadId))
    }

    suspend fun setThreadPinned(threadId: String, isPinned: Boolean): RemoteThread {
        val result = request("thread/metadata/update", buildJsonObject {
            put("threadId", threadId)
            put("isPinned", isPinned)
        })
        return result["thread"]?.let(::parseThread)
            ?: throw RpcException("thread/metadata/update did not return thread")
    }

    suspend fun compactThread(threadId: String) {
        request("thread/compact/start", buildJsonObject { put("threadId", threadId) })
    }

    suspend fun getThreadGoal(threadId: String): ThreadGoal? {
        val result = request("thread/goal/get", threadGoalGetParams(threadId))
        return parseThreadGoal(result["goal"] ?: JsonNull)
    }

    suspend fun setThreadGoal(
        threadId: String,
        objective: String? = null,
        status: ThreadGoalStatus? = null,
        tokenBudget: Long? = null,
    ): ThreadGoal {
        val result = request(
            "thread/goal/set",
            threadGoalSetParams(threadId, objective, status, tokenBudget),
        )
        return parseThreadGoal(result["goal"] ?: JsonNull)
            ?: throw RpcException("thread/goal/set 未返回 goal")
    }

    suspend fun clearThreadGoal(threadId: String): Boolean {
        val result = request("thread/goal/clear", threadGoalClearParams(threadId))
        return result.boolean("cleared")
    }

    suspend fun forkThread(
        threadId: String,
        cwd: String,
        model: String?,
        serviceTier: String?,
        approvalPolicy: String,
        approvalsReviewer: String,
        permissionProfile: String?,
    ): ForkedRemoteThread {
        val result = request(
            "thread/fork",
            threadForkParams(
                threadId,
                cwd,
                model,
                serviceTier,
                approvalPolicy,
                approvalsReviewer,
                permissionProfile,
            ),
        )
        val threadElement = result["thread"] ?: throw RpcException("thread/fork 未返回 thread")
        val thread = parseThread(threadElement) ?: throw RpcException("thread/fork 返回了无效 thread")
        val threadObject = threadElement.asObject()
        return ForkedRemoteThread(
            thread = thread,
            session = RemoteThreadSession(
                timeline = threadObject?.array("turns").orEmpty()
                    .flatMap { it.asObject()?.array("items").orEmpty() }
                    .mapNotNull(::parseTimelineItem),
                model = result.string("model") ?: model,
                reasoningEffort = result.string("reasoningEffort"),
                serviceTier = result.string("serviceTier") ?: serviceTier,
                cwd = result.string("cwd") ?: thread.cwd,
                collaborationMode = result.obj("collaborationMode")?.string("mode"),
                approvalPolicy = result.string("approvalPolicy") ?: approvalPolicy,
                approvalsReviewer = result.string("approvalsReviewer") ?: approvalsReviewer,
                permissionProfile = result.obj("activePermissionProfile")?.string("id") ?: permissionProfile,
            ),
        )
    }

    suspend fun startReview(
        threadId: String,
        targetKind: ReviewTargetKind,
        targetValue: String = "",
    ): StartedRemoteReview {
        val result = request("review/start", reviewStartParams(threadId, targetKind, targetValue))
        return StartedRemoteReview(
            turnId = result.obj("turn")?.string("id") ?: throw RpcException("review/start 未返回 turn.id"),
            threadId = result.string("reviewThreadId") ?: threadId,
        )
    }

    suspend fun listMcpServerStatuses(threadId: String?): List<RemoteMcpServerStatus> {
        val servers = mutableListOf<RemoteMcpServerStatus>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        do {
            val result = request("mcpServerStatus/list", buildJsonObject {
                put("limit", MCP_STATUS_PAGE_SIZE)
                put("detail", "toolsAndAuthOnly")
                threadId?.let { put("threadId", it) }
                cursor?.let { put("cursor", it) }
            })
            servers += result.array("data").mapNotNull(::parseMcpServerStatus)
            cursor = result.string("nextCursor")?.takeIf(String::isNotBlank)
            if (cursor != null && !seenCursors.add(cursor)) {
                throw RpcException("mcpServerStatus/list 返回了重复的 nextCursor")
            }
        } while (cursor != null)
        return servers.distinctBy { it.name }.sortedBy { it.name.lowercase() }
    }

    suspend fun startMcpOauthLogin(name: String, threadId: String?): String {
        val result = request("mcpServer/oauth/login", buildJsonObject {
            put("name", name)
            threadId?.let { put("threadId", it) }
        })
        return result.string("authorizationUrl")
            ?: throw RpcException("mcpServer/oauth/login did not return authorizationUrl")
    }

    suspend fun reloadMcpServers() {
        request("config/mcpServer/reload")
    }

    suspend fun submitFeedback(classification: String, reason: String, threadId: String?): String {
        val result = request("feedback/upload", feedbackUploadParams(classification, reason, threadId))
        return result.string("threadId").orEmpty()
    }

    suspend fun readRateLimits(): RemoteRateLimits {
        val result = request("account/rateLimits/read")
        return parseRateLimits(result["rateLimits"] ?: JsonNull)
            ?: throw RpcException("account/rateLimits/read 未返回 rateLimits")
    }

    suspend fun remotePathExists(path: String): Boolean = try {
        request("fs/getMetadata", buildJsonObject { put("path", path) })
        true
    } catch (error: RpcException) {
        val message = error.message.orEmpty()
        if (message.contains("ENOENT", true) || message.contains("No such file", true) ||
            message.contains("cannot find", true) || message.contains("not found", true)
        ) {
            false
        } else {
            throw error
        }
    }

    suspend fun startThread(
        cwd: String,
        model: String?,
        serviceTier: String?,
        approvalPolicy: String,
        approvalsReviewer: String,
        permissionProfile: String?,
    ): StartedRemoteThread {
        val result = request(
            "thread/start",
            threadStartParams(cwd, model, serviceTier, approvalPolicy, approvalsReviewer, permissionProfile),
        )
        return StartedRemoteThread(
            id = result.obj("thread")?.string("id") ?: throw RpcException("thread/start 未返回 thread.id"),
            model = result.string("model") ?: model,
            reasoningEffort = result.string("reasoningEffort"),
            serviceTier = result.string("serviceTier") ?: serviceTier,
            cwd = result.string("cwd") ?: cwd,
        )
    }

    suspend fun resumeThread(threadId: String, cwd: String): RemoteThreadSession = try {
        resumeThreadPaginated(threadId, cwd)
    } catch (error: RpcException) {
        if (!error.isHistoryPaginationUnavailable()) throw error
        resumeThreadLegacy(threadId, cwd)
    }

    private suspend fun resumeThreadPaginated(threadId: String, cwd: String): RemoteThreadSession {
        val result = request("thread/resume", threadResumeParams(threadId, cwd, paginated = true))
        val thread = result.obj("thread")
        val initialPage = result.obj("initialTurnsPage")
        if (initialPage != null) {
            return sessionFromResume(
                result = result,
                thread = thread,
                fallbackCwd = cwd,
                timeline = parseTurnsTimeline(initialPage.array("data")),
                olderHistoryCursor = selectOlderHistoryCursor(
                    initialPageCursor = initialPage.string("nextCursor"),
                    turnsBackwardsCursor = result.string("turnsBackwardsCursor"),
                ),
            )
        }

        // Older servers may ignore the experimental fields. Read their legacy history
        // without issuing a second resume against an already-loaded thread.
        val inlineTurns = thread?.array("turns").orEmpty()
        val legacyThread = if (inlineTurns.isNotEmpty()) {
            thread
        } else {
            request("thread/read", buildJsonObject {
                put("threadId", threadId)
                put("includeTurns", true)
            }).obj("thread") ?: thread
        }
        return sessionFromResume(
            result = result,
            thread = legacyThread,
            fallbackCwd = cwd,
            timeline = parseTurnsTimeline(legacyThread?.array("turns").orEmpty(), descending = false),
        )
    }

    private suspend fun resumeThreadLegacy(threadId: String, cwd: String): RemoteThreadSession {
        val result = request("thread/resume", threadResumeParams(threadId, cwd, paginated = false))
        val thread = result.obj("thread")
        return sessionFromResume(
            result = result,
            thread = thread,
            fallbackCwd = cwd,
            timeline = parseTurnsTimeline(thread?.array("turns").orEmpty(), descending = false),
        )
    }

    private fun sessionFromResume(
        result: JsonObject,
        thread: JsonObject?,
        fallbackCwd: String,
        timeline: List<TimelineItem>,
        olderHistoryCursor: String? = null,
    ) = RemoteThreadSession(
        timeline = timeline,
        model = result.string("model"),
        reasoningEffort = result.string("reasoningEffort"),
        serviceTier = result.string("serviceTier"),
        cwd = result.string("cwd") ?: thread?.string("cwd") ?: fallbackCwd,
        olderHistoryCursor = olderHistoryCursor,
        collaborationMode = result.obj("collaborationMode")?.string("mode"),
        approvalPolicy = result.string("approvalPolicy"),
        approvalsReviewer = result.string("approvalsReviewer"),
        permissionProfile = result.obj("activePermissionProfile")?.string("id"),
    )

    suspend fun loadOlderThreadHistory(threadId: String, cursor: String): RemoteThreadHistoryPage {
        val result = request("thread/turns/list", threadTurnsListParams(threadId, cursor))
        return RemoteThreadHistoryPage(
            timeline = parseTurnsTimeline(result.array("data")),
            nextCursor = checkedNextHistoryCursor(
                returnedCursor = result.string("nextCursor"),
                consumedCursors = setOf(cursor),
            ),
        )
    }

    suspend fun startTurn(
        threadId: String,
        text: String,
        cwd: String,
        model: String?,
        reasoningEffort: String?,
        serviceTier: String?,
        approvalPolicy: String,
        approvalsReviewer: String,
        permissionProfile: String?,
        collaborationMode: RemoteCollaborationMode?,
        mentions: List<ComposerMention> = emptyList(),
        attachments: List<ComposerImageAttachment> = emptyList(),
    ) {
        request(
            "turn/start",
            turnStartParams(
                threadId,
                text,
                cwd,
                model,
                reasoningEffort,
                serviceTier,
                approvalPolicy,
                approvalsReviewer,
                permissionProfile,
                collaborationMode,
                mentions,
                attachments,
            ),
        )
    }

    suspend fun steerTurn(
        threadId: String,
        expectedTurnId: String,
        text: String,
        mentions: List<ComposerMention> = emptyList(),
        attachments: List<ComposerImageAttachment> = emptyList(),
    ) {
        request(
            "turn/steer",
            turnSteerParams(threadId, expectedTurnId, text, mentions, attachments),
        )
    }

    suspend fun interruptTurn(threadId: String, turnId: String) {
        request("turn/interrupt", buildJsonObject {
            put("threadId", threadId)
            put("turnId", turnId)
        })
    }

    suspend fun respondToApproval(
        request: ApprovalRequest,
        decision: String,
        answers: Map<String, List<String>> = emptyMap(),
    ) {
        val result = when (request.kind) {
            ApprovalKind.USER_INPUT -> buildJsonObject {
                put("answers", buildJsonObject {
                    request.questions.forEach { question ->
                        val values = answers[question.id].orEmpty()
                        if (values.isEmpty()) return@forEach
                        put(question.id, buildJsonObject {
                            put("answers", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
                        })
                    }
                })
            }
            ApprovalKind.PERMISSION -> buildJsonObject {
                val original = runCatching { json.parseToJsonElement(request.rawParams).jsonObject }.getOrNull()
                put(
                    "permissions",
                    if (decision == "accept") original?.obj("permissions") ?: buildJsonObject {}
                    else buildJsonObject {},
                )
                put("scope", if (decision == "acceptForSession") "session" else "turn")
            }
            else -> buildJsonObject { put("decision", decision) }
        }
        respond(request.requestId, result)
    }

    suspend fun request(method: String, params: JsonObject = buildJsonObject {}): JsonObject {
        val id = requestId.getAndIncrement().toString()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        try {
            send(buildJsonObject {
                put("method", method)
                put("id", id.toLong())
                put("params", params)
            })
            return deferred.await()
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun notify(method: String, params: JsonObject) = send(buildJsonObject {
        put("method", method)
        put("params", params)
    })

    private suspend fun respond(id: String, result: JsonObject) = send(buildJsonObject {
        id.toLongOrNull()?.let { put("id", it) } ?: put("id", id)
        put("result", result)
    })

    private suspend fun send(message: JsonObject) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            transport.writer.write(json.encodeToString(JsonObject.serializer(), message))
            transport.writer.newLine()
            transport.writer.flush()
        }
    }

    private suspend fun readLoop() {
        try {
            while (true) {
                val line = transport.reader.readLine() ?: break
                if (line.isBlank()) continue
                val message = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
                if (message == null) {
                    _events.emit(AppServerEvent.Diagnostic("无法解析 app-server 输出：$line"))
                    continue
                }
                val id = message["id"]?.jsonPrimitive?.contentOrNull
                if (id != null && (message.containsKey("result") || message.containsKey("error"))) {
                    val deferred = pending.remove(id)
                    val error = message.obj("error")
                    if (error != null) {
                        deferred?.completeExceptionally(
                            RpcException(
                                error.string("message") ?: "RPC 请求失败",
                                error["code"]?.jsonPrimitive?.longOrNull?.toInt(),
                            ),
                        )
                    } else {
                        deferred?.complete(message.obj("result") ?: buildJsonObject {})
                    }
                    continue
                }
                val method = message.string("method") ?: continue
                val params = message.obj("params") ?: buildJsonObject {}
                if (id != null) handleServerRequest(id, method, params) else handleNotification(method, params)
            }
            _events.emit(AppServerEvent.Failure("远端 app-server 已断开"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            _events.emit(AppServerEvent.Failure(error.message ?: "SSH 数据流已中断"))
        } finally {
            val error = RpcException("远端连接已关闭")
            pending.values.forEach { it.completeExceptionally(error) }
            pending.clear()
        }
    }

    private suspend fun stderrLoop() {
        runCatching {
            while (true) {
                val line = transport.errorReader.readLine() ?: break
                if (line.isNotBlank()) _events.emit(AppServerEvent.Diagnostic(line))
            }
        }
    }

    private suspend fun handleNotification(method: String, params: JsonObject) {
        when (method) {
            "item/started", "item/completed" -> params.obj("item")?.let(::parseTimelineItem)?.let { item ->
                val status = item.status.ifBlank {
                    if (method == "item/started") "inProgress" else "completed"
                }
                _events.emit(AppServerEvent.ItemUpsert(params.string("threadId"), item.copy(status = status)))
            }
            "item/agentMessage/delta" -> _events.emit(
                AppServerEvent.AgentDelta(
                    params.string("threadId"),
                    params.string("itemId").orEmpty(),
                    params.string("delta").orEmpty(),
                ),
            )
            "item/plan/delta" -> _events.emit(
                AppServerEvent.PlanDelta(
                    params.string("threadId"),
                    params.string("itemId").orEmpty(),
                    params.string("delta").orEmpty(),
                ),
            )
            "item/reasoning/summaryTextDelta", "item/reasoning/textDelta" -> _events.emit(
                AppServerEvent.ReasoningDelta(
                    params.string("threadId"),
                    params.string("itemId").orEmpty(),
                    params.string("delta").orEmpty(),
                ),
            )
            "item/commandExecution/outputDelta" -> _events.emit(
                AppServerEvent.OutputDelta(
                    params.string("threadId"),
                    params.string("itemId").orEmpty(),
                    params.string("delta").orEmpty(),
                ),
            )
            "turn/diff/updated" -> Unit
            "turn/started" -> _events.emit(
                AppServerEvent.TurnRunning(
                    threadId = params.string("threadId"),
                    running = true,
                    turnId = params.obj("turn")?.string("id"),
                ),
            )
            "turn/completed" -> {
                val turn = params.obj("turn")
                val turnError = turn?.obj("error")?.string("message")
                val threadId = params.string("threadId")
                if (!turnError.isNullOrBlank()) _events.emit(AppServerEvent.Failure(turnError, threadId))
                _events.emit(AppServerEvent.TurnRunning(threadId, false, turn?.string("id")))
            }
            "account/updated" -> _events.emit(AppServerEvent.AccountChanged)
            "thread/started", "thread/status/changed", "thread/archived", "thread/deleted", "thread/closed",
            "thread/name/updated", "thread/unarchived" ->
                _events.emit(AppServerEvent.ThreadsChanged)
            "skills/changed" -> _events.emit(AppServerEvent.SkillsChanged)
            "thread/goal/updated" -> {
                val goal = params["goal"]?.let(::parseThreadGoal) ?: return
                val threadId = params.string("threadId") ?: goal.threadId
                _events.emit(AppServerEvent.GoalUpdated(threadId, goal))
            }
            "thread/goal/cleared" -> params.string("threadId")?.let { threadId ->
                _events.emit(AppServerEvent.GoalCleared(threadId))
            }
            "thread/tokenUsage/updated" -> {
                val usage = params["tokenUsage"]?.let(::parseThreadTokenUsage) ?: return
                params.string("threadId")?.let { threadId ->
                    _events.emit(AppServerEvent.TokenUsageUpdated(threadId, usage))
                }
            }
            "account/rateLimits/updated" -> {
                params["rateLimits"]?.let(::parseRateLimits)?.let { rateLimits ->
                    _events.emit(AppServerEvent.RateLimitsUpdated(rateLimits))
                }
            }
            "thread/compacted" -> params.string("threadId")?.let { threadId ->
                _events.emit(AppServerEvent.ContextCompacted(threadId))
            }
            "mcpServer/oauthLogin/completed" -> _events.emit(
                AppServerEvent.McpLoginCompleted(
                    name = params.string("name").orEmpty(),
                    success = params.boolean("success"),
                    error = params.string("error"),
                ),
            )
            "thread/settings/updated" -> {
                val threadId = params.string("threadId") ?: return
                val settings = params.obj("threadSettings") ?: return
                _events.emit(
                    AppServerEvent.ThreadSettingsUpdated(
                        threadId = threadId,
                        settings = RemoteThreadSettingsSnapshot(
                            model = settings.string("model"),
                            reasoningEffort = settings.string("effort"),
                            serviceTier = settings.string("serviceTier"),
                            collaborationMode = settings.obj("collaborationMode")?.string("mode"),
                            permissionProfile = settings.obj("activePermissionProfile")?.string("id"),
                            approvalPolicy = settings.string("approvalPolicy"),
                            approvalsReviewer = settings.string("approvalsReviewer"),
                        ),
                    ),
                )
            }
            "account/login/completed" -> _events.emit(
                AppServerEvent.LoginCompleted(
                    success = params.boolean("success"),
                    error = params.string("error"),
                ),
            )
            "error" -> {
                val error = params.obj("error")
                _events.emit(
                    AppServerEvent.Failure(
                        error?.string("message") ?: "Codex turn 执行失败",
                        params.string("threadId"),
                    ),
                )
            }
            "warning", "guardianWarning", "deprecationNotice", "configWarning" -> {
                val message = params.string("message") ?: params.obj("warning")?.string("message")
                if (!message.isNullOrBlank()) _events.emit(AppServerEvent.Warning(message))
            }
        }
    }

    private suspend fun handleServerRequest(id: String, method: String, params: JsonObject) {
        val request = when (method) {
            "item/commandExecution/requestApproval", "execCommandApproval" -> ApprovalRequest(
                requestId = id,
                kind = ApprovalKind.COMMAND,
                title = "允许执行命令？",
                detail = params.string("command") ?: params.string("reason") ?: "远端 Codex 请求执行命令",
                rawMethod = method,
                rawParams = params.toString(),
            )
            "item/fileChange/requestApproval", "applyPatchApproval" -> ApprovalRequest(
                requestId = id,
                kind = ApprovalKind.FILE_CHANGE,
                title = "允许修改文件？",
                detail = params.string("reason") ?: params.string("grantRoot") ?: "远端 Codex 请求写入项目",
                rawMethod = method,
                rawParams = params.toString(),
            )
            "item/permissions/requestApproval" -> ApprovalRequest(
                requestId = id,
                kind = ApprovalKind.PERMISSION,
                title = "允许额外权限？",
                detail = params.string("reason") ?: "远端 Codex 请求额外文件或网络权限",
                rawMethod = method,
                rawParams = params.toString(),
            )
            "item/tool/requestUserInput" -> {
                val questions = params.array("questions").mapNotNull { element ->
                    val question = element.asObject() ?: return@mapNotNull null
                    val questionId = question.string("id") ?: return@mapNotNull null
                    ApprovalQuestion(
                        id = questionId,
                        header = question.string("header").orEmpty(),
                        question = question.string("question") ?: "请输入回复",
                        options = question.array("options").mapNotNull { it.asObject()?.string("label") },
                    )
                }
                ApprovalRequest(
                    requestId = id,
                    kind = ApprovalKind.USER_INPUT,
                    title = questions.firstOrNull()?.header?.ifBlank { null } ?: "Codex 需要你的输入",
                    detail = questions.firstOrNull()?.question ?: "请输入回复",
                    rawMethod = method,
                    rawParams = params.toString(),
                    questions = questions,
                )
            }
            else -> ApprovalRequest(id, ApprovalKind.UNKNOWN, "远端请求", method, method, params.toString())
        }
        _events.emit(AppServerEvent.Approval(params.string("threadId"), request))
    }

    companion object {
        internal fun initializeParams(): JsonObject = buildJsonObject {
            put("clientInfo", buildJsonObject {
                put("name", "codex_remote_android")
                put("title", "Codex Remote for Android")
                put("version", BuildConfig.VERSION_NAME)
            })
            put("capabilities", buildJsonObject {
                put("experimentalApi", true)
                put("requestAttestation", false)
            })
        }

        internal fun threadResumeParams(
            threadId: String,
            cwd: String,
            paginated: Boolean,
        ): JsonObject = buildJsonObject {
            put("threadId", threadId)
            if (cwd.isNotBlank()) put("cwd", cwd)
            if (paginated) {
                put("excludeTurns", true)
                put("initialTurnsPage", buildJsonObject {
                    put("limit", THREAD_HISTORY_PAGE_SIZE)
                    put("sortDirection", "desc")
                    put("itemsView", "full")
                })
            }
        }

        internal fun threadTurnsListParams(threadId: String, cursor: String): JsonObject = buildJsonObject {
            put("threadId", threadId)
            put("cursor", cursor)
            put("limit", THREAD_HISTORY_PAGE_SIZE)
            put("sortDirection", "desc")
            put("itemsView", "full")
        }

        internal fun parseTurnsTimeline(
            turns: List<JsonElement>,
            descending: Boolean = true,
        ): List<TimelineItem> = (if (descending) turns.asReversed() else turns)
            .flatMap { it.asObject()?.array("items").orEmpty() }
            .mapNotNull(::parseTimelineItem)

        internal fun checkedNextHistoryCursor(
            returnedCursor: String?,
            consumedCursors: Set<String>,
        ): String? {
            val cursor = returnedCursor?.takeIf(String::isNotBlank) ?: return null
            if (cursor in consumedCursors) {
                throw RpcException("thread/turns/list 返回了重复的 nextCursor")
            }
            return cursor
        }

        internal fun threadListParams(cursor: String?, archived: Boolean = false): JsonObject = buildJsonObject {
            put("limit", THREAD_PAGE_SIZE)
            put("sortKey", "updated_at")
            put("sortDirection", "desc")
            put("archived", archived)
            // Desktop passes an empty filter so app-server includes every interactive source.
            put("sourceKinds", buildJsonArray {})
            if (!cursor.isNullOrBlank()) put("cursor", cursor)
        }

        internal fun threadStartParams(
            cwd: String,
            model: String?,
            serviceTier: String? = null,
            approvalPolicy: String,
            approvalsReviewer: String = "user",
            permissionProfile: String? = null,
        ): JsonObject = buildJsonObject {
            put("cwd", cwd)
            put("approvalPolicy", approvalPolicy)
            put("approvalsReviewer", approvalsReviewer)
            if (permissionProfile.isNullOrBlank()) {
                put("sandbox", if (approvalPolicy == "never") "danger-full-access" else "workspace-write")
            }
            else put("permissions", permissionProfile)
            if (!model.isNullOrBlank()) put("model", model)
            if (!serviceTier.isNullOrBlank()) put("serviceTier", serviceTier)
        }

        internal fun threadSetNameParams(threadId: String, name: String): JsonObject = buildJsonObject {
            put("threadId", threadId)
            put("name", name)
        }

        internal fun threadArchiveParams(threadId: String): JsonObject = buildJsonObject {
            put("threadId", threadId)
        }

        internal fun threadMutationParams(threadId: String): JsonObject = buildJsonObject {
            put("threadId", threadId)
        }

        internal fun feedbackUploadParams(
            classification: String,
            reason: String,
            threadId: String?,
        ): JsonObject = buildJsonObject {
            put("classification", classification)
            reason.trim().takeIf(String::isNotEmpty)?.let { put("reason", it) }
            threadId?.let { put("threadId", it) }
            put("includeLogs", true)
            put("tags", buildJsonObject { put("client", "codex_remote_android") })
        }

        internal fun threadGoalGetParams(threadId: String): JsonObject = buildJsonObject {
            put("threadId", threadId)
        }

        internal fun threadGoalSetParams(
            threadId: String,
            objective: String? = null,
            status: ThreadGoalStatus? = null,
            tokenBudget: Long? = null,
        ): JsonObject = buildJsonObject {
            put("threadId", threadId)
            objective?.let { put("objective", it) }
            status?.let { put("status", it.wireValue()) }
            tokenBudget?.let { put("tokenBudget", it) }
        }

        internal fun threadGoalClearParams(threadId: String): JsonObject = buildJsonObject {
            put("threadId", threadId)
        }

        internal fun threadForkParams(
            threadId: String,
            cwd: String,
            model: String?,
            serviceTier: String? = null,
            approvalPolicy: String,
            approvalsReviewer: String = "user",
            permissionProfile: String? = null,
        ): JsonObject = buildJsonObject {
            put("threadId", threadId)
            if (cwd.isNotBlank()) put("cwd", cwd)
            if (!model.isNullOrBlank()) put("model", model)
            if (!serviceTier.isNullOrBlank()) put("serviceTier", serviceTier)
            put("approvalPolicy", approvalPolicy)
            put("approvalsReviewer", approvalsReviewer)
            if (permissionProfile.isNullOrBlank()) {
                put("sandbox", if (approvalPolicy == "never") "danger-full-access" else "workspace-write")
            }
            else put("permissions", permissionProfile)
            put("ephemeral", false)
        }

        internal fun reviewStartParams(
            threadId: String,
            targetKind: ReviewTargetKind,
            targetValue: String,
        ): JsonObject = buildJsonObject {
            put("threadId", threadId)
            put("delivery", "inline")
            put("target", buildJsonObject {
                when (targetKind) {
                    ReviewTargetKind.UNCOMMITTED_CHANGES -> put("type", "uncommittedChanges")
                    ReviewTargetKind.BASE_BRANCH -> {
                        put("type", "baseBranch")
                        put("branch", targetValue.trim())
                    }
                    ReviewTargetKind.CUSTOM -> {
                        put("type", "custom")
                        put("instructions", targetValue.trim())
                    }
                }
            })
        }

        internal fun skillsListParams(cwds: List<String>, forceReload: Boolean): JsonObject = buildJsonObject {
            if (cwds.isNotEmpty()) {
                put("cwds", buildJsonArray { cwds.distinct().forEach { add(JsonPrimitive(it)) } })
            }
            if (forceReload) put("forceReload", true)
        }

        internal fun pluginInstalledParams(cwds: List<String>): JsonObject = buildJsonObject {
            if (cwds.isNotEmpty()) {
                put("cwds", buildJsonArray { cwds.distinct().forEach { add(JsonPrimitive(it)) } })
            }
        }

        internal fun turnStartParams(
            threadId: String,
            text: String,
            cwd: String,
            model: String?,
            reasoningEffort: String?,
            serviceTier: String? = null,
            approvalPolicy: String,
            approvalsReviewer: String = "user",
            permissionProfile: String? = null,
            collaborationMode: RemoteCollaborationMode? = null,
            mentions: List<ComposerMention>,
            attachments: List<ComposerImageAttachment> = emptyList(),
        ): JsonObject = buildJsonObject {
            put("threadId", threadId)
            if (cwd.isNotBlank()) put("cwd", cwd)
            put("approvalPolicy", approvalPolicy)
            put("approvalsReviewer", approvalsReviewer)
            if (!permissionProfile.isNullOrBlank()) {
                put("permissions", permissionProfile)
            } else if (approvalPolicy == "never") {
                put("sandboxPolicy", buildJsonObject { put("type", "dangerFullAccess") })
            }
            if (!model.isNullOrBlank()) put("model", model)
            if (!reasoningEffort.isNullOrBlank()) put("effort", reasoningEffort)
            if (!serviceTier.isNullOrBlank()) put("serviceTier", serviceTier)
            if (collaborationMode != null && !model.isNullOrBlank()) {
                put("collaborationMode", collaborationModeParam(collaborationMode, model, reasoningEffort))
            }
            put("input", userInputs(text, mentions, attachments))
        }

        internal fun turnSteerParams(
            threadId: String,
            expectedTurnId: String,
            text: String,
            mentions: List<ComposerMention>,
            attachments: List<ComposerImageAttachment>,
        ): JsonObject = buildJsonObject {
            put("threadId", threadId)
            put("expectedTurnId", expectedTurnId)
            put("input", userInputs(text, mentions, attachments))
        }

        private fun userInputs(
            text: String,
            mentions: List<ComposerMention>,
            attachments: List<ComposerImageAttachment>,
        ): JsonArray = buildJsonArray {
            if (text.isNotBlank()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                    put("text_elements", buildJsonArray {})
                })
            }
            attachments.distinctBy { it.id }.forEach { attachment ->
                add(buildJsonObject {
                    put("type", "image")
                    put("url", attachment.dataUrl)
                })
            }
            mentions.distinctBy { "${it.kind}:${it.path}" }.forEach { mention ->
                add(buildJsonObject {
                    put("type", if (mention.kind == ComposerMentionKind.SKILL) "skill" else "mention")
                    put("name", mention.name)
                    put("path", mention.path)
                })
            }
        }

        private fun collaborationModeParam(
            collaborationMode: RemoteCollaborationMode,
            model: String,
            reasoningEffort: String?,
        ): JsonObject = buildJsonObject {
            put("mode", collaborationMode.mode)
            put("settings", buildJsonObject {
                put("model", collaborationMode.model ?: model)
                val effort = collaborationMode.reasoningEffort ?: reasoningEffort
                if (effort == null) put("reasoning_effort", JsonNull) else put("reasoning_effort", effort)
                put("developer_instructions", JsonNull)
            })
        }

        internal fun parseModel(element: JsonElement): RemoteModel? {
            val model = element.asObject() ?: return null
            val id = model.string("model") ?: model.string("id") ?: return null
            val serviceTiers = model.array("serviceTiers").mapNotNull { tierElement ->
                val tier = tierElement.asObject() ?: return@mapNotNull null
                val tierId = tier.string("id") ?: return@mapNotNull null
                com.codex.remote.domain.RemoteServiceTier(
                    id = tierId,
                    name = tier.string("name") ?: tierId,
                    description = tier.string("description").orEmpty(),
                )
            }.ifEmpty {
                model.array("additionalSpeedTiers").mapNotNull { tier ->
                    tier.jsonPrimitive.contentOrNull?.let {
                        com.codex.remote.domain.RemoteServiceTier(it, it, "")
                    }
                }
            }
            return RemoteModel(
                id = id,
                displayName = model.string("displayName") ?: id,
                description = model.string("description").orEmpty(),
                isDefault = model.boolean("isDefault"),
                hidden = model.boolean("hidden"),
                supportedReasoningEfforts = model.array("supportedReasoningEfforts").mapNotNull { option ->
                    val item = option.asObject() ?: return@mapNotNull null
                    val value = item.string("reasoningEffort") ?: return@mapNotNull null
                    ReasoningEffortOption(value = value, description = item.string("description").orEmpty())
                },
                defaultReasoningEffort = model.string("defaultReasoningEffort"),
                inputModalities = model.array("inputModalities")
                    .mapNotNullTo(linkedSetOf()) { it.jsonPrimitive.contentOrNull },
                serviceTiers = serviceTiers,
                defaultServiceTier = model.string("defaultServiceTier"),
            )
        }

        internal fun parseCollaborationMode(element: JsonElement): RemoteCollaborationMode? {
            val mode = element.asObject() ?: return null
            val wireMode = mode.string("mode") ?: return null
            return RemoteCollaborationMode(
                name = mode.string("name") ?: wireMode,
                mode = wireMode,
                model = mode.string("model"),
                reasoningEffort = mode.string("reasoning_effort") ?: mode.string("reasoningEffort"),
            )
        }

        internal fun parsePermissionProfile(element: JsonElement): RemotePermissionProfile? {
            val profile = element.asObject() ?: return null
            val id = profile.string("id") ?: return null
            return RemotePermissionProfile(
                id = id,
                description = profile.string("description").orEmpty(),
                allowed = profile.booleanOrDefault("allowed", true),
            )
        }

        internal fun parseAccount(result: JsonObject): RemoteAccount {
            val account = result.obj("account")
            return RemoteAccount(
                type = account?.string("type"),
                email = account?.string("email"),
                planType = account?.string("planType"),
                requiresOpenaiAuth = result.boolean("requiresOpenaiAuth"),
            )
        }

        internal fun parseThreadGoal(element: JsonElement): ThreadGoal? {
            val goal = element.asObject() ?: return null
            val threadId = goal.string("threadId") ?: return null
            val objective = goal.string("objective") ?: return null
            val status = goal.string("status")?.toThreadGoalStatus() ?: return null
            return ThreadGoal(
                threadId = threadId,
                objective = objective,
                status = status,
                tokenBudget = goal["tokenBudget"]?.jsonPrimitive?.longOrNull,
                tokensUsed = goal["tokensUsed"]?.jsonPrimitive?.longOrNull ?: 0,
                timeUsedSeconds = goal["timeUsedSeconds"]?.jsonPrimitive?.longOrNull ?: 0,
                createdAt = goal["createdAt"]?.jsonPrimitive?.longOrNull ?: 0,
                updatedAt = goal["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0,
            )
        }

        internal fun parseMcpServerStatus(element: JsonElement): RemoteMcpServerStatus? {
            val status = element.asObject() ?: return null
            val name = status.string("name") ?: return null
            return RemoteMcpServerStatus(
                name = name,
                authStatus = status.string("authStatus").orEmpty(),
                toolCount = status.obj("tools")?.size ?: 0,
                resourceCount = status.array("resources").size + status.array("resourceTemplates").size,
            )
        }

        internal fun parseRateLimits(element: JsonElement): RemoteRateLimits? {
            val limits = element.asObject() ?: return null
            val credits = limits.obj("credits")
            return RemoteRateLimits(
                limitName = limits.string("limitName"),
                planType = limits.string("planType"),
                primary = limits["primary"]?.let(::parseRateLimitWindow),
                secondary = limits["secondary"]?.let(::parseRateLimitWindow),
                creditsBalance = credits?.string("balance"),
                creditsUnlimited = credits?.boolean("unlimited") ?: false,
            )
        }

        internal fun parseThreadTokenUsage(element: JsonElement): RemoteThreadTokenUsage? {
            val usage = element.asObject() ?: return null
            val total = usage.obj("total") ?: return null
            return RemoteThreadTokenUsage(
                totalTokens = total["totalTokens"]?.jsonPrimitive?.longOrNull ?: 0,
                inputTokens = total["inputTokens"]?.jsonPrimitive?.longOrNull ?: 0,
                outputTokens = total["outputTokens"]?.jsonPrimitive?.longOrNull ?: 0,
                modelContextWindow = usage["modelContextWindow"]?.jsonPrimitive?.longOrNull,
            )
        }

        private fun parseRateLimitWindow(element: JsonElement): RateLimitWindowSnapshot? {
            val window = element.asObject() ?: return null
            return RateLimitWindowSnapshot(
                usedPercent = window["usedPercent"]?.jsonPrimitive?.doubleOrNull ?: return null,
                windowDurationMinutes = window["windowDurationMins"]?.jsonPrimitive?.longOrNull,
                resetsAt = window["resetsAt"]?.jsonPrimitive?.longOrNull,
            )
        }

        internal fun parseSkill(element: JsonElement, cwd: String): RemoteSkill? {
            val skill = element.asObject() ?: return null
            val name = skill.string("name") ?: return null
            val path = skill.string("path") ?: return null
            val interfaceData = skill.obj("interface")
            return RemoteSkill(
                name = name,
                displayName = interfaceData?.string("displayName") ?: name,
                description = interfaceData?.string("shortDescription")
                    ?: skill.string("shortDescription")
                    ?: skill.string("description").orEmpty(),
                path = path,
                enabled = skill.booleanOrDefault("enabled", true),
                cwds = setOf(cwd),
            )
        }

        internal fun parsePlugin(element: JsonElement, marketplace: String): RemotePlugin? {
            val plugin = element.asObject() ?: return null
            val id = plugin.string("id") ?: return null
            val name = plugin.string("name") ?: id.substringBefore('@')
            val interfaceData = plugin.obj("interface")
            val available = plugin.string("availability")?.equals("DISABLED_BY_ADMIN", ignoreCase = true) != true
            return RemotePlugin(
                id = id,
                name = name,
                displayName = interfaceData?.string("displayName") ?: name,
                description = interfaceData?.string("shortDescription").orEmpty(),
                marketplace = marketplace,
                mentionPath = "plugin://$id",
                enabled = plugin.booleanOrDefault("installed", true) &&
                    plugin.booleanOrDefault("enabled", true) && available,
            )
        }

        internal fun parseTimelineItem(element: JsonElement): TimelineItem? {
            val item = element.asObject() ?: return null
            val type = item.string("type") ?: return null
            val id = item.string("id") ?: "$type-${item.hashCode()}"
            return when (type) {
                "userMessage" -> TimelineItem(
                    id,
                    TimelineKind.USER,
                    body = item.array("content").mapNotNull { contentElement ->
                        val content = contentElement.asObject() ?: return@mapNotNull null
                        when (content.string("type")) {
                            "text" -> content.string("text")
                            "image", "localImage" -> "[Image]"
                            "audio", "localAudio" -> "[Audio]"
                            "skill", "mention" -> content.string("name")?.let { "\$$it" }
                            else -> null
                        }
                    }.joinToString("\n"),
                )
                "agentMessage" -> TimelineItem(id, TimelineKind.AGENT, body = item.string("text").orEmpty())
                "reasoning" -> TimelineItem(
                    id,
                    TimelineKind.REASONING,
                    title = "思考过程",
                    body = (item.array("summary").ifEmpty { item.array("content") })
                        .joinToString("\n") { it.jsonPrimitive.contentOrNull.orEmpty() },
                )
                "plan" -> TimelineItem(id, TimelineKind.PLAN, title = "计划", body = item.string("text").orEmpty())
                "commandExecution" -> TimelineItem(
                    id,
                    TimelineKind.COMMAND,
                    title = item.string("command").orEmpty(),
                    body = item.string("aggregatedOutput").orEmpty(),
                    status = item.string("status").orEmpty(),
                )
                "fileChange" -> {
                    val changes = item.array("changes").mapNotNull { changeElement ->
                        val change = changeElement.asObject() ?: return@mapNotNull null
                        val path = change.string("path")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                        val kind = change.obj("kind")?.string("type")
                            ?: change.string("kind")
                            ?: "update"
                        FileChangeSummary(
                            path = path,
                            kind = kind,
                            diff = change.string("diff").orEmpty(),
                        )
                    }
                    TimelineItem(
                        id,
                        TimelineKind.FILE_CHANGE,
                        title = changes.joinToString(", ") { it.path },
                        status = item.string("status").orEmpty(),
                        fileChanges = changes,
                    )
                }
                "mcpToolCall", "dynamicToolCall", "collabAgentToolCall" -> TimelineItem(
                    id,
                    TimelineKind.TOOL,
                    title = item.string("tool") ?: type,
                    body = item["arguments"]?.toString()
                        ?: item.string("prompt")
                        ?: item["result"]?.toString().orEmpty(),
                    status = item.string("status").orEmpty(),
                )
                "subAgentActivity" -> TimelineItem(
                    id,
                    TimelineKind.TOOL,
                    title = "Sub-agent ${item.string("kind").orEmpty()}",
                    body = item.string("agentPath").orEmpty(),
                )
                "webSearch" -> TimelineItem(
                    id,
                    TimelineKind.TOOL,
                    title = "Web search",
                    body = item.string("query").orEmpty(),
                )
                "imageView" -> TimelineItem(
                    id,
                    TimelineKind.TOOL,
                    title = "Viewed image",
                    body = item.string("path").orEmpty(),
                )
                "imageGeneration" -> TimelineItem(
                    id,
                    TimelineKind.TOOL,
                    title = "Image generation",
                    body = item.string("savedPath") ?: item.string("revisedPrompt").orEmpty(),
                    status = item.string("status").orEmpty(),
                )
                "enteredReviewMode", "exitedReviewMode" -> TimelineItem(
                    id,
                    TimelineKind.REVIEW,
                    title = if (type == "enteredReviewMode") "Code review started" else "Code review completed",
                    body = item.string("review").orEmpty(),
                )
                "contextCompaction" -> TimelineItem(
                    id,
                    TimelineKind.COMPACTION,
                    title = "Context compacted",
                )
                "hookPrompt" -> TimelineItem(id, TimelineKind.TOOL, title = "Hook", body = "Prompt context added")
                "sleep" -> TimelineItem(id, TimelineKind.TOOL, title = "Wait", body = "Codex waited before continuing")
                else -> null
            }
        }

        internal fun parseThread(element: JsonElement): RemoteThread? {
        val item = element.asObject() ?: return null
        val id = item.string("id") ?: return null
        return RemoteThread(
            id = id,
            title = item.string("name") ?: item.string("preview")?.take(80).orEmpty().ifBlank { "新会话" },
            cwd = item.string("cwd").orEmpty(),
            updatedAt = item["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0,
            isPinned = item.boolean("isPinned"),
            status = when (val status = item["status"]) {
                is JsonPrimitive -> status.contentOrNull.orEmpty()
                is JsonObject -> status.string("type") ?: status.keys.firstOrNull().orEmpty()
                else -> ""
            },
        )
        }
    }

    override fun close() {
        readerJob?.cancel()
        scope.cancel()
        transport.close()
    }
}

internal data class ThreadPage(
    val threads: List<RemoteThread>,
    val nextCursor: String?,
)

internal data class ModelPage(
    val models: List<RemoteModel>,
    val nextCursor: String?,
)

internal suspend fun collectAllThreadPages(
    loadPage: suspend (cursor: String?) -> ThreadPage,
): List<RemoteThread> {
    val threadsById = linkedMapOf<String, RemoteThread>()
    val usedCursors = mutableSetOf<String>()
    var cursor: String? = null
    do {
        val page = loadPage(cursor)
        page.threads.forEach { thread ->
            val current = threadsById[thread.id]
            if (current == null || thread.updatedAt >= current.updatedAt) threadsById[thread.id] = thread
        }
        cursor = page.nextCursor?.takeIf(String::isNotBlank)
        if (cursor != null && !usedCursors.add(cursor)) {
            throw RpcException("thread/list 返回了重复的 nextCursor")
        }
    } while (cursor != null)
    return threadsById.values.sortedWith(compareByDescending<RemoteThread> { it.updatedAt }.thenBy { it.id })
}

internal suspend fun collectAllModelPages(
    loadPage: suspend (cursor: String?) -> ModelPage,
): List<RemoteModel> {
    val modelsById = linkedMapOf<String, RemoteModel>()
    val usedCursors = mutableSetOf<String>()
    var cursor: String? = null
    do {
        val page = loadPage(cursor)
        page.models.forEach { model -> modelsById[model.id] = model }
        cursor = page.nextCursor?.takeIf(String::isNotBlank)
        if (cursor != null && !usedCursors.add(cursor)) {
            throw RpcException("model/list 返回了重复的 nextCursor")
        }
    } while (cursor != null)
    return modelsById.values.toList()
}

private const val THREAD_PAGE_SIZE = 100
private const val MODEL_PAGE_SIZE = 100
private const val MCP_STATUS_PAGE_SIZE = 100
private const val PERMISSION_PROFILE_PAGE_SIZE = 100
internal const val THREAD_HISTORY_PAGE_SIZE = 5

internal fun selectOlderHistoryCursor(
    initialPageCursor: String?,
    turnsBackwardsCursor: String?,
): String? = initialPageCursor?.takeIf(String::isNotBlank)
    ?: turnsBackwardsCursor?.takeIf(String::isNotBlank)

private fun RpcException.isHistoryPaginationUnavailable(): Boolean {
    if (code == -32601 || code == -32602) return true
    return message.orEmpty().let { message ->
        message.contains("unknown field", ignoreCase = true) ||
            message.contains("invalid params", ignoreCase = true) ||
            message.contains("initialTurnsPage", ignoreCase = true) ||
            message.contains("excludeTurns", ignoreCase = true) ||
            message.contains("experimental", ignoreCase = true) &&
            message.contains("not supported", ignoreCase = true)
    }
}

private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false
private fun JsonObject.booleanOrDefault(key: String, default: Boolean): Boolean =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: default

private fun ThreadGoalStatus.wireValue(): String = when (this) {
    ThreadGoalStatus.ACTIVE -> "active"
    ThreadGoalStatus.PAUSED -> "paused"
    ThreadGoalStatus.BLOCKED -> "blocked"
    ThreadGoalStatus.USAGE_LIMITED -> "usageLimited"
    ThreadGoalStatus.BUDGET_LIMITED -> "budgetLimited"
    ThreadGoalStatus.COMPLETE -> "complete"
}

private fun String.toThreadGoalStatus(): ThreadGoalStatus? = when (lowercase().replace("_", "").replace("-", "")) {
    "active" -> ThreadGoalStatus.ACTIVE
    "paused" -> ThreadGoalStatus.PAUSED
    "blocked" -> ThreadGoalStatus.BLOCKED
    "usagelimited" -> ThreadGoalStatus.USAGE_LIMITED
    "budgetlimited" -> ThreadGoalStatus.BUDGET_LIMITED
    "complete" -> ThreadGoalStatus.COMPLETE
    else -> null
}
