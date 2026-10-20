package com.example.crashguard

import android.Manifest
import android.app.*
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.crashguard.addcontact.ContactsHelper
import com.example.crashguard.alerthistory.AlertHistoryHelper
import com.example.crashguard.widgets.ControlCenterWidget
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.sqrt
import kotlin.math.roundToInt

class SafetyService : Service() {

    companion object {
        private const val TAG = "SafetyService"
        private const val CHANNEL_ID = "SafetyServiceChannel"
        private const val ALERT_CHANNEL_ID = "SafetyAlertChannel"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002

        const val ACTION_START_VOICE = "ACTION_START_VOICE"
        const val ACTION_STOP_VOICE = "ACTION_STOP_VOICE"
        const val ACTION_START_CRASH = "ACTION_START_CRASH"
        const val ACTION_STOP_CRASH = "ACTION_STOP_CRASH"
        const val ACTION_STOP_ALL = "ACTION_STOP_ALL"
        const val ACTION_MANUAL_SOS = "ACTION_MANUAL_SOS"
        const val ACTION_SEND_CONFIRMED_SMS = "ACTION_SEND_CONFIRMED_SMS"

        const val ACTION_ACCIDENT_DETECTED = "com.example.crashguard.ACCIDENT_DETECTED"
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_SEVERITY = "extra_severity"

        var isCrashDetectionRunning = false
            private set
        var isVoiceDetectionRunning = false
            private set

        private const val VOICE_MODEL_NAME = "wake_word_model_three_float.tflite"
        private const val SAMPLE_RATE = 8000
        private const val WINDOW_SAMPLES = 8000
        private const val STEP_SAMPLES = 2000
        private const val MFCC_COEFFS = 13
        private const val MFCC_FRAMES = 98
        private const val VOICE_THRESHOLD = 0.70f
    }

    private var voiceInterpreter: Interpreter? = null
    private var audioRecord: AudioRecord? = null
    private val voiceExecutor = Executors.newSingleThreadExecutor()
    private val slidingAudioBuffer = FloatArray(WINDOW_SAMPLES)
    private val mfccExtractor = MfccExtractor(sampleRate = SAMPLE_RATE, numMfccs = MFCC_COEFFS)
    private var lastVoiceDetectionTime = 0L

    private var crashDetector: SensorCrashDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val noiseFloorBuffer = mutableListOf<Float>()
    private var adaptiveRmsThreshold = 0.005f
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null
    private var isQuantizedInput = false
    private var inputScale = 1f
    private var inputZeroPoint = 0
    private var isQuantizedOutput = false
    private var outputScale = 1f
    private var outputZeroPoint = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannels()
        loadVoiceModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand Action: $action")

        if (action == null) {
            // Service restarted by system
            if (AuthManager.isCrashEnabled(this)) startCrashDetection()
            if (AuthManager.isVoiceEnabled(this)) startVoiceDetection()
        } else {
            when (action) {
                ACTION_START_VOICE -> {
                    AuthManager.setVoiceEnabled(this, true)
                    startVoiceDetection()
                }
                ACTION_STOP_VOICE -> {
                    AuthManager.setVoiceEnabled(this, false)
                    stopVoiceDetection()
                }
                ACTION_START_CRASH -> {
                    AuthManager.setCrashEnabled(this, true)
                    startCrashDetection()
                }
                ACTION_STOP_CRASH -> {
                    AuthManager.setCrashEnabled(this, false)
                    stopCrashDetection()
                }
                ACTION_STOP_ALL -> {
                    AuthManager.setVoiceEnabled(this, false)
                    AuthManager.setCrashEnabled(this, false)
                    stopVoiceDetection()
                    stopCrashDetection()
                    stopSelf()
                    return START_NOT_STICKY
                }
                ACTION_MANUAL_SOS -> triggerEmergencyAlert("Manual SOS")
                ACTION_SEND_CONFIRMED_SMS -> {
                    val source = intent.getStringExtra(EXTRA_SOURCE) ?: "Confirmed Alert"
                    val severity = intent.getStringExtra(EXTRA_SEVERITY)
                    triggerEmergencyAlert(source, severity)
                }
            }
        }
        
        updateNotification()
        updateWidgets()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved - Restarting Service")
        // Try to keep it alive on swipe away for some devices
        val restartServiceIntent = Intent(applicationContext, this.javaClass).apply {
            setPackage(packageName)
        }
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    private fun loadVoiceModel() {
        try {
            val buffer = FileUtil.loadMappedFile(this, VOICE_MODEL_NAME)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
                setUseXNNPACK(true)
            }
            voiceInterpreter = Interpreter(buffer, options).also { configureInterpreter(it) }
            Log.d(TAG, "Voice model loaded successfully; quantInput=$isQuantizedInput")
        } catch (e: Exception) {
            Log.e(TAG, "Voice model load failed: ${e.message}")
        }
    }

    private fun configureInterpreter(interpreter: Interpreter) {
        val inputTensor = interpreter.getInputTensor(0)
        val inputElements = inputTensor.shape().fold(1) { acc, dim -> acc * dim }
        isQuantizedInput = when (inputTensor.dataType()) {
            DataType.INT8, DataType.UINT8 -> true
            else -> false
        }
        val inputBytes = inputElements * if (isQuantizedInput) 1 else 4
        inputBuffer = ByteBuffer.allocateDirect(inputBytes).order(ByteOrder.nativeOrder())
        if (isQuantizedInput) {
            val params = inputTensor.quantizationParams()
            inputScale = params.scale
            inputZeroPoint = params.zeroPoint
        }

        val outputTensor = interpreter.getOutputTensor(0)
        val outputElements = outputTensor.shape().fold(1) { acc, dim -> acc * dim }
        isQuantizedOutput = when (outputTensor.dataType()) {
            DataType.INT8, DataType.UINT8 -> true
            else -> false
        }
        val outputBytes = outputElements * if (isQuantizedOutput) 1 else 4
        outputBuffer = ByteBuffer.allocateDirect(outputBytes).order(ByteOrder.nativeOrder())
        if (isQuantizedOutput) {
            val params = outputTensor.quantizationParams()
            outputScale = params.scale
            outputZeroPoint = params.zeroPoint
        }
    }

    private fun startVoiceDetection() {
        if (isVoiceDetectionRunning) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize.coerceAtLeast(WINDOW_SAMPLES * 2))

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord state not initialized")
                return
            }

            audioRecord?.startRecording()
            isVoiceDetectionRunning = true
            acquireWakeLock()
            loadVoiceModel()
            Log.d(TAG, "Voice detection started")

            voiceExecutor.execute {
                val readBuffer = ShortArray(STEP_SAMPLES)
                while (isVoiceDetectionRunning) {
                    val read = audioRecord?.read(readBuffer, 0, STEP_SAMPLES) ?: 0
                    if (read > 0) {
                        System.arraycopy(slidingAudioBuffer, read, slidingAudioBuffer, 0, WINDOW_SAMPLES - read)
                        val inv = 1f / 32768.0f
                        for (i in 0 until read) {
                            slidingAudioBuffer[WINDOW_SAMPLES - read + i] = readBuffer[i] * inv
                        }
                        val rms = calculateAdaptiveRMS(readBuffer, read)
                        if (rms < adaptiveRmsThreshold) continue
                        processVoiceInference(slidingAudioBuffer)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice detection: ${e.message}")
            isVoiceDetectionRunning = false
        }
    }

    private fun calculateAdaptiveRMS(buffer: ShortArray, count: Int): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            val sample = buffer[i].toDouble() / 32768.0
            sum += sample * sample
        }
        val rms = sqrt(sum / count).toFloat()
        noiseFloorBuffer.add(rms)
        if (noiseFloorBuffer.size > 40) noiseFloorBuffer.removeAt(0)
        val floor = noiseFloorBuffer.average().toFloat()
        adaptiveRmsThreshold = (floor * 2.0f).coerceAtLeast(0.005f)
        return rms
    }

    private fun processVoiceInference(buffer: FloatArray) {
        if (voiceInterpreter == null) return
        val mfccs = mfccExtractor.extract(buffer, MFCC_FRAMES)
        val prob = runVoiceInference(mfccs)
        Log.d(TAG, "Voice prob: $prob")
        if (prob > VOICE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastVoiceDetectionTime > 5000) {
                lastVoiceDetectionTime = now
                Log.i(TAG, "WAKE WORD DETECTED! Prob: $prob")
                triggerEmergencyAlert("Voice Activation")
            }
        }
    }

    private fun runVoiceInference(mfccs: FloatArray): Float {
        val interpreter = voiceInterpreter ?: return 0f
        val inBuffer = inputBuffer ?: return 0f
        val outBuffer = outputBuffer ?: return 0f

        inBuffer.clear()
        if (isQuantizedInput) {
            val scale = if (inputScale == 0f) 1e-6f else inputScale
            for (value in mfccs) {
                val quantized = ((value / scale) + inputZeroPoint).roundToInt().coerceIn(-128, 127)
                inBuffer.put(quantized.toByte())
            }
        } else {
            for (value in mfccs) inBuffer.putFloat(value)
        }
        inBuffer.rewind()

        outBuffer.clear()
        interpreter.run(inBuffer, outBuffer)
        outBuffer.rewind()

        return if (isQuantizedOutput) {
            val raw = outBuffer.get().toInt()
            (raw - outputZeroPoint) * outputScale
        } else {
            outBuffer.getFloat()
        }
    }

    private fun stopVoiceDetection() {
        isVoiceDetectionRunning = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        checkReleaseWakeLock()
    }

    private fun startCrashDetection() {
        if (isCrashDetectionRunning) return
        isCrashDetectionRunning = true
        acquireWakeLock()
        crashDetector = SensorCrashDetector(this) { peak, gyro, severity ->
            Log.i(TAG, "CRASH DETECTED! Severity: $severity, Peak: $peak, Gyro: $gyro")
            triggerAccidentDialog(peak, gyro, severity, "Automatic Crash Detection")
        }
        crashDetector?.start()
    }

    private fun stopCrashDetection() {
        isCrashDetectionRunning = false
        crashDetector?.stop()
        crashDetector = null
        checkReleaseWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CrashGuard:SafetyWakeLock")
            wakeLock?.acquire(10*60*1000L /*10 minutes*/)
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun checkReleaseWakeLock() {
        if (!isCrashDetectionRunning && !isVoiceDetectionRunning) {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
            wakeLock = null
        }
    }

    private fun triggerAccidentDialog(accel: Float, gyro: Float, severity: String, source: String) {
        val emergencyIntent = Intent(this, EmergencyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("accel", accel)
            putExtra("gyro", gyro)
            putExtra(EXTRA_SEVERITY, severity)
            putExtra(EXTRA_SOURCE, source)
        }

        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (hasOverlayPermission) {
            try {
                startActivity(emergencyIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Direct activity start failed: ${e.message}")
                showFallbackNotification(emergencyIntent)
            }
        } else {
            showFallbackNotification(emergencyIntent)
        }

        val broadcastIntent = Intent(ACTION_ACCIDENT_DETECTED).apply {
            putExtra("accel", accel)
            putExtra("gyro", gyro)
            putExtra(EXTRA_SEVERITY, severity)
            putExtra(EXTRA_SOURCE, source)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun showFallbackNotification(intent: Intent) {
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Emergency Detected!")
            .setContentText("A potential accident was detected.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun triggerEmergencyAlert(source: String, severity: String? = null) {
        fetchLocation { location ->
            val isFresh = location != null && (System.currentTimeMillis() - location.time) < 60000
            if (isFresh) {
                sendSmsAlert(source, location, severity)
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    fetchLocation { freshLoc -> sendSmsAlert(source, freshLoc, severity) }
                }, 2500)
            }
        }
    }

    private fun fetchLocation(callback: (Location?) -> Unit) {
        if (isInternetAvailable()) {
            val fused = LocationServices.getFusedLocationProviderClient(this)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc -> callback(loc) }
                    .addOnFailureListener { fallbackLocation(callback) }
                return
            }
        }
        fallbackLocation(callback)
    }

    private fun fallbackLocation(callback: (Location?) -> Unit) {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val best = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            callback(best)
        } catch (e: SecurityException) {
            callback(null)
        }
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun sendSmsAlert(source: String, location: Location?, severity: String? = null) {
        val phones = ContactsHelper.getSelectedPhones(this)
        if (phones.isEmpty()) return
        
        val userName = AuthManager.getUsername(this) ?: "a user"
        val msg = SmsHelper.makeUrgentAlert(userName, location?.latitude, location?.longitude, severity, source)
        
        phones.forEach { SmsHelper.send(this, it, msg) }
        
        val type = when {
            source.contains("Voice") -> "V.A.A"
            source.contains("Manual") -> "Manual"
            else -> "Automatic"
        }
        AlertHistoryHelper.saveAlert(this, type, if (location != null) "https://maps.google.com/?q=${location.latitude},${location.longitude}" else "Unknown")
    }

    private fun updateNotification() {
        val voiceIntent = Intent(this, SafetyService::class.java).apply {
            action = if (isVoiceDetectionRunning) ACTION_STOP_VOICE else ACTION_START_VOICE
        }
        val voicePendingIntent = PendingIntent.getService(this, 1, voiceIntent, PendingIntent.FLAG_IMMUTABLE)

        val crashIntent = Intent(this, SafetyService::class.java).apply {
            action = if (isCrashDetectionRunning) ACTION_STOP_CRASH else ACTION_START_CRASH
        }
        val crashPendingIntent = PendingIntent.getService(this, 2, crashIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopAllIntent = Intent(this, SafetyService::class.java).apply { action = ACTION_STOP_ALL }
        val stopAllPendingIntent = PendingIntent.getService(this, 3, stopAllIntent, PendingIntent.FLAG_IMMUTABLE)

        val statusText = when {
            isVoiceDetectionRunning && isCrashDetectionRunning -> "Voice & Crash Detection Active"
            isVoiceDetectionRunning -> "Voice Detection Active"
            isCrashDetectionRunning -> "Crash Detection Active"
            else -> "Safety System Standing By"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CrashGuard Protection")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(0, if (isVoiceDetectionRunning) "Stop Voice" else "Start Voice", voicePendingIntent)
            .addAction(0, if (isCrashDetectionRunning) "Stop Crash" else "Start Crash", crashPendingIntent)
            .addAction(0, "Stop All", stopAllPendingIntent)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateWidgets() {
        val intent = Intent(this, ControlCenterWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val ids = AppWidgetManager.getInstance(application)
            .getAppWidgetIds(ComponentName(application, ControlCenterWidget::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Safety Service", NotificationManager.IMPORTANCE_LOW))

            val alertChannel = NotificationChannel(ALERT_CHANNEL_ID, "Safety Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Channel for crash detection alerts"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        updateWidgets()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}