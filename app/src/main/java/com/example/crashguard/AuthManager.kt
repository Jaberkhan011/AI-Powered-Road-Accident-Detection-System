package com.example.crashguard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object AuthManager {
    private const val PREF = "accident_prefs"
    private const val KEY_LOGGED_IN = "logged_in"
    private const val KEY_USER = "user"
    private const val KEY_PHONE = "phone"
    private const val KEY_CRASH_ENABLED = "crash_enabled"
    private const val KEY_VOICE_ENABLED = "voice_enabled"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // Save username + phone for showing on HomeActivity
    fun saveLogin(ctx: Context, username: String, phone: String) {
        prefs(ctx).edit {
            putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_USER, username)
                .putString(KEY_PHONE, phone)
        }
    }

    fun clearLogin(ctx: Context) {
        prefs(ctx).edit { clear() }
    }

    fun isLoggedIn(ctx: Context) = prefs(ctx).getBoolean(KEY_LOGGED_IN, false)

    fun getUsername(ctx: Context) = prefs(ctx).getString(KEY_USER, null)

    fun getPhone(ctx: Context) = prefs(ctx).getString(KEY_PHONE, null)

    fun setCrashEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit { putBoolean(KEY_CRASH_ENABLED, enabled) }
    }

    fun isCrashEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_CRASH_ENABLED, false)

    fun setVoiceEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit { putBoolean(KEY_VOICE_ENABLED, enabled) }
    }

    fun isVoiceEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_VOICE_ENABLED, false)
}