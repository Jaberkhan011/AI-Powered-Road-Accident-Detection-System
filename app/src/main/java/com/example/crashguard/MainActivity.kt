package com.example.crashguard

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.crashguard.fragments.AddContact
import com.example.crashguard.fragments.AlertHistory
import com.example.crashguard.fragments.Home
import com.example.crashguard.fragments.MoreOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private val REQ_PERMS = 101

    private val overlayResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val REQUIRED_PERMS = mutableListOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMS, REQ_PERMS)
        }

        checkOverlayPermission()

        supportActionBar?.hide()

        val sosButton: FloatingActionButton = findViewById(R.id.SOSButton)
        val bottomNav: BottomNavigationView = findViewById(R.id.bottomNav)

        bottomNav.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null
            when (item.itemId) {
                R.id.Home -> selectedFragment = Home()
                R.id.AddContacts -> selectedFragment = AddContact()
                R.id.AlertHistory -> selectedFragment = AlertHistory()
                R.id.MoreOptions -> selectedFragment = MoreOptions()
            }
            if (selectedFragment != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.frameLayout, selectedFragment)
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .commit()
            }
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.Home
        }

        sosButton.setOnLongClickListener {
            sendManualAlert()
            true
        }
        
        sosButton.setOnClickListener {
            Toast.makeText(this, "Long press for SOS", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasPermissions(): Boolean {
        return REQUIRED_PERMS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please enable 'Display over other apps' for direct alerts", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayResultLauncher.launch(intent)
            }
        }
    }

    private fun sendManualAlert() {
        val intent = Intent(this, SafetyService::class.java).apply {
            action = SafetyService.ACTION_MANUAL_SOS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    fun startDetection() {
        val intent = Intent(this, SafetyService::class.java).apply {
            action = SafetyService.ACTION_START_CRASH
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    fun stopDetection() {
        val intent = Intent(this, SafetyService::class.java).apply {
            action = SafetyService.ACTION_STOP_CRASH
        }
        startService(intent)
    }
}
