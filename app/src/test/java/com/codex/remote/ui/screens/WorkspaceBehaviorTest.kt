package com.codex.remote.ui.screens

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
}
