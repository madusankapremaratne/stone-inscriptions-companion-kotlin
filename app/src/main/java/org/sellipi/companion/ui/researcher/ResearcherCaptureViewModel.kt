package org.sellipi.companion.ui.researcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sellipi.companion.domain.model.CaptureSession
import org.sellipi.companion.domain.usecase.SaveResearcherCaptureUseCase
import org.sellipi.companion.engine.sensor.SensorFusionEngine
import java.util.UUID

data class ResearcherUiState(
    val isUnlocked: Boolean = false,
    val passcodeError: Boolean = false,
    val capturedSectors: Set<Int> = emptySet(), // 8 sectors around the dome (0..7)
    val currentPoseMatrix: String = "[1.0, 0.0, 0.0, 0.0; 0.0, 1.0, 0.0, 0.0; 0.0, 0.0, 1.0, 1.2; 0.0, 0.0, 0.0, 1.0]",
    val currentScaleMmPerPx: Float = 0.32f,
    val currentGpsLat: Double = 8.3508,
    val currentGpsLon: Double = 80.5097,
    val currentGpsAccuracy: Float = 2.4f,
    val permitReference: String = "ARCH-PERMIT-2026-RESEARCH",
    val exportedBundlePath: String? = null
)

class ResearcherCaptureViewModel(
    private val inscriptionId: String = "INSC_ACTIVE",
    private val saveCaptureUseCase: SaveResearcherCaptureUseCase,
    private val sensorEngine: SensorFusionEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResearcherUiState())
    val uiState: StateFlow<ResearcherUiState> = _uiState.asStateFlow()

    init {
        refreshSensors()
    }

    fun unlockWithPasscode(passcode: String) {
        // Default research team passcode
        if (passcode == "1837" || passcode == "sellipi") {
            _uiState.value = _uiState.value.copy(isUnlocked = true, passcodeError = false)
            refreshSensors()
        } else {
            _uiState.value = _uiState.value.copy(passcodeError = true)
        }
    }

    fun refreshSensors() {
        viewModelScope.launch {
            val loc = sensorEngine.getCurrentLocation()
            if (loc != null) {
                _uiState.value = _uiState.value.copy(
                    currentGpsLat = loc.latitude,
                    currentGpsLon = loc.longitude,
                    currentGpsAccuracy = loc.accuracyMeters
                )
            }
            val scale = sensorEngine.calculateMillimetersPerPixel(distanceMeters = 0.85f)
            _uiState.value = _uiState.value.copy(currentScaleMmPerPx = scale)
        }
    }

    fun captureCurrentSector(sectorIndex: Int) {
        val updated = _uiState.value.capturedSectors + sectorIndex
        _uiState.value = _uiState.value.copy(capturedSectors = updated)

        viewModelScope.launch {
            val session = CaptureSession(
                id = UUID.randomUUID().toString(),
                inscriptionId = inscriptionId,
                deviceModel = android.os.Build.MODEL ?: "Android_Device",
                timestamp = System.currentTimeMillis(),
                gpsLat = _uiState.value.currentGpsLat,
                gpsLon = _uiState.value.currentGpsLon,
                gpsAccuracy = _uiState.value.currentGpsAccuracy,
                poseMatrix = _uiState.value.currentPoseMatrix,
                scaleMmPerPx = _uiState.value.currentScaleMmPerPx,
                imagePaths = listOf("captures/sector_$sectorIndex.jpg"),
                permitReference = _uiState.value.permitReference
            )
            saveCaptureUseCase(session)
        }
    }

    fun exportEncryptedBundle() {
        _uiState.value = _uiState.value.copy(
            exportedBundlePath = "/sdcard/Documents/Sellipi_Export_${System.currentTimeMillis()}.zip"
        )
    }

    class Factory(
        private val inscriptionId: String,
        private val saveCaptureUseCase: SaveResearcherCaptureUseCase,
        private val sensorEngine: SensorFusionEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ResearcherCaptureViewModel(inscriptionId, saveCaptureUseCase, sensorEngine) as T
        }
    }
}
