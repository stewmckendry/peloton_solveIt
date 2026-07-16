package com.stewart.pelotonsolveit.speech.realtime

/**
 * The SolveIt location captured when a realtime conversation starts.
 *
 * Tool calls will use this immutable snapshot instead of following mutable
 * WebView selection state during an active session.
 */
data class SolveItContext(
    val dialogName: String,
    val selectedMessageId: String?
)
