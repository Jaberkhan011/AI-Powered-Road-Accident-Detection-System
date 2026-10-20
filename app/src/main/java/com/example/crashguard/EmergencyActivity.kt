package com.example.crashguard

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator

class EmergencyActivity : AppCompatActivity() {

    private var activeCountdown: CountDownTimer? = null
    private lateinit var timerProgress: CircularProgressIndicator
    private lateinit var tvCountdown: TextView
    private lateinit var tvDetails: TextView
    private lateinit var btnSendNow: MaterialButton
    private lateinit var btnCancel: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure activity shows over lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_emergency)

        timerProgress = findViewById(R.id.timerProgress)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvDetails = findViewById(R.id.tvDetails)
        btnSendNow = findViewById(R.id.btnSendNow)
        btnCancel = findViewById(R.id.btnCancel)

        val accel = intent.getFloatExtra("accel", 0f)
        val gyro = intent.getFloatExtra("gyro", 0f)
        val severity = intent.getStringExtra(SafetyService.EXTRA_SEVERITY)
        val source = intent.getStringExtra(SafetyService.EXTRA_SOURCE) ?: "Emergency Alert"

        setupUI(accel, gyro, severity, source)
    }

    private fun setupUI(accel: Float, gyro: Float, severity: String?, source: String) {
        val severityLabel = if (severity != null) getString(R.string.severity_label, severity) else ""
        tvDetails.text = getString(R.string.emergency_details_format, source, severityLabel, accel, gyro)
        
        val isPedestrian = severity?.contains("Pedestrian", ignoreCase = true) == true
        val countdownTimeMs = if (isPedestrian) 30_000L else 15_000L
        
        timerProgress.max = countdownTimeMs.toInt()
        timerProgress.progress = countdownTimeMs.toInt()

        btnSendNow.setOnClickListener {
            confirmAlert(source, severity)
        }

        btnCancel.setOnClickListener {
            cancelAlert()
        }

        activeCountdown = object : CountDownTimer(countdownTimeMs, 50) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished + 999) / 1000
                tvCountdown.text = secondsLeft.toString()
                timerProgress.progress = millisUntilFinished.toInt()
            }

            override fun onFinish() {
                tvCountdown.text = "0"
                timerProgress.progress = 0
                confirmAlert(source, severity)
            }
        }.start()
    }

    private fun confirmAlert(source: String, severity: String?) {
        activeCountdown?.cancel()
        val intent = Intent(this, SafetyService::class.java).apply {
            action = SafetyService.ACTION_SEND_CONFIRMED_SMS
            putExtra(SafetyService.EXTRA_SOURCE, source)
            if (severity != null) {
                putExtra(SafetyService.EXTRA_SEVERITY, severity)
            }
        }
        startService(intent)
        finish()
    }

    private fun cancelAlert() {
        activeCountdown?.cancel()
        finish()
    }

    override fun onDestroy() {
        activeCountdown?.cancel()
        super.onDestroy()
    }
}
