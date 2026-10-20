package com.example.crashguard

import android.content.Context
import android.telephony.SmsManager

object SmsHelper {

    fun makeUrgentAlert(userName: String, lat: Double?, lon: Double?, severity: String?, source: String): String {
        val location = if (lat != null && lon != null)
            "https://maps.google.com/?q=$lat,$lon"
        else "Location unavailable"

        val severityText = if (severity != null) "\n⚠️ Severity: $severity" else ""
        
        val header = when {
            source.contains("Automatic", true) -> "🚨 URGENT: No Response Detected\n\nA crash was detected for $userName, and they did not respond."
            source.contains("Voice", true) -> "🚨 URGENT: Voice Emergency Alert\n\nA voice-activated emergency was triggered by $userName."
            else -> "🚨 URGENT: Manual SOS Alert\n\nA manual SOS alert was sent by $userName."
        }

        return "$header\n\n📍 Last Known Location: $location$severityText\n\nImmediate assistance may be required."
    }

    fun makeAlert(msg: String, lat: Double?, lon: Double?): String {
        return if (lat != null && lon != null)
            "$msg\nLocation: https://www.google.com/maps?q=$lat,$lon"
        else "$msg\nLocation unavailable"
    }

    fun send(ctx: Context, phone: String, msg: String) {
        val sms = SmsManager.getDefault()
        val parts = sms.divideMessage(msg)
        sms.sendMultipartTextMessage(phone, null, parts, null, null)
    }

    fun sendAll(ctx: Context, numbers: List<String>, msg: String) {
        numbers.forEach { send(ctx, it, msg) }
    }
}