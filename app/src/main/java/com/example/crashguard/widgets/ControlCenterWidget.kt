package com.example.crashguard.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.crashguard.R
import com.example.crashguard.SafetyService

class ControlCenterWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            "TOGGLE_CRASH" -> {
                val action = if (SafetyService.isCrashDetectionRunning) SafetyService.ACTION_STOP_CRASH else SafetyService.ACTION_START_CRASH
                sendServiceAction(context, action)
            }
            "TOGGLE_VOICE" -> {
                val action = if (SafetyService.isVoiceDetectionRunning) SafetyService.ACTION_STOP_VOICE else SafetyService.ACTION_START_VOICE
                sendServiceAction(context, action)
            }
        }
        
        // Refresh all widgets of this type
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ControlCenterWidget::class.java))
        onUpdate(context, appWidgetManager, ids)
    }

    private fun sendServiceAction(context: Context, serviceAction: String) {
        val intent = Intent(context, SafetyService::class.java).apply {
            action = serviceAction
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_control_center)

        // Set up toggle crash intent on the whole row (RelativeLayout)
        val crashIntent = Intent(context, ControlCenterWidget::class.java).apply { action = "TOGGLE_CRASH" }
        val crashPI = PendingIntent.getBroadcast(context, 1, crashIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.toggleCrash, crashPI)

        // Set up toggle voice intent on the whole row (RelativeLayout)
        val voiceIntent = Intent(context, ControlCenterWidget::class.java).apply { action = "TOGGLE_VOICE" }
        val voicePI = PendingIntent.getBroadcast(context, 2, voiceIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.toggleVoice, voicePI)

        // Colors
        val activeColor = 0xFF4CAF50.toInt() // Green
        val inactiveColor = 0xFFD32F2F.toInt() // Red
        val statusGrey = 0xFF757575.toInt()

        // Update Crash Detection UI
        if (SafetyService.isCrashDetectionRunning) {
            views.setTextViewText(R.id.tvCrashStatus, "Active")
            views.setTextColor(R.id.tvCrashStatus, activeColor)
            views.setInt(R.id.switchCrashDetect, "setColorFilter", activeColor)
        } else {
            views.setTextViewText(R.id.tvCrashStatus, "Inactive")
            views.setTextColor(R.id.tvCrashStatus, statusGrey)
            views.setInt(R.id.switchCrashDetect, "setColorFilter", inactiveColor)
        }

        // Update Voice Activation UI
        if (SafetyService.isVoiceDetectionRunning) {
            views.setTextViewText(R.id.tvVoiceStatus, "Active")
            views.setTextColor(R.id.tvVoiceStatus, activeColor)
            views.setInt(R.id.VoiceAlertActivation, "setColorFilter", activeColor)
        } else {
            views.setTextViewText(R.id.tvVoiceStatus, "Inactive")
            views.setTextColor(R.id.tvVoiceStatus, statusGrey)
            views.setInt(R.id.VoiceAlertActivation, "setColorFilter", inactiveColor)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
