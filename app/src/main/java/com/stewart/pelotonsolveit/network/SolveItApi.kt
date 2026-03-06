package com.stewart.pelotonsolveit

import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

fun solveItPost(path: String, params: Map<String, String>): String {
    val client = OkHttpClient()
    val bodyBuilder = FormBody.Builder()
    params.forEach { (k, v) -> bodyBuilder.add(k, v) }
    Log.d("PelotonSolveIt", "POST $path params=$params")
    val request = Request.Builder()
        .url("${BuildConfig.SOLVEIT_URL}/$path")
        .addHeader("Cookie", "_solveit=${BuildConfig.SOLVEIT_TOKEN}")
        .post(bodyBuilder.build())
        .build()
    val responseBody = client.newCall(request).execute().body?.string() ?: ""
    Log.d("PelotonSolveIt", "Response: $responseBody")
    return responseBody
}

fun sendToSolveIt(msg: String, bridge: SolveItJSBridge) {
    val dlgName = bridge.dlgName
    val msgId = bridge.msgId
    if( dlgName.isEmpty() && msgId.isEmpty() ) {
        Log.d("PelotonSolveIt", "POST to SolveIt skipped - no dlgName or msgId found")
        return
    }
    val params = mutableMapOf(
        "dlg_name" to dlgName,
        "content" to msg,
        "msg_type" to "prompt"
    )
    if (msgId.isNotEmpty()) params["id"] = msgId
    else params["placement"] = "at_end"
    val addMsgResult = solveItPost(
        "add_relative_",
        params)
    val json = JSONObject(addMsgResult)
    val newMsgId = json.getString("id")
    Log.d("PelotonSolveIt", "msgId=$msgId")
    val result = solveItPost(
        "add_runq_",
        mapOf("dlg_name" to dlgName,
            "id_" to newMsgId,
            "api" to "true"))
    Log.d("PelotonSolveIt", "result=$result")
}

