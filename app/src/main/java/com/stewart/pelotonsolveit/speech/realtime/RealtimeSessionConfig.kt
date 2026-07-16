package com.stewart.pelotonsolveit.speech.realtime

import org.json.JSONObject

data class RealtimeSessionConfig(
    val model: String = DEFAULT_MODEL,
    val voice: String = DEFAULT_VOICE
) {
    fun toJson(): String = JSONObject()
        .put("type", "realtime")
        .put("model", model)
        .put(
            "audio",
            JSONObject().put(
                "output",
                JSONObject().put("voice", voice)
            )
        )
        .toString()

    companion object {
        const val DEFAULT_MODEL = "gpt-realtime-2.1"
        const val DEFAULT_VOICE = "marin"
    }
}
