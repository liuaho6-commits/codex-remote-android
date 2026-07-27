package com.codex.remote.domain

import com.codex.remote.ui.screens.desktopSlashCommands
import com.codex.remote.ui.screens.modelSettingsSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerBehaviorTest {
    @Test
    fun slashCommandsOnlyTriggerAtTheStartOfTheCurrentLine() {
        val trigger = findComposerTrigger("context\n/mod", cursor = 12)

        assertEquals(ComposerTriggerKind.SLASH_COMMAND, trigger?.kind)
        assertEquals("mod", trigger?.query)
        assertNull(findComposerTrigger("please use /model", cursor = 17))
    }

    @Test
    fun mentionTriggerTracksTheTokenAtTheCursor() {
        val trigger = findComposerTrigger("Ask \$dep", cursor = 8)

        assertEquals(ComposerTriggerKind.MENTION, trigger?.kind)
        assertEquals("dep", trigger?.query)
        val update = replaceComposerTrigger("Ask \$dep", requireNotNull(trigger), "\$deploy ")
        assertEquals("Ask \$deploy ", update.text)
        assertEquals(update.text.length, update.cursor)
    }

    @Test
    fun tokenMatchingDoesNotResolveLongerNamesByPrefix() {
        assertTrue("\$deploy now".containsComposerToken("\$deploy"))
        assertTrue(!"\$deployment now".containsComposerToken("\$deploy"))
    }

    @Test
    fun goalCommandIsAvailableBeforeAndAfterThreadCreation() {
        assertTrue(desktopSlashCommands(hasThread = false).any { it.name == "goal" })
        assertTrue(desktopSlashCommands(hasThread = true).any { it.name == "goal" })
    }

    @Test
    fun desktopRemoteCommandsIncludePlanCompactAndReviewMode() {
        val beforeThread = desktopSlashCommands(hasThread = false).map { it.name }
        val withThread = desktopSlashCommands(hasThread = true).map { it.name }

        assertTrue("plan-mode" in beforeThread)
        assertTrue("compact" in beforeThread)
        assertTrue("review-mode" in withThread)
    }

    @Test
    fun combinedModelSummaryOnlyShowsFastWhenEnabled() {
        assertEquals("5.6 Sol · 极高", modelSettingsSummary("5.6 Sol", "ultra", null))
        assertEquals("5.6 Sol · 极高 · Fast", modelSettingsSummary("5.6 Sol", "ultra", "Fast"))
    }
}
