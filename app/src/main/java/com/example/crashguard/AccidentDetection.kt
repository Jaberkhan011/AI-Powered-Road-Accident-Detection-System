package com.example.crashguard

import android.content.Context

/**
 * AccidentDetection.kt
 * Lightweight wrapper around SensorCrashDetector.
 * This file only exposes a simple API to start/stop detection and set a listener.
 * IMPORTANT: SMS sending or background service logic has been removed from this file.
 */

class AccidentDetection(
    private val context: Context,
    private val listener: AccidentListener
) {
    interface AccidentListener {
        fun onAccidentDetected(accelMag: Float, gyroDegPerSec: Float, severity: String)
    }

    private var detector: SensorCrashDetector? = null

    fun start() {
        if (detector == null) {
            detector = SensorCrashDetector(context) { a, g, s ->
                listener.onAccidentDetected(a, g, s)
            }
        }
        detector?.start()
    }

    fun stop() {
        detector?.stop()
        detector = null
    }
}