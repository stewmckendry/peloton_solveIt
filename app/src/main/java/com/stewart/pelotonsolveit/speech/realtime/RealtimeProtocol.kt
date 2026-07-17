package com.stewart.pelotonsolveit.speech.realtime

import org.json.JSONObject

/** Collects function calls until their originating Realtime response is complete. */
internal class RealtimeToolCallBatcher {
    private val pendingCalls = linkedMapOf<String, MutableList<RealtimeToolCall>>()

    @Synchronized
    fun accept(event: JSONObject, tools: RealtimeSolveItTools): List<RealtimeToolCall>? {
        tools.parseToolCall(event)?.let { call ->
            pendingCalls.getOrPut(call.responseId) { mutableListOf() }.add(call)
            return null
        }
        if (event.optString("type") != RESPONSE_DONE_EVENT) return null

        val response = event.optJSONObject("response") ?: return emptyList()
        val responseId = response.optString("id")
        if (responseId.isBlank()) return emptyList()
        val calls = pendingCalls.remove(responseId).orEmpty()
        return if (response.optString("status") == "completed") calls else emptyList()
    }

    @Synchronized
    fun clear() {
        pendingCalls.clear()
    }

    private companion object {
        const val RESPONSE_DONE_EVENT = "response.done"
    }
}

internal fun describeRealtimeError(event: JSONObject): String {
    val error = event.optJSONObject("error")
        ?: return "OpenAI error: malformed error event"
    return buildString {
        append("OpenAI error")
        error.optString("code").takeIf { it.isNotBlank() }?.let { append(" code=$it") }
        error.optString("param").takeIf { it.isNotBlank() }?.let { append(" param=$it") }
        event.optString("event_id").takeIf { it.isNotBlank() }?.let { append(" event_id=$it") }
        error.optString("message").takeIf { it.isNotBlank() }?.let {
            append(" message=")
            append(it.take(MAX_ERROR_MESSAGE_LENGTH))
        }
    }
}

private const val MAX_ERROR_MESSAGE_LENGTH = 500
