package com.codex.remote.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionDraftValidationTest {
    private val validDraft = ConnectionDraft(
        name = "Devbox",
        host = "devbox.example.com",
        port = "22",
        username = "developer",
        password = "secret",
    )

    @Test
    fun reportsAllMissingRequiredFieldsInFormOrder() {
        assertEquals(
            listOf(
                ConnectionDraftIssue.CONNECTION_NAME,
                ConnectionDraftIssue.HOST,
                ConnectionDraftIssue.USERNAME,
                ConnectionDraftIssue.PASSWORD,
            ),
            ConnectionDraft().validationIssues(),
        )
    }

    @Test
    fun rejectsPortsOutsideSshRange() {
        assertEquals(listOf(ConnectionDraftIssue.PORT), validDraft.copy(port = "0").validationIssues())
        assertEquals(listOf(ConnectionDraftIssue.PORT), validDraft.copy(port = "65536").validationIssues())
        assertTrue(validDraft.copy(port = "65535").validationIssues().isEmpty())
    }

    @Test
    fun requiresTheSelectedAuthenticationSecret() {
        assertEquals(
            listOf(ConnectionDraftIssue.PRIVATE_KEY),
            validDraft.copy(authType = AuthType.PRIVATE_KEY, password = "").validationIssues(),
        )
    }

    @Test
    fun acceptsAnExistingEncryptedSecretWhenEditing() {
        val existing = SavedConnection(
            name = "Devbox",
            host = "devbox.example.com",
            username = "developer",
            authType = AuthType.PRIVATE_KEY,
            encryptedPrivateKey = "encrypted",
        )

        assertTrue(
            validDraft.copy(authType = AuthType.PRIVATE_KEY, password = "").validationIssues(existing).isEmpty(),
        )
    }
}
