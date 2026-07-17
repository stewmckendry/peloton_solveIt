package com.stewart.pelotonsolveit.speech.realtime

import com.stewart.pelotonsolveit.SolveItMessage
import com.stewart.pelotonsolveit.SolveItMessagePlacement
import com.stewart.pelotonsolveit.SolveItMessageType
import com.stewart.pelotonsolveit.SolveItUiContext
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeSolveItToolsTest {
    @Test
    fun sessionUpdateIncludesInstructionsAndAllTools() {
        val tools = newTools()

        val session = tools.sessionUpdateEvent().getJSONObject("session")
        val names = session.getJSONArray("tools").let { definitions ->
            (0 until definitions.length()).map {
                definitions.getJSONObject(it).getString("name")
            }
        }

        assertEquals("session.update", tools.sessionUpdateEvent().getString("type"))
        assertEquals("realtime", session.getString("type"))
        assertEquals("auto", session.getString("tool_choice"))
        val instructions = session.getString("instructions")
        assertTrue(instructions.contains("visible dialog"))
        assertTrue(instructions.contains("Dialog Engineering"))
        assertTrue(instructions.contains("persistent Python kernel"))
        assertTrue(instructions.contains("messages above that prompt"))
        assertEquals(
            listOf(
                "get_ui_context",
                "use_current_dialog",
                "clear_working_dialog",
                "view_dialog",
                "read_message",
                "add_message",
                "run_message"
            ),
            names
        )
    }

    @Test
    fun parsesCompletedCallAndBuildsFunctionOutputEvents() = runBlocking {
        val tools = newTools()
        val call = tools.parseToolCall(
            JSONObject()
                .put("type", "response.function_call_arguments.done")
                .put("call_id", "call-123")
                .put("name", "get_ui_context")
                .put("arguments", "{}")
        )!!

        val result = tools.execute(call)
        val output = tools.functionOutputEvent(call.callId, result)
        val item = output.getJSONObject("item")

        assertEquals("get_ui_context", call.name)
        assertEquals("conversation.item.create", output.getString("type"))
        assertEquals("function_call_output", item.getString("type"))
        assertEquals("call-123", item.getString("call_id"))
        assertTrue(JSONObject(item.getString("output")).getBoolean("ok"))
        assertEquals("response.create", tools.continueResponseEvent().getString("type"))
    }

    @Test
    fun visibleAndWorkingDialogsRemainIndependent() = runBlocking {
        var uiContext = SolveItUiContext("draft-one", "message-1")
        val workspace = RealtimeWorkspace()
        val tools = newTools({ uiContext }, workspace)

        val bindResult = tools.execute(call("use_current_dialog"))
        uiContext = SolveItUiContext("draft-two", "message-2")
        val contextResult = tools.execute(call("get_ui_context"))

        assertTrue(bindResult.getBoolean("ok"))
        assertEquals("draft-two", contextResult.getString("visible_dialog_name"))
        assertEquals("message-2", contextResult.getString("selected_message_id"))
        assertEquals("draft-one", contextResult.getString("working_dialog_name"))
    }

    @Test
    fun useCurrentDialogReturnsRecoverableErrorWhenNothingIsVisible() = runBlocking {
        val workspace = RealtimeWorkspace()
        val result = newTools({ SolveItUiContext(null, null) }, workspace)
            .execute(call("use_current_dialog"))

        assertFalse(result.getBoolean("ok"))
        assertTrue(result.getJSONObject("error").getString("message").contains("No SolveIt dialog"))
        assertNull(workspace.workingDialogName)
    }

    @Test
    fun addMessageUsesOnlyExplicitlyBoundDialog() = runBlocking {
        var uiContext = SolveItUiContext("draft-one", "message-1")
        val workspace = RealtimeWorkspace()
        val client = FakeSolveItToolClient()
        val tools = newTools({ uiContext }, workspace, client)
        tools.execute(call("use_current_dialog"))
        uiContext = SolveItUiContext("draft-two", "message-2")

        val result = tools.execute(
            call(
                "add_message",
                JSONObject()
                    .put("content", "Opening paragraph")
                    .put("message_type", "note")
            )
        )

        assertTrue(result.getBoolean("ok"))
        assertEquals("draft-one", client.lastDialogName)
        assertEquals("Opening paragraph", client.lastContent)
        assertEquals(SolveItMessageType.NOTE, client.lastMessageType)
        assertEquals(SolveItMessagePlacement.AT_END, client.lastPlacement)
    }

    @Test
    fun successfulMutationReportsWhichDialogChanged() = runBlocking {
        val workspace = RealtimeWorkspace()
        workspace.useCurrentDialog(SolveItUiContext("draft-one", null))
        val changedDialogs = mutableListOf<String>()
        val tools = RealtimeSolveItTools(
            uiContextProvider = { SolveItUiContext("draft-one", null) },
            workspace = workspace,
            client = FakeSolveItToolClient(),
            onDialogChanged = changedDialogs::add
        )

        tools.execute(call("run_message", JSONObject().put("message_id", "message-1")))

        assertEquals(listOf("draft-one"), changedDialogs)
    }

    private fun newTools(
        uiContextProvider: () -> SolveItUiContext = {
            SolveItUiContext("visible-dialog", "selected-message")
        },
        workspace: RealtimeWorkspace = RealtimeWorkspace(),
        client: SolveItToolClient = FakeSolveItToolClient()
    ) = RealtimeSolveItTools(uiContextProvider, workspace, client)

    private fun call(name: String, arguments: JSONObject = JSONObject()) = RealtimeToolCall(
        callId = "call-id",
        name = name,
        arguments = arguments
    )
}

private class FakeSolveItToolClient : SolveItToolClient {
    var lastDialogName: String? = null
    var lastContent: String? = null
    var lastMessageType: SolveItMessageType? = null
    var lastPlacement: SolveItMessagePlacement? = null

    override suspend fun viewDialog(
        dialogName: String,
        messageType: SolveItMessageType?,
        includeOutput: Boolean
    ): String = "<dialog />"

    override suspend fun readMessage(dialogName: String, messageId: String) = message(messageId)

    override suspend fun addMessage(
        dialogName: String,
        content: String,
        type: SolveItMessageType,
        placement: SolveItMessagePlacement,
        relativeToMessageId: String?
    ): SolveItMessage {
        lastDialogName = dialogName
        lastContent = content
        lastMessageType = type
        lastPlacement = placement
        return message("new-message", type.apiValue, content)
    }

    override suspend fun runMessage(dialogName: String, messageId: String) = message(messageId)

    private fun message(
        id: String,
        type: String = "note",
        content: String = "content"
    ) = SolveItMessage(
        id = id,
        type = type,
        content = content,
        output = "",
        running = false
    )
}
