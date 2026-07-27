package com.codex.remote.data.rpc

import com.codex.remote.domain.TimelineItem
import com.codex.remote.domain.TimelineKind
import com.codex.remote.domain.mergeTimelineHistory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadHistoryPaginationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun initialResumeRequestsOnlyTheDesktopSizedRecentPage() {
        val params = CodexRpcClient.threadResumeParams(
            threadId = "thread-1",
            cwd = "/srv/app",
            paginated = true,
        )

        assertTrue(params.getValue("excludeTurns").jsonPrimitive.content.toBoolean())
        val page = params.getValue("initialTurnsPage").jsonObject
        assertEquals(THREAD_HISTORY_PAGE_SIZE, page.getValue("limit").jsonPrimitive.content.toInt())
        assertEquals(5, THREAD_HISTORY_PAGE_SIZE)
        assertEquals("desc", page.getValue("sortDirection").jsonPrimitive.content)
        assertEquals("full", page.getValue("itemsView").jsonPrimitive.content)
    }

    @Test
    fun legacyResumeDoesNotSendExperimentalPaginationFields() {
        val params = CodexRpcClient.threadResumeParams(
            threadId = "thread-1",
            cwd = "",
            paginated = false,
        )

        assertEquals(setOf("threadId"), params.keys)
        assertFalse(params.containsKey("excludeTurns"))
        assertFalse(params.containsKey("initialTurnsPage"))
    }

    @Test
    fun olderPageUsesFullDescendingTurnHistory() {
        val params = CodexRpcClient.threadTurnsListParams("thread-1", "older-2")

        assertEquals("thread-1", params.getValue("threadId").jsonPrimitive.content)
        assertEquals("older-2", params.getValue("cursor").jsonPrimitive.content)
        assertEquals(5, params.getValue("limit").jsonPrimitive.content.toInt())
        assertEquals("desc", params.getValue("sortDirection").jsonPrimitive.content)
        assertEquals("full", params.getValue("itemsView").jsonPrimitive.content)
    }

    @Test
    fun descendingWireTurnsBecomeChronologicalTimelineItems() {
        val turns = json.parseToJsonElement(
            """[
                {"id":"turn-new","items":[{"type":"agentMessage","id":"new","text":"new"}]},
                {"id":"turn-old","items":[{"type":"agentMessage","id":"old","text":"old"}]}
            ]""",
        ).jsonArray

        val timeline = CodexRpcClient.parseTurnsTimeline(turns)

        assertEquals(listOf("old", "new"), timeline.map { it.id })
    }

    @Test
    fun prependingOverlappingHistoryKeepsLiveItemsAndTheirOrder() {
        val persistedOverlap = item("overlap", "persisted")
        val liveOverlap = item("overlap", "live delta")
        val liveTail = item("live-tail", "still streaming")

        val merged = mergeTimelineHistory(
            older = listOf(item("older", "older"), persistedOverlap),
            newer = listOf(liveOverlap, liveTail),
        )

        assertEquals(listOf("older", "overlap", "live-tail"), merged.map { it.id })
        assertEquals("live delta", merged[1].body)
        assertEquals("live-tail", merged.last().id)
    }

    @Test
    fun prependingHistoryMovesTheAnchorByExactlyTheInsertedItems() {
        val current = listOf(item("anchor", "visible"), item("newest", "newest"))
        val merged = mergeTimelineHistory(
            older = listOf(item("old-1", "one"), item("old-2", "two")),
            newer = current,
        )

        assertEquals(2, merged.indexOfFirst { it.id == "anchor" })
        assertEquals("anchor", merged[2].id)
    }

    @Test
    fun repeatedOrBlankHistoryCursorCannotContinuePagination() {
        assertNull(CodexRpcClient.checkedNextHistoryCursor("", emptySet()))
        assertThrows(RpcException::class.java) {
            CodexRpcClient.checkedNextHistoryCursor("older-2", setOf("older-1", "older-2"))
        }
    }

    @Test
    fun resumeFallsBackToTheCurrentBackwardsCursorField() {
        assertEquals("page-cursor", selectOlderHistoryCursor("page-cursor", "resume-cursor"))
        assertEquals("resume-cursor", selectOlderHistoryCursor("", "resume-cursor"))
        assertNull(selectOlderHistoryCursor(null, ""))
    }

    private fun item(id: String, body: String) = TimelineItem(
        id = id,
        kind = TimelineKind.AGENT,
        body = body,
    )
}
