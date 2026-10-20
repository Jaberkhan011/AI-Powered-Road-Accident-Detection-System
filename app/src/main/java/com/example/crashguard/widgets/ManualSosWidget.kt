package com.example.crashguard.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.crashguard.R
import com.example.crashguard.SafetyService

class ManualSosWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_manual_sos)
            
            val intent = Intent(context, SafetyService::class.java).apply {
                action = SafetyService.ACTION_MANUAL_SOS
            }
            
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            } else {
                PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            
            views.setOnClickPendingIntent(R.id.btnWidgetSos, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
