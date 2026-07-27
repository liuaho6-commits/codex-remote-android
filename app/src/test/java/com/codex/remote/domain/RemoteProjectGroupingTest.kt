package com.codex.remote.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteProjectGroupingTest {
    @Test
    fun groupsEveryThreadByNormalizedRemoteCwd() {
        val projects = groupThreadsByProject(
            listOf(
                thread("demo-old", "/srv/demo/", 10),
                thread("api", "/srv/api", 20),
                thread("demo-new", "/srv/demo", 30),
            ),
        )

        assertEquals(listOf("demo", "api"), projects.map { it.name })
        assertEquals(listOf("demo-new", "demo-old"), projects[0].threads.map { it.id })
        assertEquals("/srv/demo", projects[0].path)
    }

    @Test
    fun treatsWindowsPathCaseAndSeparatorsAsTheSameProject() {
        val projects = groupThreadsByProject(
            listOf(
                thread("one", "C:\\Work\\Codex", 10),
                thread("two", "c:/work/codex/", 20),
            ),
        )

        assertEquals(1, projects.size)
        assertEquals("codex", projects.single().name)
        assertEquals(2, projects.single().threads.size)
    }

    private fun thread(id: String, cwd: String, updatedAt: Long) = RemoteThread(
        id = id,
        title = id,
        cwd = cwd,
        updatedAt = updatedAt,
        status = "idle",
    )
}
