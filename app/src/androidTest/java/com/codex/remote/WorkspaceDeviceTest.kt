package com.codex.remote

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codex.remote.data.rpc.CodexRpcClient
import com.codex.remote.data.store.ConnectionStore
import com.codex.remote.domain.AppUiState
import com.codex.remote.domain.AuthType
import com.codex.remote.domain.ComposerImageAttachment
import com.codex.remote.domain.ComposerMention
import com.codex.remote.domain.FileChangeSummary
import com.codex.remote.domain.PermissionMode
import com.codex.remote.domain.ReasoningEffortOption
import com.codex.remote.domain.RemoteAccount
import com.codex.remote.domain.RemoteCollaborationMode
import com.codex.remote.domain.RemoteModel
import com.codex.remote.domain.RemotePlugin
import com.codex.remote.domain.RemoteProject
import com.codex.remote.domain.RemoteServiceTier
import com.codex.remote.domain.RemoteSkill
import com.codex.remote.domain.RemoteThread
import com.codex.remote.domain.RemoteThreadTokenUsage
import com.codex.remote.domain.ReviewTargetKind
import com.codex.remote.domain.SavedConnection
import com.codex.remote.domain.ThreadGoal
import com.codex.remote.domain.ThreadGoalStatus
import com.codex.remote.domain.TimelineItem
import com.codex.remote.domain.TimelineKind
import com.codex.remote.ui.screens.WorkspaceScreen
import com.codex.remote.ui.theme.CodexRemoteTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class WorkspaceDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun composerMatchesDesktopLayoutAndPlanIsCommandDriven() {
        val state = mutableStateOf(baseState())
        val callbacks = WorkspaceCallbacks()
        show(state, callbacks)

        composeRule.onAllNodesWithText("Default", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Plan", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("5.6-sol · 极高 · Fast").assertExists()
        composeRule.onNodeWithTag(COMPOSER_CONTEXT).assertContentDescriptionEquals("上下文已使用 64%")

        val add = composeRule.onNodeWithTag(COMPOSER_ADD).fetchSemanticsNode().boundsInRoot
        val permissions = composeRule.onNodeWithTag(COMPOSER_PERMISSIONS).fetchSemanticsNode().boundsInRoot
        val context = composeRule.onNodeWithTag(COMPOSER_CONTEXT).fetchSemanticsNode().boundsInRoot
        val model = composeRule.onNodeWithTag(COMPOSER_MODEL).fetchSemanticsNode().boundsInRoot
        val send = composeRule.onNodeWithTag(COMPOSER_SEND).fetchSemanticsNode().boundsInRoot
        assertTrue(add.center.x < permissions.center.x)
        assertTrue(permissions.center.x < context.center.x)
        assertTrue(context.center.x < model.center.x)
        assertTrue(model.center.x < send.center.x)

        composeRule.onNodeWithTag(COMPOSER_PERMISSIONS).performClick()
        composeRule.onNodeWithText("替我审批").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(PermissionMode.AUTO_REVIEW, callbacks.permissionMode) }

        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("/plan")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()
        composeRule.runOnIdle { assertEquals("plan", callbacks.collaborationMode) }
        composeRule.onAllNodesWithText("Default", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Plan mode", substring = true).assertCountEquals(0)
    }

    @Test
    fun completedTurnCollapsesReasoningCommandsToolsAndFileChangesLive() {
        val runningTimeline = listOf(
            TimelineItem("reasoning", TimelineKind.REASONING, title = "Reasoning", body = "private reasoning", status = "inProgress"),
            TimelineItem("cmd-1", TimelineKind.COMMAND, title = "pwd", body = "/workspace/demo", status = "running"),
            TimelineItem("cmd-2", TimelineKind.COMMAND, title = "git status", body = "clean", status = "running"),
            TimelineItem("tool", TimelineKind.TOOL, title = "Remote tool", body = "tool details", status = "started"),
            TimelineItem(
                "files",
                TimelineKind.FILE_CHANGE,
                status = "inProgress",
                fileChanges = listOf(
                    FileChangeSummary(
                        "app/src/Main.kt",
                        "update",
                        "--- a/app/src/Main.kt\n+++ b/app/src/Main.kt\n-old\n+val ready = true",
                    ),
                ),
            ),
        )
        val state = mutableStateOf(baseState(timeline = runningTimeline, isTurnRunning = true))
        show(state)

        scrollTo("timeline-tool-body-reasoning")
        composeRule.onNodeWithText("private reasoning").assertIsDisplayed()
        scrollTo("timeline-tool-body-command-group:cmd-1")
        composeRule.onNodeWithText("运行了多个命令").assertExists()
        composeRule.onNodeWithText("/workspace/demo", substring = true).assertExists()
        scrollTo("timeline-tool-body-tool")
        composeRule.onNodeWithText("tool details").assertIsDisplayed()
        scrollTo("file-changes-files")
        composeRule.onNodeWithText("+val ready = true").assertExists()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                isTurnRunning = false,
                activeTurnId = null,
                timeline = runningTimeline.map { it.copy(status = "completed") },
            )
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("timeline-tool-body-reasoning").fetchSemanticsNodes().isEmpty()
        }

        scrollTo("timeline-tool-reasoning")
        composeRule.onNodeWithText("private reasoning").assertDoesNotExist()
        scrollTo("timeline-tool-command-group:cmd-1")
        composeRule.onNodeWithText("/workspace/demo", substring = true).assertDoesNotExist()
        scrollTo("timeline-tool-tool")
        composeRule.onNodeWithText("tool details").assertDoesNotExist()
        scrollTo("file-changes-files")
        composeRule.onNodeWithText("+val ready = true").assertDoesNotExist()
    }

    @Test
    fun markdownBoldCodeAndLatexRenderAsAndroidSpans() {
        val markdown = """## Rendered heading

**bold-rendered** and inline ${'$'}x^2${'$'}.

```kotlin
val answer = 42
```
""".trimIndent()
        show(mutableStateOf(baseState(timeline = listOf(TimelineItem("markdown", TimelineKind.AGENT, body = markdown)))))
        composeRule.onNodeWithTag("timeline-agent-markdown").assertExists()

        lateinit var rendered: Spanned
        composeRule.runOnIdle {
            val textView = composeRule.activity.window.decorView.descendantTextViews()
                .firstOrNull { it.text.toString().contains("bold-rendered") }
            assertNotNull(textView)
            rendered = textView!!.text as Spanned
        }
        val plainText = rendered.toString()
        val spans = rendered.getSpans(0, rendered.length, Any::class.java)
        assertFalse(plainText.contains("**"))
        assertFalse(plainText.contains("```"))
        assertTrue(plainText.contains("val answer = 42"))
        assertTrue(spans.any { span ->
            (span is StyleSpan && span.style and Typeface.BOLD != 0) ||
                span.javaClass.name.contains("StrongEmphasis", ignoreCase = true)
        })
        assertTrue(spans.any { span ->
            span.javaClass.name.contains("latex", ignoreCase = true) ||
                span.javaClass.name.contains("drawable", ignoreCase = true)
        })
    }

    @Test
    fun bottomButtonReachesSentinelAfterLongAgentResponse() {
        val longAnswer = (1..80).joinToString("\n\n") { "Rendered answer paragraph $it" }
        val timeline = buildList {
            repeat(20) { index -> add(TimelineItem("user-$index", TimelineKind.USER, body = "Question $index")) }
            add(TimelineItem("last-answer", TimelineKind.AGENT, body = longAnswer))
        }
        show(mutableStateOf(baseState(timeline = timeline)))

        composeRule.onNodeWithTag(CONVERSATION_BOTTOM).assertIsDisplayed()
        composeRule.onNodeWithTag(CONVERSATION_LIST).performTouchInput { swipeDown() }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(CONVERSATION_BOTTOM_BUTTON).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(CONVERSATION_BOTTOM_BUTTON).assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(CONVERSATION_BOTTOM_BUTTON).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(CONVERSATION_BOTTOM).assertIsDisplayed()
    }

    @Test
    fun loadingFiveOlderTurnsKeepsTheVisibleMessageAnchored() {
        val current = (0 until 10).flatMap { index ->
            listOf(
                TimelineItem("new-user-$index", TimelineKind.USER, body = "Current question $index"),
                TimelineItem("new-agent-$index", TimelineKind.AGENT, body = "Current answer $index"),
            )
        }
        val older = (0 until 5).flatMap { index ->
            listOf(
                TimelineItem("old-user-$index", TimelineKind.USER, body = "Older question $index"),
                TimelineItem("old-agent-$index", TimelineKind.AGENT, body = "Older answer $index"),
            )
        }
        val state = mutableStateOf(baseState(timeline = current).copy(hasOlderHistory = true, olderHistoryCursor = "page-2"))
        val callbacks = WorkspaceCallbacks()
        callbacks.onLoadOlder = {
            callbacks.olderLoads++
            state.value = state.value.copy(isOlderHistoryLoading = true)
        }
        show(state, callbacks)

        repeat(12) {
            if (callbacks.olderLoads > 0) return@repeat
            composeRule.onNodeWithTag(CONVERSATION_LIST).performTouchInput { swipeDown() }
            composeRule.waitForIdle()
        }
        assertEquals(1, callbacks.olderLoads)
        val before = composeRule.onNodeWithTag("timeline-item-new-agent-0").fetchSemanticsNode().boundsInRoot.top
        composeRule.runOnIdle {
            state.value = state.value.copy(
                timeline = older + current,
                isOlderHistoryLoading = false,
                hasOlderHistory = false,
                olderHistoryCursor = null,
            )
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("timeline-item-new-agent-0").fetchSemanticsNodes().isNotEmpty()
        }
        val after = composeRule.onNodeWithTag("timeline-item-new-agent-0").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "History anchor moved from $before to $after (${abs(after - before)} px)",
            abs(after - before) <= 4f,
        )
        composeRule.onNodeWithTag(CONVERSATION_LIST).performScrollToNode(hasTestTag("timeline-item-old-user-0"))
        composeRule.onNodeWithText("Older question 0").assertIsDisplayed()
    }

    @Test
    fun switchingThreadsClearsDraftAndNeverShowsTheOtherTimeline() {
        val threadA = thread("thread-a", "Thread A")
        val threadB = thread("thread-b", "Thread B")
        val state = mutableStateOf(
            baseState(
                timeline = listOf(TimelineItem("a-message", TimelineKind.USER, body = "thread-a-body")),
                threads = listOf(threadA, threadB),
            ),
        )
        show(state)
        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("draft-a")
        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("目标").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(COMPOSER_GOAL_MARKER).assertExists()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                selectedThreadId = "thread-b",
                timeline = (0 until 30).map { index ->
                    TimelineItem("b-message-$index", TimelineKind.USER, body = "thread-b-body-$index")
                },
            )
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(CONVERSATION_BOTTOM).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("thread-b-body-29").assertExists()
        composeRule.onNodeWithText("thread-a-body").assertDoesNotExist()
        composeRule.onAllNodesWithTag(CONVERSATION_BOTTOM_BUTTON).assertCountEquals(0)
        composeRule.onAllNodesWithTag(COMPOSER_GOAL_MARKER).assertCountEquals(0)
        assertFalse(composerText().contains("draft-a"))
        composeRule.runOnIdle {
            assertTrue(state.value.acceptsThreadEvent("thread-b"))
            assertFalse(state.value.acceptsThreadEvent("thread-a"))
            assertFalse(state.value.acceptsThreadEvent(null))
        }
    }

    @Test
    fun addMenuUsesDismissibleGoalMarkerAndStillProvidesPlanSkillsAndPlugins() {
        val state = mutableStateOf(
            baseState().copy(
                skills = listOf(RemoteSkill("qa-check", "QA Skill", "Device test skill", "/skills/qa/SKILL.md", true, setOf("/workspace/demo"))),
                plugins = listOf(RemotePlugin("qa@local", "qa-plugin", "QA Plugin", "Device test plugin", "local", "plugin://qa@local", true)),
            ),
        )
        val callbacks = WorkspaceCallbacks()
        show(state, callbacks)

        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("目标").assertIsDisplayed().performClick()
        composeRule.onAllNodesWithText("Unsupported method", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag(COMPOSER_GOAL_MARKER).assertIsDisplayed().performClick()
        composeRule.onAllNodesWithTag(COMPOSER_GOAL_MARKER).assertCountEquals(0)

        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("目标").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("ship-device-tests")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("ship-device-tests" to true), callbacks.sentMessages)
        }
        composeRule.onAllNodesWithTag(COMPOSER_GOAL_MARKER).assertCountEquals(0)

        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("目标").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("计划模式").assertIsDisplayed().performClick()
        assertEquals("plan", callbacks.collaborationMode)
        composeRule.onAllNodesWithTag(COMPOSER_GOAL_MARKER).assertCountEquals(0)

        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("QA Skill").performScrollTo().performClick()
        assertTrue(composerText().contains("${'$'}qa-check"))
        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("QA Plugin").performScrollTo().performClick()
        assertTrue(composerText().contains("${'$'}qa-plugin"))
    }

    @Test
    fun goalSlashCommandActivatesTheSameMarkerAndOnlyMarksTheNextSend() {
        val state = mutableStateOf(baseState())
        val callbacks = WorkspaceCallbacks()
        show(state, callbacks)

        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("/goal")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()
        composeRule.onNodeWithTag(COMPOSER_GOAL_MARKER).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(callbacks.sentMessages.isEmpty()) }

        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("finish-device-coverage")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()
        composeRule.onAllNodesWithTag(COMPOSER_GOAL_MARKER).assertCountEquals(0)
        assertEquals("", composerText())

        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("normal-follow-up")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()
        assertEquals("", composerText())
        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("/goal inline-objective")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    "finish-device-coverage" to true,
                    "normal-follow-up" to false,
                    "inline-objective" to true,
                ),
                callbacks.sentMessages,
            )
        }
    }

    @Test
    fun goalMarkerWorksBeforeTheRemoteThreadExists() {
        val state = mutableStateOf(baseState(threads = emptyList()).copy(selectedThreadId = null))
        val callbacks = WorkspaceCallbacks()
        show(state, callbacks)

        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("目标").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(COMPOSER_GOAL_MARKER).assertIsDisplayed()
        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("create-thread-with-goal")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("create-thread-with-goal" to true), callbacks.sentMessages)
        }
        composeRule.onAllNodesWithText("请先发送第一条消息", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag(COMPOSER_GOAL_MARKER).assertCountEquals(0)
    }

    @Test
    fun goalMessagesAreVisiblyDistinguishedFromNormalMessages() {
        val state = mutableStateOf(
            baseState(
                timeline = listOf(
                    TimelineItem("goal-message", TimelineKind.USER, body = "ship it", isGoal = true),
                    TimelineItem("normal-message", TimelineKind.USER, body = "status update"),
                ),
            ),
        )
        show(state)

        composeRule.onNodeWithTag("timeline-goal-goal-message").assertExists()
        composeRule.onNodeWithText("作为目标发送").assertExists()
        composeRule.onAllNodesWithTag("timeline-goal-normal-message").assertCountEquals(0)
    }

    @Test
    fun completedFileChangesUseStructuredExpandablePresentationAndNoChangesTab() {
        val files = TimelineItem(
            id = "files",
            kind = TimelineKind.FILE_CHANGE,
            status = "completed",
            fileChanges = listOf(
                FileChangeSummary("app/src/Main.kt", "update", "--- a/app/src/Main.kt\n+++ b/app/src/Main.kt\n-old\n+new\n+next"),
                FileChangeSummary("app/src/Status.kt", "add", "--- /dev/null\n+++ b/app/src/Status.kt\n+val ready = true"),
            ),
        )
        show(mutableStateOf(baseState(timeline = listOf(files))))

        composeRule.onNodeWithText("已编辑 2 个文件").assertIsDisplayed()
        composeRule.onNodeWithText("--- a/app/src/Main.kt").assertDoesNotExist()
        composeRule.onNodeWithText("已编辑 2 个文件").performClick()
        composeRule.onNodeWithText("app/src/Main.kt").assertExists()
        composeRule.onNodeWithText("app/src/Status.kt").assertExists()
        composeRule.onNodeWithText("Modified").assertExists()
        composeRule.onNodeWithText("Added").assertExists()
        composeRule.onNodeWithText("+2  -1").assertExists()
        composeRule.onAllNodesWithText("Changes").assertCountEquals(0)
        composeRule.onAllNodesWithText("{\"changes\"", substring = true).assertCountEquals(0)
    }

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag(CONVERSATION_LIST).performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun composerText(): String = composeRule.onNodeWithTag(COMPOSER_INPUT)
        .fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun show(
        state: MutableState<AppUiState>,
        callbacks: WorkspaceCallbacks = WorkspaceCallbacks(),
    ) {
        composeRule.setContent {
            CodexRemoteTheme(darkTheme = false) {
                WorkspaceScreen(
                    state = state.value,
                    onOpenConnections = {},
                    onNewThread = {},
                    onSelectProject = {},
                    onSelectThread = {},
                    onLoadOlderHistory = callbacks.onLoadOlder,
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onLoadArchivedThreads = {},
                    onUnarchiveThread = {},
                    onDeleteArchivedThread = {},
                    onSetThreadPinned = { _, _ -> },
                    onSend = callbacks.onSend,
                    onStop = {},
                    onCompactThread = {},
                    onForkThread = {},
                    onStartReview = { _, _ -> },
                    onRunInit = {},
                    onLoadMcpStatus = {},
                    onReloadMcpServers = {},
                    onStartMcpLogin = {},
                    onMcpAuthorizationHandled = {},
                    onSubmitFeedback = { _, _ -> },
                    onSetGoal = callbacks.onSetGoal,
                    onSetGoalStatus = {},
                    onClearGoal = {},
                    onShowStatus = {},
                    onSetModel = {},
                    onSetReasoningEffort = {},
                    onSetServiceTier = {},
                    onSetCollaborationMode = { callbacks.collaborationMode = it },
                    onSetPermissionProfile = {},
                    onSetPermissionMode = { callbacks.permissionMode = it },
                    onLoadRemoteDirectory = {},
                    onClearRemoteDirectory = {},
                    onStartLogin = {},
                    onCancelLogin = {},
                    onApproval = { _, _ -> },
                    onTrustHostKey = {},
                    onRejectHostKey = {},
                    onDismissNotice = {},
                )
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class RemoteStateDeviceTest {
    @Test
    fun goalAndPlanParamsMatchTheOfficialAppServerProtocol() {
        val goalParams = CodexRpcClient.threadGoalSetParams(
            threadId = "thread-a",
            objective = "ship",
            status = ThreadGoalStatus.ACTIVE,
        )
        assertEquals("thread-a", goalParams["threadId"]?.jsonPrimitive?.content)
        assertEquals("ship", goalParams["objective"]?.jsonPrimitive?.content)
        assertEquals("active", goalParams["status"]?.jsonPrimitive?.content)

        val turnParams = CodexRpcClient.turnStartParams(
            threadId = "thread-a",
            text = "plan request",
            cwd = "/workspace/demo",
            model = "gpt-5.6-sol",
            reasoningEffort = "ultra",
            serviceTier = "fast",
            approvalPolicy = "on-request",
            collaborationMode = RemoteCollaborationMode("Plan", "plan", "gpt-5.6-sol", "ultra"),
            mentions = emptyList(),
        )
        assertEquals("plan", turnParams["collaborationMode"]?.jsonObject?.get("mode")?.jsonPrimitive?.content)
        assertEquals("gpt-5.6-sol", turnParams["model"]?.jsonPrimitive?.content)
        assertEquals("ultra", turnParams["effort"]?.jsonPrimitive?.content)
        assertEquals("fast", turnParams["serviceTier"]?.jsonPrimitive?.content)
    }

    @Test
    fun lastUsedServerPersistsAndIsSelectedForAutomaticRestore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ConnectionStore(context)
        val suffix = UUID.randomUUID().toString()
        val first = store.save(
            com.codex.remote.domain.ConnectionDraft(
                name = "device-first-$suffix",
                host = "127.0.0.1",
                port = "22",
                username = "tester",
                password = "secret",
            ),
        )
        val second = store.save(
            com.codex.remote.domain.ConnectionDraft(
                name = "device-second-$suffix",
                host = "127.0.0.2",
                port = "22",
                username = "tester",
                password = "secret",
            ),
        )
        try {
            store.recordUsed(first.id)
            val persisted = withTimeout(5_000) {
                store.connections.first { connections ->
                    connections.any { it.id == first.id && it.lastUsedAt > 0 } &&
                        connections.any { it.id == second.id }
                }
            }
            assertEquals(first.id, persisted.lastUsedConnectionOrNull()?.id)
        } finally {
            store.delete(first.id)
            store.delete(second.id)
        }
    }
}

private class WorkspaceCallbacks {
    var collaborationMode: String? = null
    var permissionMode: PermissionMode? = null
    val sentMessages = mutableListOf<Pair<String, Boolean>>()
    var olderLoads: Int = 0
    var onLoadOlder: () -> Unit = {}
    var onSetGoal: (String) -> Unit = {}
    var onSend: (String, List<ComposerMention>, List<ComposerImageAttachment>, Boolean) -> Unit =
        { text, _, _, asGoal -> sentMessages += text.trim() to asGoal }
}

private fun baseState(
    timeline: List<TimelineItem> = emptyList(),
    isTurnRunning: Boolean = false,
    threads: List<RemoteThread> = listOf(thread("thread-a", "Thread A")),
): AppUiState {
    val connection = SavedConnection(
        id = "connection",
        name = "QA Host",
        host = "10.0.2.2",
        username = "tester",
        authType = AuthType.PASSWORD,
        encryptedPassword = "encrypted",
        lastUsedAt = 1,
    )
    val project = RemoteProject(
        id = "/workspace/demo",
        name = "demo",
        path = "/workspace/demo",
        threads = threads,
        updatedAt = 1,
    )
    val model = RemoteModel(
        id = "gpt-5.6-sol",
        displayName = "GPT-5.6-Sol",
        description = "Device test model",
        isDefault = true,
        supportedReasoningEfforts = listOf(ReasoningEffortOption("ultra", "Maximum")),
        defaultReasoningEffort = "ultra",
        inputModalities = setOf("text", "image"),
        serviceTiers = listOf(RemoteServiceTier("fast", "Fast", "Priority processing")),
        defaultServiceTier = "fast",
    )
    return AppUiState(
        savedConnections = listOf(connection),
        activeConnection = connection,
        connectionStatus = com.codex.remote.domain.ConnectionStatus.CONNECTED,
        threads = threads,
        projects = listOf(project),
        selectedProjectPath = project.path,
        selectedThreadId = threads.firstOrNull()?.id,
        timeline = timeline,
        models = listOf(model),
        selectedModel = model.id,
        selectedReasoningEffort = "ultra",
        selectedServiceTier = "fast",
        collaborationModes = listOf(
            RemoteCollaborationMode("Default", "default"),
            RemoteCollaborationMode("Plan", "plan", model.id, "ultra"),
        ),
        selectedCollaborationMode = "default",
        selectedPermissionProfile = ":workspace",
        approvalPolicy = "on-request",
        approvalsReviewer = "user",
        remoteAccount = RemoteAccount("chatgpt", "qa@example.invalid", "plus", false),
        threadTokenUsage = RemoteThreadTokenUsage(128_000, 120_000, 8_000, 200_000),
        isTurnRunning = isTurnRunning,
        activeTurnId = if (isTurnRunning) "turn-a" else null,
        isRestoringLastConnection = false,
    )
}

private fun thread(id: String, title: String) = RemoteThread(
    id = id,
    title = title,
    cwd = "/workspace/demo",
    updatedAt = 1,
    status = "idle",
)

private fun goal(objective: String) = ThreadGoal(
    threadId = "thread-a",
    objective = objective,
    status = ThreadGoalStatus.ACTIVE,
    tokenBudget = null,
    tokensUsed = 0,
    timeUsedSeconds = 0,
    createdAt = 1,
    updatedAt = 1,
)

private fun View.descendantTextViews(): List<TextView> = buildList {
    if (this@descendantTextViews is TextView) add(this@descendantTextViews)
    if (this@descendantTextViews is ViewGroup) {
        repeat(childCount) { childIndex -> addAll(getChildAt(childIndex).descendantTextViews()) }
    }
}

private const val CONVERSATION_LIST = "conversation-list"
private const val CONVERSATION_BOTTOM = "conversation-bottom"
private const val CONVERSATION_BOTTOM_BUTTON = "conversation-bottom-button"
private const val COMPOSER_INPUT = "composer-input"
private const val COMPOSER_ADD = "composer-add"
private const val COMPOSER_PERMISSIONS = "composer-permissions"
private const val COMPOSER_CONTEXT = "composer-context"
private const val COMPOSER_MODEL = "composer-model"
private const val COMPOSER_SEND = "composer-send"
private const val COMPOSER_GOAL_MARKER = "composer-goal-marker"
