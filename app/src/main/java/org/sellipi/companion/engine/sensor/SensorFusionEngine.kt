package org.sellipi.companion.engine.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

data class GeoLocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float
)

class SensorFusionEngine(context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): GeoLocationData? {
        return try {
            val cancellationToken = CancellationTokenSource()
            val location: Location? = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).await()

            location?.let {
                GeoLocationData(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracyMeters = it.accuracy
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Estimates scale in millimeters per pixel based on camera distance (in meters),
     * sensor focal length (in mm), and image sensor resolution width (in px).
     */
    fun calculateMillimetersPerPixel(
        distanceMeters: Float,
        focalLengthMm: Float = 4.38f,
        sensorWidthMm: Float = 6.4f,
        imageWidthPx: Int = 1920
    ): Float {
        if (distanceMeters <= 0f || focalLengthMm <= 0f || imageWidthPx <= 0) return 0.5f
        // Field of view width at surface distance: (sensorWidth * distance / focalLength) * 1000 mm
        val fovWidthMm = (sensorWidthMm * (distanceMeters * 1000f)) / focalLengthMm
        return fovWidthMm / imageWidthPx
    }
}
