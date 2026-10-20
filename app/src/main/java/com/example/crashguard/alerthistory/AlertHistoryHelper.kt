package com.example.crashguard.alerthistory

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

object AlertHistoryHelper {
    private const val PREF = "alert_history_pref"
    private const val KEY = "alert_history_list"

    fun saveAlert(ctx: Context, type: String, location: String) {
        val time = DateFormat.getDateTimeInstance().format(Date())
        val history = getHistory(ctx).toMutableList()
        val id = System.currentTimeMillis()
        history.add(0, AlertRecord(id, type, time, location))
        saveHistory(ctx, history)
    }

    fun getHistory(ctx: Context): List<AlertRecord> {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        val out = mutableListOf<AlertRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                AlertRecord(
                    o.getLong("id"),
                    o.getString("type"),
                    o.getString("time"),
                    o.getString("location")
                )
            )
        }
        return out
    }

    fun deleteAlert(ctx: Context, id: Long) {
        val history = getHistory(ctx).filter { it.id != id }
        saveHistory(ctx, history)
    }

    private fun saveHistory(ctx: Context, history: List<AlertRecord>) {
        val arr = JSONArray()
        history.forEach { record ->
            val o = JSONObject()
            o.put("id", record.id)
            o.put("type", record.type)
            o.put("time", record.time)
            o.put("location", record.location)
            arr.put(o)
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit {
            putString(KEY, arr.toString())
        }
    }
}