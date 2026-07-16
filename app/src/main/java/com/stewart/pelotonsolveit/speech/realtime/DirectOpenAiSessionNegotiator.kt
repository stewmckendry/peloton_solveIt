package com.stewart.pelotonsolveit.speech.realtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DirectOpenAiSessionNegotiator(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient()
) : RealtimeSessionNegotiator {

    override suspend fun negotiate(
        offerSdp: String,
        config: RealtimeSessionConfig
    ): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "OpenAI API key is missing" }
        require(offerSdp.isNotBlank()) { "WebRTC SDP offer is empty" }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "sdp",
                null,
                offerSdp.toRequestBody(SDP_MEDIA_TYPE)
            )
            .addFormDataPart(
                "session",
                null,
                config.toJson().toRequestBody(JSON_MEDIA_TYPE)
            )
            .build()

        val request = Request.Builder()
            .url(REALTIME_CALLS_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RealtimeSessionException(
                    "OpenAI Realtime session negotiation failed with HTTP ${response.code}: " +
                        responseBody.take(MAX_ERROR_BODY_LENGTH)
                )
            }
            if (responseBody.isBlank()) {
                throw RealtimeSessionException(
                    "OpenAI Realtime session negotiation returned an empty SDP answer"
                )
            }
            responseBody
        }
    }

    private companion object {
        const val REALTIME_CALLS_URL = "https://api.openai.com/v1/realtime/calls"
        const val MAX_ERROR_BODY_LENGTH = 1_000
        val SDP_MEDIA_TYPE = "application/sdp".toMediaType()
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

class RealtimeSessionException(message: String) : Exception(message)
