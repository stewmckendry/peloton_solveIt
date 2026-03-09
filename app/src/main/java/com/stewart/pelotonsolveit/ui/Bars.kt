package com.stewart.pelotonsolveit

import android.Manifest
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stewart.pelotonsolveit.ui.theme.BarBackground

@Composable
fun TopBar(observer: PelotonTreadObserver,
           webView: WebView?,
           micOn: Boolean,
           onMicClick: () -> Unit,
           isDarkMode: Boolean,
           onToggleDarkMode: () -> Unit,
           isWhisper: Boolean,
           onToggleSpeechMode: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = BarBackground) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.weight(1f))
            SpeechModeButton(isWhisper, onToggleSpeechMode = onToggleSpeechMode)
            MicButton(micOn, onMicClick = onMicClick)
            WorkoutButtons(observer = observer)
            BrightButton(isDarkMode, onToggleDarkMode = onToggleDarkMode)
        }
    }
}

@Composable
fun BottomBar(observer: PelotonTreadObserver) {
    Surface(modifier = Modifier.fillMaxWidth(), color = BarBackground) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            StatsLeft(observer = observer)
            Spacer(modifier = Modifier.weight(1f))
            StatsRight(observer = observer)
        }
    }
}