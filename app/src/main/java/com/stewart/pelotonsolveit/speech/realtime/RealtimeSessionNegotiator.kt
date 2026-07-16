package com.stewart.pelotonsolveit.speech.realtime

/**
 * Exchanges a local WebRTC SDP offer for the remote SDP answer.
 *
 * Keeping this boundary separate lets the direct OpenAI request used by the
 * private v1 build move to a backend later without changing the WebRTC engine.
 */
interface RealtimeSessionNegotiator {
    suspend fun negotiate(
        offerSdp: String,
        config: RealtimeSessionConfig
    ): String
}
