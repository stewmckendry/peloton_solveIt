package com.stewart.pelotonsolveit.speech.realtime

sealed interface RealtimeState {
    data object Disconnected : RealtimeState
    data object Connecting : RealtimeState
    data object Listening : RealtimeState
    data object Thinking : RealtimeState
    data object Speaking : RealtimeState
    data object Stopping : RealtimeState
    data class Error(val message: String) : RealtimeState
}
