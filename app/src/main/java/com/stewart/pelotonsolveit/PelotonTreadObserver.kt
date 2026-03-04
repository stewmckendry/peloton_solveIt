package com.stewart.pelotonsolveit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.onepeloton.sensor.tread.TreadSensorObserver
import com.onepeloton.sensor.tread.TreadSensorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class PelotonTreadObserver : TreadSensorObserver {
    var speed by mutableStateOf(0.0)
    var incline by mutableStateOf(0.0)
    var distance by mutableStateOf(0.0)
    var workoutState by mutableStateOf(WorkoutState.IDLE)
    var lastUpdateTime = System.currentTimeMillis()
    var elapsedSeconds by mutableStateOf(0)
    var time_dlt by mutableStateOf(0)
    override fun onSensorDataUpdated(data: TreadSensorData) {
        System.currentTimeMillis()
        speed = data.getCurrentSpeed()
        incline = data.getCurrentIncline()
        val currentTime = System.currentTimeMillis()
        time_dlt = currentTime - lastUpdateTime
        lastUpdateTime = currentTime
        if(WorkoutState.RUNNING == true)
        distance += speed * time_dlt / 3600000
    }
    val scope = CoroutineScope(Dispatchers.Default)
    init { scope.launch {
        while (true) {
            delay(1000L)
            if (workoutState == WorkoutState.RUNNING) {
                elapsedSeconds++
            }
        }
    }
    }
    override fun onConnected() {}
    override fun onConnecting() {}
    override fun onConnectionError() {}
    override fun onDisconnected() {}
}