package com.example.crashguard.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.crashguard.AuthManager
import com.example.crashguard.MainActivity
import com.example.crashguard.SafetyService
import com.example.crashguard.addcontact.ContactsHelper
import com.example.crashguard.R

class Home : Fragment() {

    private lateinit var tvUserName: TextView
    private lateinit var tvUserPhone: TextView
    private lateinit var btnLoadContacts: Button
//    private lateinit var btnManualSOS: Button
    private lateinit var switchCrashDetect: SwitchCompat
    private lateinit var tvCrashStatus: TextView
    private lateinit var voiceAlertActivation: SwitchCompat
    private lateinit var tvVoiceStatus: TextView

    val REQ = 101

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        tvUserName = view.findViewById(R.id.tvUserName)
        tvUserPhone = view.findViewById(R.id.tvUserPhone)
        btnLoadContacts = view.findViewById(R.id.btnLoadContacts)
//        btnManualSOS = view.findViewById(R.id.btnManualSOS)
        switchCrashDetect = view.findViewById(R.id.switchCrashDetect)
        tvCrashStatus = view.findViewById(R.id.tvCrashStatus)
        voiceAlertActivation = view.findViewById(R.id.VoiceAlertActivation)
        tvVoiceStatus = view.findViewById(R.id.tvVoiceStatus)

        tvUserName.text = AuthManager.getUsername(requireContext()) ?: "User"
        tvUserPhone.text = AuthManager.getPhone(requireContext()) ?: "-"

        btnLoadContacts.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireContext() as Activity, arrayOf(Manifest.permission.READ_CONTACTS), REQ)
            } else importContacts()
        }

//        btnManualSOS.setOnClickListener {
//            val intent = Intent(requireContext(), SafetyService::class.java).apply {
//                action = SafetyService.ACTION_MANUAL_SOS
//            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                requireContext().startForegroundService(intent)
//            } else {
//                requireContext().startService(intent)
//            }
//            Toast.makeText(requireContext(), "Manual SOS Triggered", Toast.LENGTH_SHORT).show()
//        }

        switchCrashDetect.setOnCheckedChangeListener { _, isChecked ->
            val mainActivity = activity as? MainActivity
            if (isChecked) {
                if (!SafetyService.isCrashDetectionRunning) mainActivity?.startDetection()
            } else {
                if (SafetyService.isCrashDetectionRunning) mainActivity?.stopDetection()
            }
            updateCrashStatusText(isChecked)
        }

        voiceAlertActivation.setOnCheckedChangeListener { _, isChecked ->
            val intent = Intent(requireContext(), SafetyService::class.java).apply {
                action = if (isChecked) SafetyService.ACTION_START_VOICE else SafetyService.ACTION_STOP_VOICE
            }
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(intent)
                } else {
                    requireContext().startService(intent)
                }
            } else {
                requireContext().startService(intent)
            }
            updateVoiceStatusText(isChecked)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStates()
    }

    private fun updateStates() {
        if (!isAdded) return

        val isCrashRunning = SafetyService.isCrashDetectionRunning
        switchCrashDetect.isChecked = isCrashRunning
        updateCrashStatusText(isCrashRunning)

        val isVoiceRunning = SafetyService.isVoiceDetectionRunning
        voiceAlertActivation.isChecked = isVoiceRunning
        updateVoiceStatusText(isVoiceRunning)
    }

    private fun updateCrashStatusText(isRunning: Boolean) {
        tvCrashStatus.text = if (isRunning) "Active & Monitoring" else "Inactive"
        tvCrashStatus.alpha = if (isRunning) 1.0f else 0.6f
    }

    private fun updateVoiceStatusText(isRunning: Boolean) {
        tvVoiceStatus.text = if (isRunning) "Listening for Wake Word" else "Inactive"
        tvVoiceStatus.alpha = if (isRunning) 1.0f else 0.6f
    }

    private fun importContacts() {
        val list = ContactsHelper.loadContactsFromPhone(requireContext(), requireContext().contentResolver)
        ContactsHelper.saveContacts(requireContext(), list)
        Toast.makeText(requireContext(), "Imported ${list.size} contacts", Toast.LENGTH_SHORT).show()
    }
}
