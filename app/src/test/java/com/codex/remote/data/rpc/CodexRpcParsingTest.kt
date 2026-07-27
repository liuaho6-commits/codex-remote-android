package com.codex.remote.data.rpc

import com.codex.remote.domain.TimelineKind
import com.codex.remote.domain.ThreadGoalStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexRpcParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesAgentMessage() {
        val item = parse(
            """{"type":"agentMessage","id":"agent-1","text":"Done.","phase":"final_answer"}""",
        )

        assertEquals("agent-1", item?.id)
        assertEquals(TimelineKind.AGENT, item?.kind)
        assertEquals("Done.", item?.body)
    }

    @Test
    fun parsesCommandWithAggregatedOutput() {
        val item = parse(
            """{"type":"commandExecution","id":"cmd-1","command":"git status --short","cwd":"/repo","status":"completed","aggregatedOutput":" M app.kt\n","exitCode":0}""",
        )

        assertEquals(TimelineKind.COMMAND, item?.kind)
        assertEquals("git status --short", item?.title)
        assertEquals(" M app.kt\n", item?.body)
        assertEquals("completed", item?.status)
    }

    @Test
    fun parsesStructuredFileChanges() {
        val item = parse(
            """{"type":"fileChange","id":"patch-1","status":"completed","changes":[{"path":"a.kt","kind":{"type":"update","move_path":null},"diff":"-a\n+b"},{"path":"b.kt","kind":{"type":"add"},"diff":"+new"}]}""",
        )

        assertEquals(TimelineKind.FILE_CHANGE, item?.kind)
        assertEquals("a.kt, b.kt", item?.title)
        assertEquals(listOf("a.kt", "b.kt"), item?.fileChanges?.map { it.path })
        assertEquals(listOf("update", "add"), item?.fileChanges?.map { it.kind })
        assertEquals(listOf("-a\n+b", "+new"), item?.fileChanges?.map { it.diff })
    }

    @Test
    fun ignoresUnknownForwardCompatibleItem() {
        assertNull(parse("""{"type":"futureItem","id":"next"}"""))
    }

    @Test
    fun parsesOfficialObjectThreadStatus() {
        val thread = CodexRpcClient.parseThread(
            json.parseToJsonElement(
                """{"id":"thread-1","preview":"Remote task","cwd":"/srv/app","updatedAt":42,"status":{"type":"idle"}}""",
            ),
        )

        assertEquals("idle", thread?.status)
        assertEquals("/srv/app", thread?.cwd)
    }

    @Test
    fun parsesRemoteModelAndPreservesReasoningOrder() {
        val model = CodexRpcClient.parseModel(
            json.parseToJsonElement(
                """{"id":"catalog-sol","model":"gpt-5.6-sol","displayName":"GPT-5.6-Sol","description":"Frontier model","hidden":false,"isDefault":true,"defaultReasoningEffort":"low","supportedReasoningEfforts":[{"reasoningEffort":"low","description":"Fast"},{"reasoningEffort":"ultra","description":"Maximum"}],"inputModalities":["text","image"],"serviceTiers":[{"id":"fast","name":"Fast","description":"Priority processing"}],"defaultServiceTier":"fast"}""",
            ),
        )

        assertEquals("gpt-5.6-sol", model?.id)
        assertEquals("GPT-5.6-Sol", model?.displayName)
        assertEquals("low", model?.defaultReasoningEffort)
        assertEquals(listOf("low", "ultra"), model?.supportedReasoningEfforts?.map { it.value })
        assertEquals(setOf("text", "image"), model?.inputModalities)
        assertEquals(listOf("fast"), model?.serviceTiers?.map { it.id })
        assertEquals("fast", model?.defaultServiceTier)
        assertTrue(model?.isDefault == true)
    }

    @Test
    fun parsesCollaborationModesAndPermissionProfiles() {
        val mode = CodexRpcClient.parseCollaborationMode(
            json.parseToJsonElement(
                """{"name":"Plan","mode":"plan","model":null,"reasoning_effort":"medium"}""",
            ),
        )
        val profile = CodexRpcClient.parsePermissionProfile(
            json.parseToJsonElement(
                """{"id":"workspace-write","description":"Write inside the workspace","allowed":true}""",
            ),
        )

        assertEquals("plan", mode?.mode)
        assertEquals("medium", mode?.reasoningEffort)
        assertEquals("workspace-write", profile?.id)
        assertTrue(profile?.allowed == true)
    }

    @Test
    fun preservesContextCompactionInImportedHistory() {
        val item = parse("""{"type":"contextCompaction","id":"compact-1"}""")

        assertEquals(TimelineKind.COMPACTION, item?.kind)
        assertEquals("Context compacted", item?.title)
    }

    @Test
    fun parsesRemoteAccountRequirementWithoutPretendingToBeReady() {
        val result = json.parseToJsonElement(
            """{"account":null,"requiresOpenaiAuth":true}""",
        ).jsonObject

        val account = CodexRpcClient.parseAccount(result)

        assertNull(account.type)
        assertTrue(account.requiresOpenaiAuth)
        assertTrue(!account.canRunCodex)
    }

    @Test
    fun parsesRemoteSkillInterfaceAndCwd() {
        val skill = CodexRpcClient.parseSkill(
            json.parseToJsonElement(
                """{"name":"deploy","description":"Deploy safely","path":"/home/me/.codex/skills/deploy/SKILL.md","enabled":true,"interface":{"displayName":"Deploy","shortDescription":"Ship this project"}}""",
            ),
            cwd = "/srv/app",
        )

        assertEquals("deploy", skill?.name)
        assertEquals("Deploy", skill?.displayName)
        assertEquals("Ship this project", skill?.description)
        assertEquals(setOf("/srv/app"), skill?.cwds)
    }

    @Test
    fun parsesInstalledPluginIntoExactMentionPath() {
        val plugin = CodexRpcClient.parsePlugin(
            json.parseToJsonElement(
                """{"id":"linear@openai-curated","name":"linear","installed":true,"enabled":true,"availability":"AVAILABLE","interface":{"displayName":"Linear","shortDescription":"Work with issues"}}""",
            ),
            marketplace = "openai-curated",
        )

        assertEquals("Linear", plugin?.displayName)
        assertEquals("plugin://linear@openai-curated", plugin?.mentionPath)
        assertTrue(plugin?.enabled == true)
    }

    @Test
    fun parsesEveryOfficialThreadGoalStatus() {
        val statuses = mapOf(
            "active" to ThreadGoalStatus.ACTIVE,
            "paused" to ThreadGoalStatus.PAUSED,
            "blocked" to ThreadGoalStatus.BLOCKED,
            "usageLimited" to ThreadGoalStatus.USAGE_LIMITED,
            "budgetLimited" to ThreadGoalStatus.BUDGET_LIMITED,
            "complete" to ThreadGoalStatus.COMPLETE,
        )

        statuses.forEach { (wireStatus, expected) ->
            val goal = CodexRpcClient.parseThreadGoal(
                json.parseToJsonElement(
                    """{"threadId":"thread-1","objective":"Ship it","status":"$wireStatus","tokenBudget":12000,"tokensUsed":800,"timeUsedSeconds":14,"createdAt":10,"updatedAt":20}""",
                ),
            )

            assertEquals(expected, goal?.status)
            assertEquals("Ship it", goal?.objective)
            assertEquals(12_000L, goal?.tokenBudget)
            assertEquals(800L, goal?.tokensUsed)
        }
    }

    @Test
    fun buildsOfficialThreadGoalRpcParamsWithoutInventingNullFields() {
        val get = CodexRpcClient.threadGoalGetParams("thread-1")
        val set = CodexRpcClient.threadGoalSetParams(
            threadId = "thread-1",
            objective = "Finish release",
            status = ThreadGoalStatus.ACTIVE,
        )
        val clear = CodexRpcClient.threadGoalClearParams("thread-1")

        assertEquals("thread-1", get["threadId"]?.jsonPrimitive?.content)
        assertEquals("thread-1", set["threadId"]?.jsonPrimitive?.content)
        assertEquals("Finish release", set["objective"]?.jsonPrimitive?.content)
        assertEquals("active", set["status"]?.jsonPrimitive?.content)
        assertTrue(!set.containsKey("tokenBudget"))
        assertEquals("thread-1", clear["threadId"]?.jsonPrimitive?.content)
    }

    private fun parse(source: String) = CodexRpcClient.parseTimelineItem(json.parseToJsonElement(source))
}
