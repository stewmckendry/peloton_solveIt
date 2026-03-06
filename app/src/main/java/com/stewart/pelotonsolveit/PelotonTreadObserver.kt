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

enum class WorkoutState { IDLE, RUNNING, PAUSED }
const val MPH_TO_KMH = 1.60934
const val MS_PER_HOUR = 3_600_000.0

class PelotonTreadObserver : TreadSensorObserver {
    var speed by mutableStateOf(0.0)
    var incline by mutableStateOf(0.0)
    var distance by mutableStateOf(0.0)
    var pace by mutableStateOf(value = 0.0)
    var workoutState by mutableStateOf(WorkoutState.IDLE)
    var lastUpdateTime = System.currentTimeMillis()
    var elapsedSeconds by mutableStateOf(0)
    var time_dlt = 0L
    override fun onSensorDataUpdated(data: TreadSensorData) {
        System.currentTimeMillis()
        speed = data.getCurrentSpeed()
        incline = data.getCurrentIncline()
        speedToPace()
        val currentTime = System.currentTimeMillis()
        time_dlt = currentTime - lastUpdateTime
        lastUpdateTime = currentTime
        if(workoutState == WorkoutState.RUNNING) {
            distance += speed * MPH_TO_KMH * time_dlt / MS_PER_HOUR
        }
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

    fun speedToPace() {
        if (speed > 0.0) {
            pace = 60 / (speed * 1.60934)
        }
    }
    fun startWorkout() {
        elapsedSeconds = 0
        distance = 0.0
        workoutState = WorkoutState.RUNNING
        lastUpdateTime = System.currentTimeMillis()
    }

    fun pauseWorkout() {
        workoutState = WorkoutState.PAUSED
    }

    fun resumeWorkout() {
        workoutState = WorkoutState.RUNNING
        lastUpdateTime = System.currentTimeMillis()
    }

    fun stopWorkout() {
        workoutState = WorkoutState.IDLE
    }
}

