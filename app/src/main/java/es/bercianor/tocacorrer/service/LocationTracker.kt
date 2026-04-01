package es.bercianor.tocacorrer.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wrapper over LocationManager to get GPS locations.
 * 
 * Uses native Android LocationManager (no Google Play Services).
 */
class LocationTracker(private val context: Context) {

    companion object {
        /** Maximum acceptable accuracy in meters — matches WorkoutService's tracking threshold. */
        const val GPS_ACCURACY_THRESHOLD_METERS = 20f
    }

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    /**
     * Checks if GPS is enabled.
     */
    fun isGpsEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /**
     * Checks if we have location permissions.
     */
    fun hasLocationPermission(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == 
               android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Gets location updates as a Flow.
     * 
     * @param minTimeMs Minimum time between updates (ms)
     * @param minDistanceM Minimum distance between updates (m). Defaults to 5f for tracking.
     *                     Pass 0f to receive all updates (e.g., for GPS warm-up accuracy checks).
     */
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(
        minTimeMs: Long = 1000L,
        minDistanceM: Float = 5f
    ): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        if (!isGpsEnabled()) {
            close(IllegalStateException("GPS disabled"))
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        // Request GPS updates
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            minTimeMs,
            minDistanceM,
            listener,
            Looper.getMainLooper()
        )

        // Also try to get last known location, but only if it's fresh (< 30 seconds old)
        // and meets the accuracy threshold — stale/inaccurate last-known locations
        // (e.g., 500 m accuracy from a network fix) must not become the first GPS point.
        val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        lastLocation?.let { loc ->
            val ageMs = System.currentTimeMillis() - loc.time
            if (ageMs < 30_000L && loc.accuracy <= GPS_ACCURACY_THRESHOLD_METERS) {
                trySend(loc)
            }
        }

        awaitClose {
            locationManager.removeUpdates(listener)
        }
    }

    /**
     * Calculates distance between two locations in meters.
     */
    fun calculateDistance(from: Location, to: Location): Float {
        return from.distanceTo(to)
    }

    /**
     * Calculates pace in minutes per kilometer.
     * 
     * @param distanceMeters Distance in meters
     * @param durationSeconds Duration in seconds
     * @return Pace in min/km (e.g., 5.5 = 5:30 min/km)
     */
    fun calculatePace(distanceMeters: Float, durationSeconds: Long): Double {
        if (distanceMeters <= 0) return 0.0
        
        val distanceKm = distanceMeters / 1000.0
        val durationMinutes = durationSeconds / 60.0
        
        return durationMinutes / distanceKm
    }
}
