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
import com.stewart.pelotonsolveit.ui.theme.PelotonSolveItTheme
import android.webkit.WebView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.RecognizerIntent
import android.util.Log
import android.webkit.WebViewClient
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Request
import com.onepeloton.sensor.tread.TreadSensorManager
import org.json.JSONObject
import java.util.Locale

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
    val bridge = SolveItJSBridge()
    var webView: WebView? = null
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            @SuppressLint("MissingPermission")
            Thread {
                startListening(model) { transcript ->
                    Log.d("PelotonSolveIt", "Transcript: $transcript")
                    Thread {
                        try {
                            sendToSolveIt(transcript, bridge)
                        } catch (e: Exception) {
                            Log.e("PelotonSolveIt", "Error: ${e.message}", e)
                        }
                    }.start()
                }
            }.start()
        }
    }
    val observer = remember { PelotonTreadObserver() }
    val sensorManager = remember { TreadSensorManager(context, observer, null) }
    LaunchedEffect(sensorManager) {
        sensorManager.start()
    }
    PelotonSolveItTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                Row {
                    MicButton(onMicClick = {
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                        webView?.evaluateJavascript("Android.setFocusedMessage(\n" +
                                "    new URLSearchParams(window.location.search).get('name'),\n" +
                                "    document.querySelector('.editable.ring-2')?.id\n" +
                                ")\n", null)
                    })
                    WorkoutButtons(observer = observer)
                    StatsSidebar(observer = observer)
                }
            }
        ) { innerPadding ->
            SolveItWebView(
                modifier = Modifier.padding(innerPadding),
                bridge, onWebViewCreated = { wv -> webView = wv },
                onPageFinished = {
                    Thread {
                        try {
                            sendToSolveIt("Hello from Peloton!  I am starting my run session on the Peloton while reading this dialog in SolveIt - learning + running my favourite!  I will be asking sending you messages as I run!",
                                bridge)
                        } catch (e: Exception) {
                            Log.e("PelotonSolveIt", "Error: ${e.message}", e)
                        }
                    }.start()
                }
            )
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
        val json = JSONObject(recognizer.result)
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
fun SolveItWebView(modifier: Modifier = Modifier, bridge: SolveItJSBridge, onWebViewCreated: (WebView) -> Unit, onPageFinished: () -> Unit) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.javaScriptEnabled = true
                settings.builtInZoomControls = true
                loadUrl("https://solve.it.com")
                addJavascriptInterface(bridge, "Android")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        onPageFinished()
                    }
                }
            }.also { onWebViewCreated(it) }
        })
}

@Composable
fun MicButton(onMicClick: () -> Unit) {
    Button(onClick = onMicClick) {
        Text("🎤")
    }
}

@Composable
fun StatsSidebar(observer: PelotonTreadObserver) {
    val p_m = observer.pace.toInt()
    val p_s = ((observer.pace - p_m) * 60).toInt()
    Text("Pace: ${String.format(Locale.US, "%02d:%02d", p_m, p_s)} min/km")
    Text("Incline: ${"%.2f".format(Locale.US, observer.incline)} %")
    val t_m = if (observer.workoutState == WorkoutState.IDLE) 0 else observer.elapsedSeconds / 60
    val t_s = if (observer.workoutState == WorkoutState.IDLE) 0 else observer.elapsedSeconds % 60
    Text("Time: ${String.format(Locale.US, "%02d:%02d", t_m, t_s)}")
    Text("Distance: ${"%.2f".format(Locale.US, observer.distance)} km")
}

@Composable
fun WorkoutButtons(observer: PelotonTreadObserver) {
    if( observer.workoutState == WorkoutState.IDLE) {
        Button(onClick = { observer.startWorkout() }) { Text("▶ Start") }
    }
    else if( observer.workoutState == WorkoutState.RUNNING) {
        Button(onClick = { observer.pauseWorkout() }) { Text("⏸ Pause") }
        Button(onClick = { observer.stopWorkout() }) { Text("⏹ Stop") }
    }
    else if( observer.workoutState == WorkoutState.PAUSED) {
        Button(onClick = { observer.resumeWorkout() }) { Text("▶ Resume") }
        Button(onClick = { observer.stopWorkout() }) { Text("⏹ Stop") }
    }
}

fun solveItPost(path: String, params: Map<String, String>): String {
    val client = OkHttpClient()
    val bodyBuilder = FormBody.Builder()
    params.forEach { (k, v) -> bodyBuilder.add(k, v) }
    Log.d("PelotonSolveIt", "POST $path params=$params")
    val request = Request.Builder()
        .url("${BuildConfig.SOLVEIT_URL}/$path")
        .addHeader("Cookie", "_solveit=${BuildConfig.SOLVEIT_TOKEN}")
        .post(bodyBuilder.build())
        .build()
    val responseBody = client.newCall(request).execute().body?.string() ?: ""
    Log.d("PelotonSolveIt", "Response: $responseBody")
    return responseBody
}

fun sendToSolveIt(msg: String, bridge: SolveItJSBridge) {
    val dlgName = bridge.dlgName
    val msgId = bridge.msgId
    if( dlgName.isEmpty() && msgId.isEmpty() ) {
        Log.d("PelotonSolveIt", "POST to SolveIt skipped - no dlgName or msgId found")
        return
    }
    val params = mutableMapOf(
        "dlg_name" to dlgName,
        "content" to msg,
        "msg_type" to "prompt"
    )
    if (msgId.isNotEmpty()) params["id"] = msgId
    else params["placement"] = "at_end"
    val addMsgResult = solveItPost(
        "add_relative_",
        params)
    val json = JSONObject(addMsgResult)
    val newMsgId = json.getString("id")
    Log.d("PelotonSolveIt", "msgId=$msgId")
    val result = solveItPost(
        "add_runq_",
        mapOf("dlg_name" to dlgName,
            "id_" to newMsgId,
            "api" to "true"))
    Log.d("PelotonSolveIt", "result=$result")
}
