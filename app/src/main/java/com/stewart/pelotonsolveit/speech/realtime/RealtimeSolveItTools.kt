package com.stewart.pelotonsolveit.speech.realtime

import com.stewart.pelotonsolveit.SolveItMessage
import com.stewart.pelotonsolveit.SolveItMessagePlacement
import com.stewart.pelotonsolveit.SolveItMessageType
import com.stewart.pelotonsolveit.SolveItUiContext
import com.stewart.pelotonsolveit.addSolveItMessage
import com.stewart.pelotonsolveit.readSolveItMessage
import com.stewart.pelotonsolveit.runSolveItMessageAndWait
import com.stewart.pelotonsolveit.viewSolveItDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class RealtimeToolCall(
    val callId: String,
    val name: String,
    val arguments: JSONObject
)

internal interface SolveItToolClient {
    suspend fun viewDialog(
        dialogName: String,
        messageType: SolveItMessageType?,
        includeOutput: Boolean
    ): String

    suspend fun readMessage(dialogName: String, messageId: String): SolveItMessage

    suspend fun addMessage(
        dialogName: String,
        content: String,
        type: SolveItMessageType,
        placement: SolveItMessagePlacement,
        relativeToMessageId: String?
    ): SolveItMessage

    suspend fun runMessage(dialogName: String, messageId: String): SolveItMessage
}

internal class DirectSolveItToolClient : SolveItToolClient {
    override suspend fun viewDialog(
        dialogName: String,
        messageType: SolveItMessageType?,
        includeOutput: Boolean
    ) = withContext(Dispatchers.IO) {
        viewSolveItDialog(dialogName, messageType, includeOutput)
    }

    override suspend fun readMessage(dialogName: String, messageId: String) =
        withContext(Dispatchers.IO) { readSolveItMessage(dialogName, messageId) }

    override suspend fun addMessage(
        dialogName: String,
        content: String,
        type: SolveItMessageType,
        placement: SolveItMessagePlacement,
        relativeToMessageId: String?
    ) = withContext(Dispatchers.IO) {
        addSolveItMessage(dialogName, content, type, placement, relativeToMessageId)
    }

    override suspend fun runMessage(dialogName: String, messageId: String) =
        runSolveItMessageAndWait(dialogName, messageId)
}

internal class RealtimeSolveItTools(
    private val uiContextProvider: () -> SolveItUiContext,
    private val workspace: RealtimeWorkspace,
    private val client: SolveItToolClient = DirectSolveItToolClient(),
    private val onDialogChanged: (String) -> Unit = {}
) {
    fun sessionUpdateEvent(): JSONObject = JSONObject()
        .put("type", "session.update")
        .put(
            "session",
            JSONObject()
                .put("type", "realtime")
                .put("instructions", REALTIME_AGENT_INSTRUCTIONS)
                .put("tool_choice", "auto")
                .put("tools", toolDefinitions())
        )

    fun parseToolCall(event: JSONObject): RealtimeToolCall? {
        if (event.optString("type") != FUNCTION_ARGUMENTS_DONE_EVENT) return null
        val argumentsText = event.optString("arguments", "{}")
        return RealtimeToolCall(
            callId = event.requiredString("call_id"),
            name = event.requiredString("name"),
            arguments = if (argumentsText.isBlank()) JSONObject() else JSONObject(argumentsText)
        )
    }

    suspend fun execute(call: RealtimeToolCall): JSONObject = try {
        when (call.name) {
            GET_UI_CONTEXT -> contextResult()
            USE_CURRENT_DIALOG -> useCurrentDialogResult()
            CLEAR_WORKING_DIALOG -> clearWorkingDialogResult()
            VIEW_DIALOG -> viewDialogResult(call.arguments)
            READ_MESSAGE -> readMessageResult(call.arguments)
            ADD_MESSAGE -> addMessageResult(call.arguments)
            RUN_MESSAGE -> runMessageResult(call.arguments)
            else -> throw IllegalArgumentException("Unknown tool: ${call.name}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        JSONObject()
            .put("ok", false)
            .put(
                "error",
                JSONObject()
                    .put("type", e::class.java.simpleName)
                    .put("message", e.message ?: "Tool failed")
            )
    }

    fun functionOutputEvent(callId: String, result: JSONObject): JSONObject = JSONObject()
        .put("type", "conversation.item.create")
        .put(
            "item",
            JSONObject()
                .put("type", "function_call_output")
                .put("call_id", callId)
                .put("output", result.toString())
        )

    fun continueResponseEvent(): JSONObject = JSONObject().put("type", "response.create")

    private fun contextResult(): JSONObject {
        val snapshot = workspace.snapshot(uiContextProvider())
        return success()
            .put("visible_dialog_name", snapshot.visibleDialogName.jsonValue())
            .put("selected_message_id", snapshot.selectedMessageId.jsonValue())
            .put("working_dialog_name", snapshot.workingDialogName.jsonValue())
    }

    private fun useCurrentDialogResult(): JSONObject {
        val dialogName = workspace.useCurrentDialog(uiContextProvider())
        return success().put("working_dialog_name", dialogName)
    }

    private fun clearWorkingDialogResult(): JSONObject {
        val previous = workspace.workingDialogName
        workspace.clear()
        return success()
            .put("cleared_dialog_name", previous.jsonValue())
            .put("working_dialog_name", JSONObject.NULL)
    }

    private suspend fun viewDialogResult(arguments: JSONObject): JSONObject {
        val dialogName = workspace.requireWorkingDialog()
        val messageType = arguments.optionalMessageType("message_type")
        val includeOutput = arguments.optBoolean("include_output", false)
        val contents = client.viewDialog(dialogName, messageType, includeOutput)
        return success()
            .put("dialog_name", dialogName)
            .put("contents", contents)
    }

    private suspend fun readMessageResult(arguments: JSONObject): JSONObject {
        val dialogName = workspace.requireWorkingDialog()
        val message = client.readMessage(dialogName, arguments.requiredString("message_id"))
        return success().put("dialog_name", dialogName).put("message", message.toJson())
    }

    private suspend fun addMessageResult(arguments: JSONObject): JSONObject {
        val dialogName = workspace.requireWorkingDialog()
        val placement = when (arguments.optString("placement", "at_end")) {
            "at_end" -> SolveItMessagePlacement.AT_END
            "after_message" -> SolveItMessagePlacement.AFTER_MESSAGE
            else -> throw IllegalArgumentException("placement must be at_end or after_message")
        }
        val relativeMessageId = if (placement == SolveItMessagePlacement.AFTER_MESSAGE) {
            arguments.requiredString("relative_message_id")
        } else {
            null
        }
        val message = client.addMessage(
            dialogName = dialogName,
            content = arguments.requiredString("content"),
            type = arguments.requiredMessageType("message_type"),
            placement = placement,
            relativeToMessageId = relativeMessageId
        )
        onDialogChanged(dialogName)
        return success().put("dialog_name", dialogName).put("message", message.toJson())
    }

    private suspend fun runMessageResult(arguments: JSONObject): JSONObject {
        val dialogName = workspace.requireWorkingDialog()
        val message = client.runMessage(dialogName, arguments.requiredString("message_id"))
        onDialogChanged(dialogName)
        return success().put("dialog_name", dialogName).put("message", message.toJson())
    }

    private fun toolDefinitions() = JSONArray()
        .put(tool(GET_UI_CONTEXT, "Get the latest visible SolveIt dialog, selected message, and explicitly bound working dialog."))
        .put(tool(USE_CURRENT_DIALOG, "Bind the currently visible dialog as the working dialog after the user asks or confirms."))
        .put(tool(CLEAR_WORKING_DIALOG, "Clear the working dialog binding without changing the visible SolveIt UI."))
        .put(
            tool(
                VIEW_DIALOG,
                "Read the bound working dialog as compact XML. Use include_output only when outputs are needed.",
                properties = JSONObject()
                    .put("message_type", enumString("code", "note", "prompt"))
                    .put("include_output", JSONObject().put("type", "boolean"))
            )
        )
        .put(
            tool(
                READ_MESSAGE,
                "Read one message from the bound working dialog by ID.",
                properties = JSONObject().put("message_id", stringProperty("The SolveIt message ID.")),
                required = arrayOf("message_id")
            )
        )
        .put(
            tool(
                ADD_MESSAGE,
                "Add a note, code, or prompt message to the bound working dialog. Use after_message only with a verified message ID from that dialog.",
                properties = JSONObject()
                    .put("content", stringProperty("Complete message content."))
                    .put("message_type", enumString("code", "note", "prompt"))
                    .put("placement", enumString("at_end", "after_message"))
                    .put("relative_message_id", stringProperty("Required when placement is after_message.")),
                required = arrayOf("content", "message_type")
            )
        )
        .put(
            tool(
                RUN_MESSAGE,
                "Run a code or prompt message in the bound working dialog and wait for completion.",
                properties = JSONObject().put("message_id", stringProperty("The SolveIt message ID to run.")),
                required = arrayOf("message_id")
            )
        )

    private fun tool(
        name: String,
        description: String,
        properties: JSONObject = JSONObject(),
        required: Array<String> = emptyArray()
    ) = JSONObject()
        .put("type", "function")
        .put("name", name)
        .put("description", description)
        .put(
            "parameters",
            JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", JSONArray(required))
                .put("additionalProperties", false)
        )

    private fun stringProperty(description: String) = JSONObject()
        .put("type", "string")
        .put("description", description)

    private fun enumString(vararg values: String) = JSONObject()
        .put("type", "string")
        .put("enum", JSONArray(values))

    private fun JSONObject.requiredString(name: String): String = optString(name)
        .takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("$name is required")

    private fun JSONObject.requiredMessageType(name: String): SolveItMessageType =
        optionalMessageType(name) ?: throw IllegalArgumentException("$name is required")

    private fun JSONObject.optionalMessageType(name: String): SolveItMessageType? =
        optString(name).takeIf { it.isNotBlank() }?.let { value ->
            SolveItMessageType.entries.firstOrNull { it.apiValue == value }
                ?: throw IllegalArgumentException("$name must be code, note, or prompt")
        }

    private fun SolveItMessage.toJson() = JSONObject()
        .put("id", id)
        .put("message_type", type)
        .put("content", content)
        .put("output", output)
        .put("running", running)

    private fun success() = JSONObject().put("ok", true)

    private fun String?.jsonValue(): Any = this ?: JSONObject.NULL

    private companion object {
        const val FUNCTION_ARGUMENTS_DONE_EVENT = "response.function_call_arguments.done"
        const val GET_UI_CONTEXT = "get_ui_context"
        const val USE_CURRENT_DIALOG = "use_current_dialog"
        const val CLEAR_WORKING_DIALOG = "clear_working_dialog"
        const val VIEW_DIALOG = "view_dialog"
        const val READ_MESSAGE = "read_message"
        const val ADD_MESSAGE = "add_message"
        const val RUN_MESSAGE = "run_message"
    }
}
