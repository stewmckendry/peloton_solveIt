package com.stewart.pelotonsolveit.speech.realtime

import android.content.Context
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.Closeable
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Connection-only probe for validating OpenAI Realtime WebRTC signaling.
 *
 * It creates an audio transceiver without attaching a microphone track. Full
 * audio capture and playback will be added only after this handshake is proven.
 */
class RealtimeConnectionProbe(
    context: Context,
    private val negotiator: RealtimeSessionNegotiator,
    private val onStatus: (String) -> Unit
) : Closeable {
    private val applicationContext = context.applicationContext
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    suspend fun connect(config: RealtimeSessionConfig = RealtimeSessionConfig()) {
        closeConnection()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        report("Initializing WebRTC")

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(applicationContext)
                .createInitializationOptions()
        )
        val factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        peerConnectionFactory = factory

        val connection = factory.createPeerConnection(
            PeerConnection.RTCConfiguration(emptyList()),
            createPeerConnectionObserver()
        ) ?: throw RealtimeConnectionException("Unable to create WebRTC peer connection")
        peerConnection = connection

        connection.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(
                RtpTransceiver.RtpTransceiverDirection.SEND_RECV
            )
        )
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
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        report("Disconnected")
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

        override fun onTrack(transceiver: RtpTransceiver) = Unit
    }

    private fun createDataChannelObserver() = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) = Unit

        override fun onStateChange() {
            report("Data channel: ${dataChannel?.state()}")
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
    }
}

class RealtimeConnectionException(message: String) : Exception(message)
