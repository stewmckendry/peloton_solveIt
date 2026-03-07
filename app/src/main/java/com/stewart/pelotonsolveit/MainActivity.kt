package com.stewart.pelotonsolveit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stewart.pelotonsolveit.ui.theme.PelotonSolveItTheme
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.speech.RecognizerIntent
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.vosk.Model
import org.vosk.Recognizer
import com.onepeloton.sensor.tread.TreadSensorManager
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val transcript = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (transcript != null) Log.d("PelotonSolveIt", "Transcript: $transcript")
        }
    }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PelotonSolveItApp()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun PelotonSolveItApp() {
    val context = LocalContext.current
    val model = loadVoskModel(context)
    val bridge = SolveItJSBridge()
    var webView: WebView? = null
    var isDarkMode by remember { mutableStateOf(true) }
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
            topBar = {
                TopBar(observer, webView, onMicClick = {
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                    webView?.evaluateJavascript(
                        "Android.setFocusedMessage(\n" +
                                "    new URLSearchParams(window.location.search).get('name'),\n" +
                                "    document.querySelector('.editable.ring-2')?.id\n" +
                                ")\n", null) },
                    isDarkMode, onToggleDarkMode = {
                        if (isDarkMode)
                            webView?.evaluateJavascript("document.documentElement.classList.remove('dark')", null)
                        else
                            webView?.evaluateJavascript("document.documentElement.classList.add('dark')", null)
                        isDarkMode = !isDarkMode
                    })
            },
            bottomBar = {
                BottomBar(observer)
            }
        ) { innerPadding ->
            SolveItWebView(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                bridge, onWebViewCreated = { wv -> webView = wv },
                onPageFinished = {
                    Thread {
                        try {
                            Thread.sleep(3000L)
                            val greeting = """
                                Hello from Peloton! I'm starting a run session and will be reading and interacting with this dialog hands-free while I run. 
                                I'll be sending messages by voice, so please keep responses concise and easy to read.
                                I can't write or run code myself right now, but I love seeing it and understanding how it works. 
                                If it would help to add a note or code cell to the dialog, please go ahead and do so — I can only send prompt messages by voice, so I'm relying on you to add and run code or notes on my behalf where it makes sense.
                                I learn best in small steps — please explain things clearly, check my understanding often, and don't move on until I confirm I've got it!"
                            """.trimIndent()
                            sendToSolveIt(greeting, bridge, pin = true, placement = "at_end")
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
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
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
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.allowContentAccess = true
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
                addJavascriptInterface(bridge, "Android")
                loadUrl("https://solve.it.com")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript("document.getElementById('dialog-container').style.height = '100%';\n" +
                                "document.getElementById('dialog-container').style.minHeight = window.innerHeight + 'px';\n", null)
                        view.postDelayed({
                            view.evaluateJavascript("Android.setFocusedMessage(\n" +
                                    "    new URLSearchParams(window.location.search).get('name'),\n" +
                                    "    document.querySelector('.editable.ring-2')?.id\n" +
                                    ")\n", null)
                            view.evaluateJavascript("document.documentElement.classList.add('dark')", null)
                            onPageFinished()
                        }, 500)
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        Log.e("PelotonSolveIt", "WebView error: ${error.description} for ${request.url} (is WS: ${request.url.toString().startsWith("ws")})")
                    }
                }
            }.also { onWebViewCreated(it) }
        })
}


