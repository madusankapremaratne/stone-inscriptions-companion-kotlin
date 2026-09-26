package org.sellipi.companion.data.llm

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Whether this phone may run the on-device model at all. Gemma 3 1B int4 needs roughly 1 GB
 * while running, so 4 GB phones (e.g. Galaxy M21) are allowed; the evaluation screen measures
 * how usable it is. Overlay mode and extractive Q&A never depend on this.
 */
data class DeviceCapability(
    val eligible: Boolean,
    val totalRamGb: Double,
    val reason: IneligibleReason?
) {
    enum class IneligibleReason { NOT_ARM64, LOW_RAM }

    companion object {
        const val MIN_RAM_GB = 3.5

        fun check(context: Context): DeviceCapability {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val ramGb = info.totalMem / (1024.0 * 1024 * 1024)
            val reason = when {
                "arm64-v8a" !in Build.SUPPORTED_ABIS -> IneligibleReason.NOT_ARM64
                ramGb < MIN_RAM_GB -> IneligibleReason.LOW_RAM
                else -> null
            }
            return DeviceCapability(reason == null, ramGb, reason)
        }

        /** Skip generation when the phone is already hot (outdoor sun is the normal case at sites). */
        fun thermalAllowsGeneration(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.currentThermalStatus < PowerManager.THERMAL_STATUS_SEVERE
        }
    }
}
