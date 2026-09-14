package org.sellipi.companion.engine.ar

import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream

enum class ArTrackingStatus {
    SEARCHING,
    TRACKING_SURFACE,
    TRACKING_MARKER,
    PAUSED,
    UNSUPPORTED
}

data class ArTrackedTarget(
    val inscriptionId: String,
    val centerPoseMatrix: FloatArray,
    val extentX: Float,
    val extentZ: Float,
    val trackingQuality: Float
)

class ArCoreSessionManager(private val context: Context) {

    private val _trackingStatus = MutableStateFlow(ArTrackingStatus.SEARCHING)
    val trackingStatus: StateFlow<ArTrackingStatus> = _trackingStatus.asStateFlow()

    private val _trackedTarget = MutableStateFlow<ArTrackedTarget?>(null)
    val trackedTarget: StateFlow<ArTrackedTarget?> = _trackedTarget.asStateFlow()

    fun checkArCoreAvailability(callback: (Boolean) -> Unit) {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability.isTransient) {
            // Re-query after transient check
            callback(false)
        } else {
            callback(availability.isSupported)
        }
    }

    fun configureAugmentedImages(session: Session, databaseStream: InputStream? = null): Config {
        val config = Config(session)
        config.focusMode = Config.FocusMode.AUTO
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE

        if (databaseStream != null) {
            try {
                val imageDatabase = AugmentedImageDatabase.deserialize(session, databaseStream)
                config.augmentedImageDatabase = imageDatabase
            } catch (e: Exception) {
                // Graceful fallback to default config
            }
        }
        return config
    }

    fun onArFrameUpdated(augmentedImages: Collection<AugmentedImage>) {
        val tracked = augmentedImages.firstOrNull { it.trackingState == TrackingState.TRACKING }

        if (tracked != null) {
            val poseMatrix = FloatArray(16)
            tracked.centerPose.toMatrix(poseMatrix, 0)

            _trackedTarget.value = ArTrackedTarget(
                inscriptionId = tracked.name ?: "INSC_ACTIVE",
                centerPoseMatrix = poseMatrix,
                extentX = tracked.extentX,
                extentZ = tracked.extentZ,
                trackingQuality = 0.95f
            )
            _trackingStatus.value = ArTrackingStatus.TRACKING_SURFACE
        } else {
            if (_trackingStatus.value == ArTrackingStatus.TRACKING_SURFACE) {
                _trackingStatus.value = ArTrackingStatus.PAUSED
            }
        }
    }
}
