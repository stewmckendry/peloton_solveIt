package com.stewart.pelotonsolveit.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.json.JSONObject
import org.vosk.Recognizer

@SuppressLint("MissingPermission")
class WhisperSpeechEngine(private val context: Context) : SpeechEngine {
    private val model = loadVoskModel(context)
    override fun listen(): String? {
        val recognizer = Recognizer(model, 16000f)
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
            while (audio.read(buffer, 0, bufferSize) > 0) {
                if (recognizer.acceptWaveForm(buffer, bufferSize)) break
            }
            val json = JSONObject(recognizer.result)
            return json.getString("text")
        } catch (e: SecurityException) {
            Log.d("PelotonSolveIt", "Mic permission denied: $e")
            return null
        } finally {
            audio.stop()
            audio.release()
        }
    }
}
