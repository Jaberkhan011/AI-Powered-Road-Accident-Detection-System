package com.example.crashguard

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.util.*
import kotlin.math.sqrt

class SensorCrashDetector(
    private val context: Context,
    private val onDetectedCallback: (Float, Float, String) -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "SensorCrashDetector"
        private const val MODEL_NAME = "accident_3class_quant.tflite"
        private const val SAMPLING_RATE_HZ = 50
        private const val SAMPLING_PERIOD_MS = 1000L / SAMPLING_RATE_HZ
        private const val WINDOW_SIZE = 100
        private const val NUM_FEATURES = 8
        
        private const val MOTION_WINDOW_SIZE = 150 
        private const val MOTION_THRESHOLD = 10.0f
        private const val CRASH_THRESHOLD = 45.0f // ~4.5Gs
        private const val VEHICLE_SENSORS_THRESHOLD = 65.0f // Threshold to favor Vehicle over Pedestrian
        private const val CONFIDENCE_THRESHOLD = 0.95f
        private const val G_FORCE_UNIT = 9.81f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var interpreter: Interpreter? = null
    
    private val accelHistory = TreeMap<Long, FloatArray>()
    private val gyroHistory = TreeMap<Long, FloatArray>()
    private val historyLimitMs = 500L

    private val windowBuffer = Array(WINDOW_SIZE) { FloatArray(NUM_FEATURES) }
    private var bufferIndex = 0
    private var isBufferFull = false

    private val motionBuffer = FloatArray(MOTION_WINDOW_SIZE)
    private var motionIndex = 0
    private var isMotionBufferFull = false

    private var detectorThread: HandlerThread? = null
    private var detectorHandler: Handler? = null
    private var nextSampleTime = 0L
    private var lastPrediction = -1

    private val samplingTask = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (nextSampleTime == 0L) {
                nextSampleTime = now
            }

            if (nextSampleTime < now - historyLimitMs) {
                nextSampleTime = now
            }

            val sample = interpolateAt(nextSampleTime)
            if (sample != null) {
                processNewSample(sample)
                if (isBufferFull) {
                    runInference()
                }
                nextSampleTime += SAMPLING_PERIOD_MS
            }
            
            detectorHandler?.postDelayed(this, SAMPLING_PERIOD_MS)
        }
    }

    fun start() {
        loadModel()
        
        detectorThread = HandlerThread("CrashDetectorThread").apply { start() }
        detectorHandler = Handler(detectorThread!!.looper)

        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_FASTEST, detectorHandler)
        sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_FASTEST, detectorHandler)
        
        nextSampleTime = 0L
        detectorHandler?.post(samplingTask)
        Log.d(TAG, "SensorCrashDetector started on dedicated thread")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        detectorHandler?.removeCallbacks(samplingTask)
        detectorThread?.quitSafely()
        detectorThread = null
        detectorHandler = null
        
        interpreter?.close()
        interpreter = null
        lastPrediction = -1
        synchronized(accelHistory) { accelHistory.clear() }
        synchronized(gyroHistory) { gyroHistory.clear() }
        Log.d(TAG, "SensorCrashDetector stopped")
    }

    private fun loadModel() {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_NAME)
            interpreter = Interpreter(modelBuffer, Interpreter.Options().setNumThreads(2))
            Log.d(TAG, "ML Model loaded: $MODEL_NAME")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ML model", e)
        }
    }

    private fun interpolateAt(time: Long): FloatArray? {
        val a1: Map.Entry<Long, FloatArray>?
        val a2: Map.Entry<Long, FloatArray>?
        val g1: Map.Entry<Long, FloatArray>?
        val g2: Map.Entry<Long, FloatArray>?

        synchronized(accelHistory) {
            a1 = accelHistory.floorEntry(time)
            a2 = accelHistory.ceilingEntry(time)
        }
        synchronized(gyroHistory) {
            g1 = gyroHistory.floorEntry(time)
            g2 = gyroHistory.ceilingEntry(time)
        }

        if (a1 == null || a2 == null || g1 == null || g2 == null) return null

        val aInterp = lerp(a1.key, a1.value, a2.key, a2.value, time)
        val gInterp = lerp(g1.key, g1.value, g2.key, g2.value, time)

        val accMag = sqrt(aInterp[0] * aInterp[0] + aInterp[1] * aInterp[1] + aInterp[2] * aInterp[2])
        val gyroMag = sqrt(gInterp[0] * gInterp[0] + gInterp[1] * gInterp[1] + gInterp[2] * gInterp[2])

        return floatArrayOf(
            aInterp[0], aInterp[1], aInterp[2],
            gInterp[0], gInterp[1], gInterp[2],
            accMag, gyroMag
        )
    }

    private fun lerp(t1: Long, v1: FloatArray, t2: Long, v2: FloatArray, target: Long): FloatArray {
        if (t1 == t2) return v1.copyOf()
        val ratio = (target - t1).toFloat() / (t2 - t1).toFloat()
        return FloatArray(v1.size) { i -> v1[i] + ratio * (v2[i] - v1[i]) }
    }

    private fun processNewSample(sample: FloatArray) {
        val accMag = sample[6]
        motionBuffer[motionIndex] = accMag
        motionIndex = (motionIndex + 1) % MOTION_WINDOW_SIZE
        if (motionIndex == 0) isMotionBufferFull = true

        windowBuffer[bufferIndex] = sample
        bufferIndex = (bufferIndex + 1) % WINDOW_SIZE
        if (bufferIndex == 0) isBufferFull = true
    }

    private fun runInference() {
        val tflite = interpreter ?: return

        if (!isMotionBufferFull) return
        val avgAccMag = motionBuffer.average().toFloat()
        if (avgAccMag < MOTION_THRESHOLD) return

        var peakAccMag = 0f
        var peakGyroMag = 0f
        for (i in 0 until WINDOW_SIZE) {
            val acc = windowBuffer[i][6]
            if (acc > peakAccMag) peakAccMag = acc
            val gyro = windowBuffer[i][7]
            if (gyro > peakGyroMag) peakGyroMag = gyro
        }
        
        if (peakAccMag < CRASH_THRESHOLD) return

        val input = prepareInput()
        val output = Array(1) { FloatArray(3) }
        tflite.run(input, output)

        val probs = output[0]
        var predictedClass = probs.indices.maxByOrNull { probs[it] } ?: 0
        val confidence = probs[predictedClass]
        Log.d(TAG, "Predicted Class: $predictedClass, Confidence: $confidence")

        // Overwrite Pedestrian Accident (2) with Vehicle Accident (1) if sensors indicate higher impact
        if ((predictedClass == 2 && confidence < CONFIDENCE_THRESHOLD) && (peakAccMag > VEHICLE_SENSORS_THRESHOLD || peakGyroMag > 8.0f)) {
            Log.i(TAG, "Overwriting Class 2 with Class 1 due to high sensor values (Acc: $peakAccMag, Gyro: $peakGyroMag)")
            predictedClass = 1
        }

        // 0: Normal, 1: Vehicle Accident, 2: Pedestrian Accident
        val isAccident = (predictedClass == 1 || predictedClass == 2)

        if (!isAccident || confidence < CONFIDENCE_THRESHOLD) {
            lastPrediction = predictedClass
            return
        }

        Log.i(TAG, "Hit: Accident Detected (Class: $predictedClass, Conf: $confidence)")
        
        // Confirmation logic: check if the previous sample was also an accident
        val wasLastAccident = (lastPrediction == 1 || lastPrediction == 2)
        
        if (wasLastAccident) {
            Log.i(TAG, "!!! CRASH CONFIRMED (Class: $predictedClass) !!!")
            val peakGyroDeg = Math.toDegrees(peakGyroMag.toDouble()).toFloat()
            
            val accidentTypeRes = if (predictedClass == 1) R.string.accident_type_vehicle else R.string.accident_type_pedestrian
            val accidentType = context.getString(accidentTypeRes)
            val baseSeverity = classifySeverity(peakAccMag, peakGyroDeg)
            val fullSeverity = "$accidentType ($baseSeverity)"
            
            // Post back to main thread for UI interaction
            Handler(Looper.getMainLooper()).post {
                onDetectedCallback(peakAccMag, peakGyroDeg, fullSeverity)
            }
            lastPrediction = -1 
        } else {
            lastPrediction = predictedClass
        }
    }

    private fun classifySeverity(peakAcc: Float, peakGyroDeg: Float): String {
        val gForce = peakAcc / G_FORCE_UNIT
        val resId = when {
            gForce > 10f || peakGyroDeg > 800f -> R.string.severity_high_magnitude
            gForce > 5f || peakGyroDeg > 300f -> R.string.severity_medium_magnitude
            else -> R.string.severity_low_magnitude
        }
        return context.getString(resId)
    }

    private fun prepareInput(): Array<Array<FloatArray>> {
        val orderedWindow = Array(WINDOW_SIZE) { FloatArray(NUM_FEATURES) }
        for (i in 0 until WINDOW_SIZE) {
            val idx = (bufferIndex + i) % WINDOW_SIZE
            orderedWindow[i] = windowBuffer[idx].copyOf()
        }

        for (feat in 0 until NUM_FEATURES) {
            val vals = FloatArray(WINDOW_SIZE) { i -> orderedWindow[i][feat] }
            val mean = vals.average().toFloat()
            var sqSum = 0f
            for (v in vals) sqSum += (v - mean) * (v - mean)
            val std = sqrt(sqSum / WINDOW_SIZE).coerceAtLeast(1e-6f)

            for (t in 0 until WINDOW_SIZE) {
                orderedWindow[t][feat] = (orderedWindow[t][feat] - mean) / std
            }
        }
        return arrayOf(orderedWindow)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = System.currentTimeMillis()
        val history = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) accelHistory else gyroHistory
        synchronized(history) {
            history[now] = event.values.copyOf()
            while (history.isNotEmpty() && history.firstKey() < now - historyLimitMs) {
                history.pollFirstEntry()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}