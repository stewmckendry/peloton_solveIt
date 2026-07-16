package com.stewart.pelotonsolveit.speech.realtime

import android.content.Context
import android.media.MediaRecorder
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.Closeable
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Minimal bidirectional OpenAI Realtime audio session.
 *
 * WebRTC captures and sends the local microphone track and automatically plays
 * the remote audio track through Android's active audio output.
 */
class RealtimeAudioSession(
    context: Context,
    private val negotiator: RealtimeSessionNegotiator,
    private val onStatus: (String) -> Unit
) : Closeable {
    private val applicationContext = context.applicationContext
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var initialGreetingSent = false

    suspend fun connect(config: RealtimeSessionConfig = RealtimeSessionConfig()) {
        close()
        initialGreetingSent = false
        report("Initializing WebRTC")

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(applicationContext)
                .createInitializationOptions()
        )
        val audioModule = JavaAudioDeviceModule.builder(applicationContext)
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setUseHardwareAcousticEchoCanceler(
                JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()
            )
            .setUseHardwareNoiseSuppressor(
                JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()
            )
            .createAudioDeviceModule()
        audioDeviceModule = audioModule

        val factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .createPeerConnectionFactory()
        peerConnectionFactory = factory

        val connection = factory.createPeerConnection(
            PeerConnection.RTCConfiguration(emptyList()),
            createPeerConnectionObserver()
        ) ?: throw RealtimeConnectionException("Unable to create WebRTC peer connection")
        peerConnection = connection

        val source = factory.createAudioSource(MediaConstraints())
        audioSource = source
        val microphoneTrack = factory.createAudioTrack(LOCAL_AUDIO_TRACK_ID, source)
        microphoneTrack.setEnabled(true)
        localAudioTrack = microphoneTrack
        connection.addTrack(microphoneTrack, listOf(LOCAL_MEDIA_STREAM_ID))

        dataChannel = connection.createDataChannel(
            OPENAI_EVENTS_CHANNEL,
            DataChannel.Init()
        ).also { channel ->
            channel.registerObserver(createDataChannelObserver())
        }

        report("Creating SDP offer")
        val offer = connection.createOffer()
        connection.setLocalDescription(offer)

        report("Negotiating with OpenAI")
        val answerSdp = negotiator.negotiate(offer.description, config)
        report("OpenAI returned SDP answer")
        connection.setRemoteDescription(
            SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        )
        report("Remote description accepted")
    }

    override fun close() {
        closeConnection()
        localAudioTrack?.setEnabled(false)
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        audioDeviceModule?.release()
        audioDeviceModule = null
    }

    private fun closeConnection() {
        dataChannel?.close()
        dataChannel?.dispose()
        dataChannel = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
    }

    private fun createPeerConnectionObserver() = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) {
            report("Signaling: $state")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            report("ICE: $state")
        }

        override fun onStandardizedIceConnectionChange(
            state: PeerConnection.IceConnectionState
        ) {
            report("ICE standardized: $state")
        }

        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            report("Peer connection: $state")
            if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                report("Realtime audio active")
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
            report("ICE gathering: $state")
        }

        override fun onIceCandidate(candidate: IceCandidate) = Unit

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit

        override fun onAddStream(stream: MediaStream) = Unit

        override fun onRemoveStream(stream: MediaStream) = Unit

        override fun onDataChannel(channel: DataChannel) = Unit

        override fun onRenegotiationNeeded() = Unit

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) = Unit

        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is AudioTrack) {
                track.setEnabled(true)
                track.setVolume(1.0)
                report("Remote audio track received")
            }
        }
    }

    private fun createDataChannelObserver() = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) = Unit

        override fun onStateChange() {
            val state = dataChannel?.state()
            report("Data channel: $state")
            if (state == DataChannel.State.OPEN && !initialGreetingSent) {
                initialGreetingSent = true
                sendInitialGreeting()
            }
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            if (buffer.binary) return
            val bytes = ByteArray(buffer.data.remaining())
            buffer.data.get(bytes)
            val event = bytes.toString(Charsets.UTF_8)
            val eventType = runCatching {
                JSONObject(event).optString("type")
            }.getOrDefault("")
            if (eventType.isNotBlank()) {
                report("OpenAI event: $eventType")
            }
        }
    }

    private fun report(message: String) = onStatus(message)

    private fun sendInitialGreeting() {
        val event = JSONObject()
            .put("type", "response.create")
            .put(
                "response",
                JSONObject().put(
                    "instructions",
                    "Briefly say: Realtime conversation is ready. What would you like to work on?"
                )
            )
            .toString()
        val sent = dataChannel?.send(
            DataChannel.Buffer(
                ByteBuffer.wrap(event.toByteArray(Charsets.UTF_8)),
                false
            )
        ) == true
        report(if (sent) "Requested initial greeting" else "Initial greeting failed")
    }

    private suspend fun PeerConnection.createOffer(): SessionDescription =
        suspendCancellableCoroutine { continuation ->
            createOffer(
                object : SdpObserver {
                    override fun onCreateSuccess(description: SessionDescription) {
                        if (continuation.isActive) continuation.resume(description)
                    }

                    override fun onCreateFailure(error: String) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                RealtimeConnectionException(
                                    "Unable to create SDP offer: $error"
                                )
                            )
                        }
                    }

                    override fun onSetSuccess() = Unit
                    override fun onSetFailure(error: String) = Unit
                },
                MediaConstraints()
            )
        }

    private suspend fun PeerConnection.setLocalDescription(
        description: SessionDescription
    ) = suspendCancellableCoroutine { continuation ->
        setLocalDescription(
            object : SdpObserver {
                override fun onSetSuccess() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onSetFailure(error: String) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            RealtimeConnectionException(
                                "Unable to set local SDP description: $error"
                            )
                        )
                    }
                }

                override fun onCreateSuccess(description: SessionDescription) = Unit
                override fun onCreateFailure(error: String) = Unit
            },
            description
        )
    }

    private suspend fun PeerConnection.setRemoteDescription(
        description: SessionDescription
    ) = suspendCancellableCoroutine { continuation ->
        setRemoteDescription(
            object : SdpObserver {
                override fun onSetSuccess() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onSetFailure(error: String) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            RealtimeConnectionException(
                                "Unable to set remote SDP description: $error"
                            )
                        )
                    }
                }

                override fun onCreateSuccess(description: SessionDescription) = Unit
                override fun onCreateFailure(error: String) = Unit
            },
            description
        )
    }

    private companion object {
        const val OPENAI_EVENTS_CHANNEL = "oai-events"
        const val LOCAL_AUDIO_TRACK_ID = "peloton-microphone"
        const val LOCAL_MEDIA_STREAM_ID = "peloton-realtime"
    }
}

class RealtimeConnectionException(message: String) : Exception(message)
