package com.example.crashguard.fragments

import android.os.Bundle
import android.text.Html
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.crashguard.R

class AboutProject : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about_project, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTechSpecs = view.findViewById<TextView>(R.id.tvTechSpecs)
        val tvHowToUse = view.findViewById<TextView>(R.id.tvHowToUse)
        
        val techReport = """
            <b>1. Architecture & Workflow</b><br/>
            The system employs an <b>Edge-Computing Architecture</b>, performing all ML inferences locally to ensure privacy and low latency. 
            The workflow consists of a multi-stage pipeline:<br/>
            • <b>Gating Stage:</b> Uses an IMU Motion Gate and RMS Energy Gate to filter idle states, significantly reducing CPU usage.<br/>
            • <b>Inference Stage:</b> Dual-modal sensing runs parallel CNN models for kinematic and acoustic detection.<br/>
            • <b>Confirmation Stage:</b> A Two-Window logic requires agreement between consecutive predictions to eliminate false positives.<br/><br/>

            <b>2. Model Specifications</b><br/>
            • <b>Crash Detection:</b> A 3-class CNN model (Quantized TFLite) trained to distinguish between Vehicle Accidents, Pedestrian Impacts, and Normal Motion. It processes 8-feature windows (Acc, Gyro, Magnitudes) at 50Hz.<br/>
            • <b>Wake-Word Detection:</b> A specialized CNN model optimized for the keyword "Trigger Backup". It processes MFCC features (13 coefficients, 98 frames) extracted from 8kHz mono audio.<br/><br/>

            <b>3. Key APIs & Technologies</b><br/>
            • <b>TensorFlow Lite:</b> Local execution of quantized neural networks.<br/>
            • <b>Google Play Services (Fused Location):</b> High-accuracy location fetching when online.<br/>
            • <b>Hardware GNSS:</b> Fallback location tracking for offline environments.<br/>
            • <b>Android Sensor Framework:</b> High-frequency sampling of Accelerometer and Gyroscope data.<br/>
            • <b>Telephony/SMS API:</b> Direct dispatch of emergency coordinates to trusted contacts.
        """.trimIndent()

        tvTechSpecs.text = Html.fromHtml(techReport, Html.FROM_HTML_MODE_COMPACT)
        
        // Setting How to Use text from strings.xml with HTML formatting support
        tvHowToUse.text = Html.fromHtml(getString(R.string.HowToUseSteps), Html.FROM_HTML_MODE_COMPACT)
    }
}
