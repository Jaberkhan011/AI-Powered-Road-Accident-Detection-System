package com.example.crashguard

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

class LocationHelper(private val ctx: Context) {

    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    interface Callback { fun onLocation(loc: Location) }

    private var callback: Callback? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            callback?.onLocation(loc)
        }
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(p: String?, s: Int, b: Bundle?) {}
    }

    @SuppressLint("MissingPermission")
    fun start(cb: Callback) {
        callback = cb
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5f, listener)
//        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
//            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 10f, listener)
    }

    fun stop() {
        lm.removeUpdates(listener)
        callback = null
    }
}