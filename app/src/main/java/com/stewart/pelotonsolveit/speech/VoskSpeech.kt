package com.stewart.pelotonsolveit.speech

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

@SuppressLint("MissingPermission")
class VoskSpeechEngine(private val context: Context) : SpeechEngine {
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
                if (recognizer.acceptWaveForm(buffer, bufferSize)) {
                    Log.d("VoskSpeechEngine", "End of utterance detected")
                    break
                }
            }
            val json = JSONObject(recognizer.result)
            return json.getString("text")
        } catch (e: SecurityException) {
            Log.e("VoskSpeechEngine", "Mic permission denied", e)
            return null
        } catch (e: Exception) {
            Log.e("VoskSpeechEngine", "Unexpected error during listen", e)
            return null
        } finally {
            audio.stop()
            audio.release()
        }
    }
}

fun loadVoskModel(context: Context): Model {
    val modelDir = File(context.filesDir, "vosk-model")
    if (!modelDir.exists()) {
        modelDir.mkdirs()
        copyAssets(context, "vosk-model-small-en-us-0.15", modelDir)
    }
    return Model(modelDir.absolutePath)
}

fun copyAssets(context: Context, srcPath: String, destDir: File, depth: Int = 0) {
    val items = context.assets.list(srcPath)
    Log.d("PelotonSolveIt", "Copying: $srcPath to $destDir")
    if (items == null || items.isEmpty()) {
        // it's a file, copy it
        context.assets.open(srcPath).use { input ->
            File(destDir, srcPath.substringAfterLast("/")).outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } else {
        // it's a folder
        val targetDir = if (depth == 0) destDir else {
            File(destDir, srcPath.substringAfterLast("/")).also { it.mkdirs() }
        }
        items.forEach { item ->
            copyAssets(context, "$srcPath/$item", targetDir, depth + 1)
        }
    }
}
