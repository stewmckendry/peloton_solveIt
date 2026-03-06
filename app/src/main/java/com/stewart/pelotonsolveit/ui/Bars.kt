package com.stewart.pelotonsolveit

import android.Manifest
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stewart.pelotonsolveit.ui.theme.BarBackground

@Composable
fun TopBar(observer: PelotonTreadObserver, webView: WebView?, onMicClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = BarBackground) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.weight(1f))
            MicButton(onMicClick = onMicClick)
            WorkoutButtons(observer = observer)
        }
    }
}

@Composable
fun BottomBar(observer: PelotonTreadObserver) {
    Surface(modifier = Modifier.fillMaxWidth(), color = BarBackground) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsSidebar(observer = observer, modifier = Modifier.weight(1f))
        }
    }
}