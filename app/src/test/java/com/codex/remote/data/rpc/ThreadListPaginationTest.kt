package com.codex.remote.data.rpc

import com.codex.remote.domain.RemoteThread
import com.codex.remote.domain.RemoteModel
import com.codex.remote.domain.ComposerMention
import com.codex.remote.domain.ComposerMentionKind
import com.codex.remote.domain.ComposerImageAttachment
import com.codex.remote.domain.RemoteCollaborationMode
import com.codex.remote.domain.ReviewTargetKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadListPaginationTest {
    @Test
    fun listParamsImportAllTopLevelSessionsWithoutCwdFilter() {
        val params = CodexRpcClient.threadListParams(cursor = null)

        assertFalse(params.containsKey("cwd"))
        assertFalse(params.containsKey("cursor"))
        assertTrue(params.getValue("sourceKinds").jsonArray.isEmpty())
    }

    @Test
    fun archivedListUsesTheSameHostWidePaginationWithArchivedFilter() {
        val params = CodexRpcClient.threadListParams(cursor = "next-page", archived = true)

        assertEquals("next-page", params.getValue("cursor").jsonPrimitive.content)
        assertTrue(params.getValue("archived").jsonPrimitive.content.toBoolean())
        assertFalse(params.containsKey("cwd"))
        assertTrue(params.getValue("sourceKinds").jsonArray.isEmpty())
    }

    @Test
    fun threadStartUsesOnlyFieldsFromTheOfficialSchema() {
        val params = CodexRpcClient.threadStartParams(
            cwd = "/workspace/repo",
            model = "gpt-5.6-sol",
            approvalPolicy = "on-request",
        )

        assertEquals("/workspace/repo", params.getValue("cwd").jsonPrimitive.content)
        assertEquals("gpt-5.6-sol", params.getValue("model").jsonPrimitive.content)
        assertEquals("on-request", params.getValue("approvalPolicy").jsonPrimitive.content)
        assertEquals("workspace-write", params.getValue("sandbox").jsonPrimitive.content)
        assertFalse(params.containsKey("effort"))
        assertTrue(params.keys.all { it in setOf("cwd", "model", "approvalPolicy", "sandbox") })
    }

    @Test
    fun threadMutationsUseOfficialParameterNames() {
        val rename = CodexRpcClient.threadSetNameParams("thread-1", "Release cleanup")
        val archive = CodexRpcClient.threadArchiveParams("thread-1")
        val restoreOrDelete = CodexRpcClient.threadMutationParams("thread-1")

        assertEquals(setOf("threadId", "name"), rename.keys)
        assertEquals("Release cleanup", rename.getValue("name").jsonPrimitive.content)
        assertEquals(setOf("threadId"), archive.keys)
        assertEquals(setOf("threadId"), restoreOrDelete.keys)
    }

    @Test
    fun forkAndReviewUseOfficialRpcShapes() {
        val fork = CodexRpcClient.threadForkParams(
            threadId = "thread-1",
            cwd = "/srv/app",
            model = "gpt-5.6-sol",
            serviceTier = "fast",
            approvalPolicy = "on-request",
            permissionProfile = "workspace-write",
        )
        val review = CodexRpcClient.reviewStartParams(
            threadId = "thread-1",
            targetKind = ReviewTargetKind.BASE_BRANCH,
            targetValue = "main",
        )

        assertEquals("fast", fork.getValue("serviceTier").jsonPrimitive.content)
        assertEquals("workspace-write", fork.getValue("permissions").jsonPrimitive.content)
        assertFalse(fork.containsKey("sandbox"))
        assertEquals("inline", review.getValue("delivery").jsonPrimitive.content)
        assertEquals("baseBranch", review.getValue("target").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("main", review.getValue("target").jsonObject.getValue("branch").jsonPrimitive.content)
    }

    @Test
    fun composerCatalogParamsStayScopedToRemoteProjects() {
        val skills = CodexRpcClient.skillsListParams(listOf("/srv/a", "/srv/a", "/srv/b"), forceReload = true)
        val plugins = CodexRpcClient.pluginInstalledParams(listOf("/srv/a", "/srv/b"))

        assertEquals(listOf("/srv/a", "/srv/b"), skills.getValue("cwds").jsonArray.map { it.jsonPrimitive.content })
        assertTrue(skills.getValue("forceReload").jsonPrimitive.content.toBoolean())
        assertEquals(listOf("/srv/a", "/srv/b"), plugins.getValue("cwds").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun feedbackUsesTheRemoteThreadAndIncludesRemoteLogs() {
        val params = CodexRpcClient.feedbackUploadParams(
            classification = "bug",
            reason = "The result was incomplete",
            threadId = "thread-1",
        )

        assertEquals("bug", params.getValue("classification").jsonPrimitive.content)
        assertEquals("The result was incomplete", params.getValue("reason").jsonPrimitive.content)
        assertEquals("thread-1", params.getValue("threadId").jsonPrimitive.content)
        assertTrue(params.getValue("includeLogs").jsonPrimitive.content.toBoolean())
        assertEquals(
            "codex_remote_android",
            params.getValue("tags").jsonObject.getValue("client").jsonPrimitive.content,
        )
    }

    @Test
    fun turnStartSendsStructuredSkillAndPluginInputs() {
        val params = CodexRpcClient.turnStartParams(
            threadId = "thread-1",
            text = "\$deploy ask \$linear",
            cwd = "/srv/app",
            model = "gpt-5.6-sol",
            reasoningEffort = "high",
            approvalPolicy = "on-request",
            mentions = listOf(
                ComposerMention(ComposerMentionKind.SKILL, "deploy", "/skills/deploy/SKILL.md", "\$deploy"),
                ComposerMention(ComposerMentionKind.PLUGIN, "Linear", "plugin://linear@openai-curated", "\$linear"),
            ),
        )

        val inputs = params.getValue("input").jsonArray.map { it.jsonObject }
        assertEquals(listOf("text", "skill", "mention"), inputs.map { it.getValue("type").jsonPrimitive.content })
        assertEquals("/skills/deploy/SKILL.md", inputs[1].getValue("path").jsonPrimitive.content)
        assertEquals("plugin://linear@openai-curated", inputs[2].getValue("path").jsonPrimitive.content)
        assertEquals("high", params.getValue("effort").jsonPrimitive.content)
    }

    @Test
    fun turnStartCarriesPlanModeServiceTierPermissionAndImage() {
        val params = CodexRpcClient.turnStartParams(
            threadId = "thread-1",
            text = "Inspect this image",
            cwd = "/srv/app",
            model = "gpt-5.6-sol",
            reasoningEffort = "high",
            serviceTier = "fast",
            approvalPolicy = "on-request",
            permissionProfile = "workspace-write",
            collaborationMode = RemoteCollaborationMode("Plan", "plan", reasoningEffort = "medium"),
            mentions = emptyList(),
            attachments = listOf(
                ComposerImageAttachment(
                    id = "image-1",
                    displayName = "screen.png",
                    mimeType = "image/png",
                    dataUrl = "data:image/png;base64,AAAA",
                ),
            ),
        )

        assertEquals("fast", params.getValue("serviceTier").jsonPrimitive.content)
        assertEquals("workspace-write", params.getValue("permissions").jsonPrimitive.content)
        val collaboration = params.getValue("collaborationMode").jsonObject
        assertEquals("plan", collaboration.getValue("mode").jsonPrimitive.content)
        assertEquals(
            "medium",
            collaboration.getValue("settings").jsonObject.getValue("reasoning_effort").jsonPrimitive.content,
        )
        val inputs = params.getValue("input").jsonArray.map { it.jsonObject }
        assertEquals(listOf("text", "image"), inputs.map { it.getValue("type").jsonPrimitive.content })
        assertEquals("data:image/png;base64,AAAA", inputs[1].getValue("url").jsonPrimitive.content)
    }

    @Test
    fun steerUsesTheActiveTurnPreconditionAndStructuredInput() {
        val params = CodexRpcClient.turnSteerParams(
            threadId = "thread-1",
            expectedTurnId = "turn-9",
            text = "Also run the focused tests",
            mentions = emptyList(),
            attachments = emptyList(),
        )

        assertEquals("thread-1", params.getValue("threadId").jsonPrimitive.content)
        assertEquals("turn-9", params.getValue("expectedTurnId").jsonPrimitive.content)
        assertEquals("text", params.getValue("input").jsonArray.single().jsonObject.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun followsEveryCursorAndDeduplicatesOverlappingPages() = runBlocking {
        val requestedCursors = mutableListOf<String?>()

        val threads = collectAllThreadPages { cursor ->
            requestedCursors += cursor
            when (cursor) {
                null -> ThreadPage(listOf(thread("one", 30), thread("overlap", 20)), "page-2")
                "page-2" -> ThreadPage(listOf(thread("overlap", 40), thread("three", 10)), null)
                else -> error("Unexpected cursor: $cursor")
            }
        }

        assertEquals(listOf(null, "page-2"), requestedCursors)
        assertEquals(listOf("overlap", "one", "three"), threads.map { it.id })
        assertEquals(40, threads.first().updatedAt)
    }

    @Test
    fun modelPaginationPreservesRemoteCatalogOrder() = runBlocking {
        val requestedCursors = mutableListOf<String?>()

        val models = collectAllModelPages { cursor ->
            requestedCursors += cursor
            when (cursor) {
                null -> ModelPage(listOf(model("gpt-a"), model("gpt-b")), "models-2")
                "models-2" -> ModelPage(listOf(model("gpt-c")), null)
                else -> error("Unexpected cursor: $cursor")
            }
        }

        assertEquals(listOf(null, "models-2"), requestedCursors)
        assertEquals(listOf("gpt-a", "gpt-b", "gpt-c"), models.map { it.id })
    }

    private fun thread(id: String, updatedAt: Long) = RemoteThread(
        id = id,
        title = id,
        cwd = "/workspace/$id",
        updatedAt = updatedAt,
        status = "idle",
    )

    private fun model(id: String) = RemoteModel(
        id = id,
        displayName = id,
        description = "",
        isDefault = false,
    )
}
