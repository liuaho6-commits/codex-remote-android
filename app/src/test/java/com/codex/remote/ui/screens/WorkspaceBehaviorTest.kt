package com.codex.remote.ui.screens

import com.codex.remote.domain.PermissionMode
import com.codex.remote.domain.TimelineItem
import com.codex.remote.domain.TimelineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceBehaviorTest {
    @Test
    fun remotePathNavigationSupportsPosixAndWindowsHosts() {
        assertEquals("/srv/app/src", appendRemotePath("/srv/app/", "src"))
        assertEquals("C:\\work\\app\\src", appendRemotePath("C:\\work\\app\\", "src"))
        assertEquals("/srv/app", remoteParentPath("/srv/app/src"))
        assertEquals("C:\\work\\app", remoteParentPath("C:\\work\\app\\src"))
        assertNull(remoteParentPath("/"))
        assertNull(remoteParentPath("C:\\"))
    }

    @Test
    fun selectedRemotePathsBecomeReadableComposerReferences() {
        assertEquals("@src/Main.kt", remotePathComposerToken("/srv/app", "/srv/app/src/Main.kt"))
        assertEquals("@\"docs/release notes.md\"", remotePathComposerToken("C:\\work\\app", "C:\\work\\app\\docs\\release notes.md"))
    }

    @Test
    fun reasoningOnlyAutoExpandsWhileItIsRunning() {
        assertTrue("inProgress".isTimelineItemRunning())
        assertTrue("running".isTimelineItemRunning())
        assertFalse("completed".isTimelineItemRunning())
        assertFalse("failed".isTimelineItemRunning())
    }

    @Test
    fun markdownLatexNormalizationLeavesCodeUntouched() {
        val markdown = """Inline ${'$'}x^2 + y^2${'$'}.
`val raw = "${'$'}notMath${'$'}"`
```kotlin
val block = "${'$'}stillRaw${'$'}"
```
""".trimIndent()

        assertEquals(
            """Inline ${'$'}${'$'}x^2 + y^2${'$'}${'$'}.
`val raw = "${'$'}notMath${'$'}"`
```kotlin
val block = "${'$'}stillRaw${'$'}"
```
""".trimIndent(),
            normalizeLatexMarkdown(markdown),
        )
    }

    @Test
    fun permissionModesDistinguishAutoReviewAndRemoteProfiles() {
        assertEquals(PermissionMode.ASK, permissionModeFor(":workspace", "on-request", "user"))
        assertEquals(PermissionMode.AUTO_REVIEW, permissionModeFor(":workspace", "on-request", "auto_review"))
        assertEquals(PermissionMode.FULL_ACCESS, permissionModeFor(":danger-full-access", "never", "user"))
        assertEquals(PermissionMode.READ_ONLY, permissionModeFor(":read-only", "on-request", "user"))
        assertNull(permissionModeFor("team-policy", "on-request", "user"))
    }

    @Test
    fun structuredDiffCountsIgnoreFileHeaders() {
        assertEquals(2 to 1, diffLineCounts("--- a/app.kt\n+++ b/app.kt\n-old\n+new\n+next"))
    }

    @Test
    fun adjacentCommandsArePresentedAsOneStableExpandableStep() {
        val timeline = listOf(
            TimelineItem("reasoning", TimelineKind.REASONING, body = "thinking"),
            TimelineItem("cmd-1", TimelineKind.COMMAND, title = "pwd", body = "/srv/app", status = "completed"),
            TimelineItem("cmd-2", TimelineKind.COMMAND, title = "git status", body = "clean", status = "completed"),
            TimelineItem("answer", TimelineKind.AGENT, body = "Done"),
        )

        val presentation = groupConsecutiveCommands(timeline)

        assertEquals(3, presentation.size)
        assertEquals("运行了多个命令", presentation[1].item.title)
        assertEquals("command-group:cmd-1", presentation[1].item.id)
        assertEquals(listOf("cmd-1", "cmd-2"), presentation[1].sourceItemIds)
        assertTrue(presentation[1].item.body.contains("1. pwd"))
        assertTrue(presentation[1].item.body.contains("2. git status"))
    }
}
