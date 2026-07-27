package com.codex.remote

import com.codex.remote.domain.AppUiState
import com.codex.remote.domain.ComposerMentionKind
import com.codex.remote.domain.RemotePlugin
import com.codex.remote.domain.RemoteProject
import com.codex.remote.domain.RemoteSkill
import com.codex.remote.domain.RemoteThread
import com.codex.remote.domain.TimelineItem
import com.codex.remote.domain.TimelineKind
import com.codex.remote.domain.ThreadGoal
import com.codex.remote.domain.ThreadGoalStatus
import com.codex.remote.domain.withThreadArchived
import com.codex.remote.domain.withThreadRenamed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerStateTest {
    @Test
    fun manuallyTypedKnownTokensResolveToStructuredRemoteInputs() {
        val state = AppUiState(
            skills = listOf(RemoteSkill("deploy", "Deploy", "", "/skills/deploy/SKILL.md", true, setOf("/srv/app"))),
            plugins = listOf(RemotePlugin("linear@market", "linear", "Linear", "", "market", "plugin://linear@market", true)),
        )

        val mentions = resolveComposerMentions("\$deploy use \$linear", "/srv/app", emptyList(), state)

        assertEquals(listOf(ComposerMentionKind.SKILL, ComposerMentionKind.PLUGIN), mentions.map { it.kind })
        assertEquals(listOf("/skills/deploy/SKILL.md", "plugin://linear@market"), mentions.map { it.path })
    }

    @Test
    fun renameAndArchiveRebuildProjectStateWithoutLosingOtherThreads() {
        val first = thread("one", "One", 20)
        val second = thread("two", "Two", 10)
        val project = RemoteProject("/srv/app", "app", "/srv/app", listOf(first, second), 20)
        val state = AppUiState(
            threads = listOf(first, second),
            projects = listOf(project),
            selectedProjectPath = "/srv/app",
            selectedThreadId = "one",
            threadGoal = ThreadGoal(
                threadId = "one",
                objective = "Release",
                status = ThreadGoalStatus.ACTIVE,
                tokenBudget = null,
                tokensUsed = 0,
                timeUsedSeconds = 0,
                createdAt = 1,
                updatedAt = 1,
            ),
            timeline = listOf(TimelineItem("message", TimelineKind.AGENT, body = "done")),
        )

        val renamed = state.withThreadRenamed("one", "Release")
        assertEquals("Release", renamed.projects.single().threads.first().title)

        val archived = renamed.withThreadArchived("one")
        assertEquals(listOf("two"), archived.threads.map { it.id })
        assertNull(archived.selectedThreadId)
        assertNull(archived.threadGoal)
        assertEquals(emptyList<TimelineItem>(), archived.timeline)
    }

    @Test
    fun remoteChildPathUsesTheRemoteHostsSeparator() {
        assertEquals("/srv/app/AGENTS.md", remoteChildPath("/srv/app/", "AGENTS.md"))
        assertEquals("C:\\work\\repo\\AGENTS.md", remoteChildPath("C:\\work\\repo\\", "AGENTS.md"))
    }

    @Test
    fun streamedEventsOnlyApplyToTheSelectedThread() {
        val state = AppUiState(selectedThreadId = "thread-a")

        assertTrue(state.acceptsThreadEvent("thread-a"))
        assertFalse(state.acceptsThreadEvent("thread-b"))
        assertTrue(state.acceptsThreadEvent(null))
    }

    private fun thread(id: String, title: String, updatedAt: Long) = RemoteThread(
        id = id,
        title = title,
        cwd = "/srv/app",
        updatedAt = updatedAt,
        status = "idle",
    )
}
