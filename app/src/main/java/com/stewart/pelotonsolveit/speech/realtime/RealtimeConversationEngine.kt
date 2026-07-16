package com.stewart.pelotonsolveit.speech.realtime

import kotlinx.coroutines.flow.StateFlow

/**
 * A persistent, bidirectional speech session.
 *
 * This is separate from SpeechEngine because a realtime conversation has a
 * lifecycle and continuously emits state instead of returning one transcript.
 */
interface RealtimeConversationEngine {
    val state: StateFlow<RealtimeState>

    suspend fun start(context: SolveItContext)

    suspend fun stop()

    suspend fun interrupt()
}
