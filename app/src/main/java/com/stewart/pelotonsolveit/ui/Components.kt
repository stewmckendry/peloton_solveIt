package com.stewart.pelotonsolveit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stewart.pelotonsolveit.ui.theme.AccentBlue
import com.stewart.pelotonsolveit.ui.theme.ButtonSurface
import com.stewart.pelotonsolveit.ui.theme.ItemSurface
import com.stewart.pelotonsolveit.ui.theme.LabelText
import com.stewart.pelotonsolveit.ui.theme.ValueText
import java.util.Locale

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = LabelText)
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ValueText)
    }
}

@Composable
fun ItemBox(content: @Composable () -> Unit) {
    Surface(
        color = ItemSurface,  // slightly lighter than bar background
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
fun MicButton(onMicClick: () -> Unit) {
    Button(
        onClick = onMicClick,
        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
        Text("Ask solveit 🎤")
    }
}

@Composable
fun WorkoutButtons(observer: PelotonTreadObserver) {
    if( observer.workoutState == WorkoutState.IDLE) {
        Button(
            onClick = { observer.startWorkout() },
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
            Text("▶ Start Workout")
        }
    }
    else if( observer.workoutState == WorkoutState.RUNNING) {
        Row() {
            Button(
                onClick = { observer.pauseWorkout() },
                colors = ButtonDefaults.buttonColors(containerColor = ButtonSurface)) {
                Text("⏸ Pause Workout")
            }
            Button(
                onClick = { observer.stopWorkout() },
                colors = ButtonDefaults.buttonColors(containerColor = ButtonSurface)) {
                Text("⏹ Stop Workout")
            }
        }
    }
    else if( observer.workoutState == WorkoutState.PAUSED) {
        Row() {
            Button(
                onClick = { observer.resumeWorkout() },
                colors = ButtonDefaults.buttonColors(containerColor = ButtonSurface)) {
                Text("▶ Resume Workout")
            }
            Button(
                onClick = { observer.stopWorkout() },
                colors = ButtonDefaults.buttonColors(containerColor = ButtonSurface)) {
                Text("⏹ Stop Workout")
            }
        }
    }
}

@Composable
fun ElapsedTime(observer: PelotonTreadObserver) {
    val time_m = observer.elapsedSeconds / 60
    val time_s = observer.elapsedSeconds % 60
    ItemBox { StatItem(
                label = "TIME",
                value = String.format(Locale.US, "%02d:%02d", time_m, time_s)
                )
    }
}

@Composable
fun StatsSidebar(observer: PelotonTreadObserver, modifier: Modifier) {
    //val p_m = observer.pace.toInt()
    //val p_s = ((observer.pace - p_m) * 60).toInt()
    //ItemBox { StatItem(label = "PACE", value = String.format(Locale.US, "%02d:%02d", p_m, p_s), modifier=modifier) }
    //ItemBox { StatItem(label = "INCLINE", value = String.format(Locale.US, format="%.1f", observer.incline), modifier=modifier) }
    ElapsedTime(observer)
    ItemBox { StatItem(label = "DISTANCE", value = "%.2f km".format(observer.distance)) }
}
