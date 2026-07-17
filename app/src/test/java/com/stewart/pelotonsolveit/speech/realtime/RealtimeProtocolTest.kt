package com.stewart.pelotonsolveit.speech.realtime

import com.stewart.pelotonsolveit.SolveItUiContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeProtocolTest {
    private val tools = RealtimeSolveItTools(
        uiContextProvider = { SolveItUiContext(null, null) },
        workspace = RealtimeWorkspace(),
        client = FakeProtocolSolveItToolClient()
    )

    @Test
    fun batchesAllCallsUntilResponseIsCompleted() {
        val batcher = RealtimeToolCallBatcher()

        assertNull(batcher.accept(toolCall("response-1", "call-1", "get_ui_context"), tools))
        assertNull(batcher.accept(toolCall("response-1", "call-2", "clear_working_dialog"), tools))
        val calls = batcher.accept(responseDone("response-1", "completed"), tools)!!

        assertEquals(listOf("call-1", "call-2"), calls.map { it.callId })
    }

    @Test
    fun differentResponsesHaveIndependentBatches() {
        val batcher = RealtimeToolCallBatcher()
        batcher.accept(toolCall("response-1", "call-1", "get_ui_context"), tools)
        batcher.accept(toolCall("response-2", "call-2", "get_ui_context"), tools)

        assertEquals(
            listOf("call-2"),
            batcher.accept(responseDone("response-2", "completed"), tools)!!.map { it.callId }
        )
        assertEquals(
            listOf("call-1"),
            batcher.accept(responseDone("response-1", "completed"), tools)!!.map { it.callId }
        )
    }

    @Test
    fun cancelledResponseDropsItsPendingCalls() {
        val batcher = RealtimeToolCallBatcher()
        batcher.accept(toolCall("response-1", "call-1", "add_message"), tools)

        assertTrue(batcher.accept(responseDone("response-1", "cancelled"), tools)!!.isEmpty())
        assertTrue(batcher.accept(responseDone("response-1", "completed"), tools)!!.isEmpty())
    }

    @Test
    fun describesSafeOpenAiErrorFieldsAndTruncatesMessage() {
        val description = describeRealtimeError(
            JSONObject()
                .put("type", "error")
                .put("event_id", "event-1")
                .put(
                    "error",
                    JSONObject()
                        .put("code", "conversation_already_has_active_response")
                        .put("param", "response")
                        .put("message", "x".repeat(700))
                )
        )

        assertTrue(description.contains("code=conversation_already_has_active_response"))
        assertTrue(description.contains("param=response"))
        assertTrue(description.contains("event_id=event-1"))
        assertTrue(description.length < 650)
    }

    private fun toolCall(responseId: String, callId: String, name: String) = JSONObject()
        .put("type", "response.function_call_arguments.done")
        .put("response_id", responseId)
        .put("call_id", callId)
        .put("name", name)
        .put("arguments", "{}")

    private fun responseDone(responseId: String, status: String) = JSONObject()
        .put("type", "response.done")
        .put(
            "response",
            JSONObject()
                .put("id", responseId)
                .put("status", status)
        )
}

private class FakeProtocolSolveItToolClient : SolveItToolClient {
    override suspend fun viewDialog(
        dialogName: String,
        messageType: com.stewart.pelotonsolveit.SolveItMessageType?,
        includeOutput: Boolean
    ) = ""

    override suspend fun readMessage(dialogName: String, messageId: String) = unsupported()

    override suspend fun addMessage(
        dialogName: String,
        content: String,
        type: com.stewart.pelotonsolveit.SolveItMessageType,
        placement: com.stewart.pelotonsolveit.SolveItMessagePlacement,
        relativeToMessageId: String?
    ) = unsupported()

    override suspend fun runMessage(dialogName: String, messageId: String) = unsupported()

    private fun unsupported(): Nothing = error("Not used by protocol tests")
}
