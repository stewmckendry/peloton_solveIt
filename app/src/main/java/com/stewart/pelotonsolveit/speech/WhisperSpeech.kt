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
import java.io.DataOutputStream
import java.io.File

@SuppressLint("MissingPermission")
class WhisperSpeechEngine(private val context: Context, private val openai_api_key: String) : SpeechEngine {
    private val vad = VadSilero(
        context,
        sampleRate = SampleRate.SAMPLE_RATE_16K,
        frameSize = FrameSize.FRAME_SIZE_512,
        mode = Mode.AGGRESSIVE,
        silenceDurationMs = 700,
        speechDurationMs = 150
    )
    override fun listen(): String? {
        val bufferSize = AudioRecord.getMinBufferSize(16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT)
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
            val whisperResult = whisperTranscribe(wavFile, openai_api_key)
            wavFile.delete()
            return JSONObject(whisperResult).getString("text") // process json result
        } catch (e: SecurityException) {
            Log.e("WhisperSpeechEngine", "Mic permission denied", e)
            return null
        } catch (e: Exception) {
            Log.e("WhisperSpeechEngine", "Unexpected error during listen", e)
            return null
        }  finally {
            audio.stop()
            audio.release()
            vad.close()
        }
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
        out.writeShort(Integer.reverseBytes(1).toShort().toInt())  // PCM format
        out.writeShort(Integer.reverseBytes(1).toShort().toInt())  // mono
        out.writeInt(Integer.reverseBytes(16000))     // sample rate
        out.writeInt(Integer.reverseBytes(32000))     // byte rate (16000 * 2)
        out.writeShort(Integer.reverseBytes(2).toShort().toInt())  // block align
        out.writeShort(Integer.reverseBytes(16).toShort().toInt()) // bits per sample
        out.writeBytes("data")
        out.writeInt(Integer.reverseBytes(bytes.size))
        out.write(bytes)
    }
}

fun whisperTranscribe(file: File, openai_api_key: String): String {
    val client = OkHttpClient()
    val body = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("model", "gpt-4o-mini-transcribe")
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
    val responseBody = client.newCall(request).execute().body?.string() ?: ""
    Log.d("WhisperSpeechEngine", "Response: $responseBody")
    return responseBody
}
