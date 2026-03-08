package com.stewart.pelotonsolveit.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate
import com.stewart.pelotonsolveit.BuildConfig
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File

@SuppressLint("MissingPermission")
class WhisperSpeechEngine(private val context: Context, private val openai_api_key: String) : SpeechEngine,
    Closeable {
    private val vad = VadSilero(
        context,
        sampleRate = SampleRate.SAMPLE_RATE_16K,
        frameSize = FrameSize.FRAME_SIZE_512,
        mode = Mode.AGGRESSIVE,
        silenceDurationMs = 700,
        speechDurationMs = 150
    )
    override fun listen(): String? {
        val frameSize = 512
        val bufferSize = frameSize * 2  // 1024 bytes — 512 samples × 2 bytes each
        val audio = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize)
        val buffer = ByteArray(bufferSize)
        try {
            audio.startRecording()
            val recordedAudio = mutableListOf<Byte>()
            var speechDetected = false
            var silentFrames = 0
            val maxSilentFrames = 150  // ~5 seconds of silence before giving up
            while (audio.read(buffer, 0, bufferSize) > 0) {
                recordedAudio.addAll(buffer.toList())
                if (vad.isSpeech(buffer)) {
                    if (!speechDetected) Log.d("WhisperSpeechEngine", "Speech started")
                    speechDetected = true
                    silentFrames = 0
                }
                else if (speechDetected) break
                else if ( silentFrames > maxSilentFrames) {
                    Log.d("WhisperSpeechEngine", "Error - No speech detected")
                    return null
                }
                else silentFrames++
            }
            Log.d("WhisperSpeechEngine", "Recording stopped, ${recordedAudio.size} bytes captured")
            val wavFile = File.createTempFile("recording", ".wav", context.cacheDir)
            saveAsWav(recordedAudio, wavFile)
            Log.d("WhisperSpeechEngine", "WAV file: ${wavFile.length()} bytes at ${wavFile.absolutePath}")
            val whisperResult = whisperTranscribe(wavFile, openai_api_key)
            wavFile.delete()
            val json = JSONObject(whisperResult)
            if (json.has("error")) {
                Log.e("WhisperSpeechEngine", "API error: ${json.getJSONObject("error").getString("message")}")
                return null
            }
            return json.getString("text")
        } catch (e: SecurityException) {
            Log.e("WhisperSpeechEngine", "Mic permission denied", e)
            return null
        } catch (e: Exception) {
            Log.e("WhisperSpeechEngine", "Unexpected error during listen", e)
            return null
        }  finally {
            audio.stop()
            audio.release()
        }
    }

    override fun close() {
        vad.close()
    }
}

private fun saveAsWav(pcmData: List<Byte>, file: File) {
    val bytes = pcmData.toByteArray()
    val totalDataLen = bytes.size + 36
    DataOutputStream(file.outputStream()).use { out ->
        // WAV header
        out.writeBytes("RIFF")
        out.writeInt(Integer.reverseBytes(totalDataLen))
        out.writeBytes("WAVEfmt ")
        out.writeInt(Integer.reverseBytes(16))        // chunk size
        out.writeShort(java.lang.Short.reverseBytes(1).toInt())   // PCM format
        out.writeShort(java.lang.Short.reverseBytes(1).toInt())   // mono
        out.writeInt(Integer.reverseBytes(16000))     // sample rate
        out.writeInt(Integer.reverseBytes(32000))     // byte rate (16000 * 2)
        out.writeShort(java.lang.Short.reverseBytes(2).toInt())   // block align
        out.writeShort(java.lang.Short.reverseBytes(16).toInt())  // bits per sample
        out.writeBytes("data")
        out.writeInt(Integer.reverseBytes(bytes.size))
        out.write(bytes)
    }
}

fun whisperTranscribe(file: File, openai_api_key: String): String {
    val client = OkHttpClient()
    val body = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("model", "whisper-1")
        .addFormDataPart("language", "en")
        .addFormDataPart("response_format", "json")
        .addFormDataPart("file", "recording.wav",
            file.asRequestBody("audio/wav".toMediaType()))
        .build()
    val url = "https://api.openai.com/v1/audio/transcriptions"
    Log.d("WhisperSpeechEngine", "POST to Whisper url=$url body=$body")
    val request = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer $openai_api_key")
        .post(body)
        .build()
    Log.d("WhisperSpeechEngine", "Request: url=$url auth=Bearer ${openai_api_key.take(8)}...")
    val responseBody = client.newCall(request).execute().body?.string() ?: ""
    Log.d("WhisperSpeechEngine", "Response: $responseBody")
    return responseBody
}
