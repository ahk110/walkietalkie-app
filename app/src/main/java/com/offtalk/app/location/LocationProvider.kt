package com.offtalk.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager

class LocationProvider(context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var currentLocation: Location? = null

    private val locationListener = LocationListener { location -> currentLocation = location }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, locationListener)
                currentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
        } catch (_: Exception) {}
    }

    fun stopLocationUpdates() {
        try { locationManager.removeUpdates(locationListener) } catch (_: Exception) {}
    }

    fun getLastKnownCoordinates(): String {
        val loc = currentLocation
        return if (loc != null) "${loc.latitude},${loc.longitude}" else "0.0,0.0"
    }
}
