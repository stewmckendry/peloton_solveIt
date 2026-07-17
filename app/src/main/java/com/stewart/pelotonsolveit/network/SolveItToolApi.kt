package com.stewart.pelotonsolveit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener

enum class SolveItMessageType(val apiValue: String) {
    CODE("code"),
    NOTE("note"),
    PROMPT("prompt")
}

enum class SolveItMessagePlacement {
    AT_END,
    AFTER_MESSAGE
}

data class SolveItMessage(
    val id: String,
    val type: String,
    val content: String,
    val output: String,
    val running: Boolean
)

fun viewSolveItDialog(
    dialogName: String,
    messageType: SolveItMessageType? = null,
    includeOutput: Boolean = false
): String {
    require(dialogName.isNotBlank()) { "Dialog name is required" }
    val params = mutableMapOf(
        "dlg_name" to dialogName,
        "as_xml" to "true",
        "nums" to "false",
        "include_meta" to "false",
        "include_output" to includeOutput.toString(),
        "trunc_out" to "true",
        "trunc_in" to "false"
    )
    messageType?.let { params["msg_type"] = it.apiValue }
    return parseSolveItTextResponse(solveItPost("find_msgs_", params))
}

fun readSolveItMessage(dialogName: String, messageId: String): SolveItMessage {
    require(dialogName.isNotBlank()) { "Dialog name is required" }
    require(messageId.isNotBlank()) { "Message ID is required" }
    val response = solveItPost(
        "read_msg_",
        mapOf(
            "dlg_name" to dialogName,
            "id_" to messageId,
            "n" to "0",
            "relative" to "true"
        )
    )
    return parseSolveItMessage(parseSolveItObjectResponse(response))
}

fun addSolveItMessage(
    dialogName: String,
    content: String,
    type: SolveItMessageType,
    placement: SolveItMessagePlacement = SolveItMessagePlacement.AT_END,
    relativeToMessageId: String? = null
): SolveItMessage {
    require(dialogName.isNotBlank()) { "Dialog name is required" }
    require(content.isNotBlank()) { "Message content is required" }
    val params = mutableMapOf(
        "dlg_name" to dialogName,
        "content" to content,
        "msg_type" to type.apiValue,
        "heading_collapsed" to "0",
        "i_collapsed" to "0",
        "o_collapsed" to "0"
    )
    when (placement) {
        SolveItMessagePlacement.AT_END -> params["placement"] = "at_end"
        SolveItMessagePlacement.AFTER_MESSAGE -> {
            params["id_"] = requireNotNull(relativeToMessageId) {
                "A relative message ID is required for AFTER_MESSAGE placement"
            }
        }
    }
    val result = parseSolveItObjectResponse(solveItPost("add_relative_", params))
    return readSolveItMessage(dialogName, result.getString("id"))
}

suspend fun runSolveItMessageAndWait(
    dialogName: String,
    messageId: String,
    timeoutMs: Long = 30_000,
    pollIntervalMs: Long = 200
): SolveItMessage = withContext(Dispatchers.IO) {
    require(timeoutMs > 0) { "Timeout must be positive" }
    require(pollIntervalMs > 0) { "Poll interval must be positive" }
    solveItPost(
        "add_runq_",
        mapOf(
            "dlg_name" to dialogName,
            "id_" to messageId,
            "api" to "true"
        )
    )

    val deadline = System.nanoTime() + timeoutMs * 1_000_000
    var message = readSolveItMessage(dialogName, messageId)
    while (message.running) {
        if (System.nanoTime() >= deadline) {
            throw SolveItTimeoutException(
                "Timed out waiting for message $messageId in $dialogName"
            )
        }
        delay(pollIntervalMs)
        message = readSolveItMessage(dialogName, messageId)
    }
    message
}

private fun parseSolveItObjectResponse(response: String): JSONObject {
    val value = JSONTokener(response).nextValue()
    val json = value as? JSONObject
        ?: throw IllegalStateException("Expected a SolveIt JSON object response")
    if (json.has("error")) throw IllegalStateException(json.optString("error"))
    return json
}

private fun parseSolveItTextResponse(response: String): String {
    val value = runCatching { JSONTokener(response).nextValue() }.getOrNull()
    if (value is JSONObject && value.has("error")) {
        throw IllegalStateException(value.optString("error"))
    }
    return if (value is String) value else response
}

private fun parseSolveItMessage(json: JSONObject) = SolveItMessage(
    id = json.getString("id"),
    type = json.optString("msg_type"),
    content = json.nullableString("content"),
    output = json.nullableString("output"),
    running = json.truthy("run")
)

private fun JSONObject.nullableString(key: String): String =
    if (isNull(key)) "" else optString(key)

private fun JSONObject.truthy(key: String): Boolean = when (val value = opt(key)) {
    is Boolean -> value
    is Number -> value.toInt() != 0
    is String -> value.isNotBlank() && value != "0" && !value.equals("false", true)
    else -> false
}

class SolveItTimeoutException(message: String) : Exception(message)
