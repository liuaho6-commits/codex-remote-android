package com.codex.remote.domain

enum class ComposerTriggerKind { SLASH_COMMAND, MENTION }

data class ComposerTrigger(
    val kind: ComposerTriggerKind,
    val start: Int,
    val end: Int,
    val query: String,
)

data class ComposerTextUpdate(
    val text: String,
    val cursor: Int,
)

internal fun findComposerTrigger(text: String, cursor: Int): ComposerTrigger? {
    val safeCursor = cursor.coerceIn(0, text.length)
    val beforeCursor = text.substring(0, safeCursor)
    val lineStart = beforeCursor.lastIndexOf('\n') + 1
    val currentLine = beforeCursor.substring(lineStart)
    if (currentLine.startsWith("/") && !currentLine.drop(1).any(Char::isWhitespace)) {
        return ComposerTrigger(
            kind = ComposerTriggerKind.SLASH_COMMAND,
            start = lineStart,
            end = safeCursor,
            query = currentLine.drop(1),
        )
    }

    val mentionStart = beforeCursor.lastIndexOf('$')
    if (mentionStart < 0) return null
    if (mentionStart > 0 && !text[mentionStart - 1].isWhitespace()) return null
    val query = beforeCursor.substring(mentionStart + 1)
    if (query.any(Char::isWhitespace)) return null
    return ComposerTrigger(
        kind = ComposerTriggerKind.MENTION,
        start = mentionStart,
        end = safeCursor,
        query = query,
    )
}

internal fun replaceComposerTrigger(
    text: String,
    trigger: ComposerTrigger,
    replacement: String,
): ComposerTextUpdate {
    val updated = text.replaceRange(trigger.start, trigger.end, replacement)
    return ComposerTextUpdate(updated, trigger.start + replacement.length)
}

internal fun String.containsComposerToken(token: String): Boolean {
    var index = indexOf(token)
    while (index >= 0) {
        val beforeValid = index == 0 || this[index - 1].isWhitespace()
        val end = index + token.length
        val afterValid = end == length || this[end].isWhitespace() || this[end] in ",.;:!?)]}"
        if (beforeValid && afterValid) return true
        index = indexOf(token, startIndex = index + 1)
    }
    return false
}

internal fun RemotePlugin.composerToken(): String = "\$$name"

internal fun RemoteSkill.composerToken(): String = "\$$name"
