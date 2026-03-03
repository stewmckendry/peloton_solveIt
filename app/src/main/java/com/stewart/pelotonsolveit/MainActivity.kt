package com.stewart.pelotonsolveit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.stewart.pelotonsolveit.ui.theme.PelotonSolveItTheme
import android.webkit.WebView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.unit.dp
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.ui.platform.LocalContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.nio.ByteBuffer
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Request
import com.stewart.pelotonsolveit.BuildConfig

class MainActivity : ComponentActivity() {
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val transcript = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (transcript != null) Log.d("PelotonSolveIt", "Transcript: $transcript")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PelotonSolveItApp()
        }
    }
}

@Composable
fun PelotonSolveItApp() {
    val context = LocalContext.current
    val model = loadVoskModel(context)
    Thread {
        try {
            sendToSolveIt("hello from Peloton!  App started and called SolveIt!")
        } catch (e: Exception) {
            Log.e("PelotonSolveIt", "Error: ${e.message}", e)
        }
    }.start()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            @SuppressLint("MissingPermission")
            startListening(model) { transcript ->
                Log.d("PelotonSolveIt", "Transcript: $transcript")
                Thread {
                    try {
                        sendToSolveIt(transcript)
                    } catch (e: Exception) {
                        Log.e("PelotonSolveIt", "Error: ${e.message}", e)
                    }
                }.start()
            }
        }
    }
    PelotonSolveItTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Row {
                SolveItWebView(modifier = Modifier.weight(0.85f).padding(innerPadding))
                Column(modifier = Modifier.weight(0.15f).background(Red)){
                    MicButton(onMicClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) })
                    StatsSidebar()
                }
            }
        }
    }
}

@RequiresPermission(Manifest.permission.RECORD_AUDIO)
@SuppressLint("MissingPermission")
fun startListening(model: Model, onResult: (String) -> Unit) {
    val recognizer = Recognizer(model, 16000f)
    val bufferSize = AudioRecord.getMinBufferSize(16000,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT)
    try {
        val audio = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        val buffer = ByteArray(bufferSize)
        audio.startRecording()
        while (audio.read(buffer, 0, bufferSize) > 0) {
            if (recognizer.acceptWaveForm(buffer, bufferSize)) break
        }
        val json = org.json.JSONObject(recognizer.result)
        val text = json.getString("text")
        onResult(text)
    } catch (e: SecurityException) {
    Log.d("PelotonSolveIt", "Mic permission denied: $e")
    return
    }
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


fun loadVoskModel(context: Context): Model {
    val modelDir = File(context.filesDir, "vosk-model")
    if (!modelDir.exists()) {
        modelDir.mkdirs()
        copyAssets(context, "vosk-model-small-en-us-0.15", modelDir)
    }
    return Model(modelDir.absolutePath)
}

@Composable
fun SolveItWebView(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.javaScriptEnabled = true
                settings.builtInZoomControls = true
                loadUrl("https://solve.it.com")
            }
        })
}

@Composable
fun MicButton(onMicClick: () -> Unit) {
    Button(onClick = onMicClick) {
        Text("🎤")
    }
}

@Composable
fun StatsSidebar() {
    // call API for polling stats
    // render data in tiles with stats on duration, distance, pace, elevation
}

fun solveItPost(path: String, params: Map<String, String>): String {
    val client = OkHttpClient()
    val bodyBuilder = FormBody.Builder()
    params.forEach { (k, v) -> bodyBuilder.add(k, v) }
    val request = Request.Builder()
        .url("${BuildConfig.SOLVEIT_URL}/$path")
        .addHeader("Cookie", "_solveit=${BuildConfig.SOLVEIT_TOKEN}")
        .post(bodyBuilder.build())
        .build()
    return client.newCall(request).execute().body?.string() ?: ""
}

fun sendToSolveIt(msg: String) {
    val msgId = solveItPost("add_relative_", mapOf("dlg_name" to BuildConfig.SOLVEIT_DIALOG, "content" to msg, "msg_type" to "prompt"))
    val result = solveItPost("add_runq_", mapOf("dlg_name" to BuildConfig.SOLVEIT_DIALOG, "id_" to msgId, "api" to "true"))
}
