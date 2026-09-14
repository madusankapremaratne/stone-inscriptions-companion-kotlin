package org.sellipi.companion.ui.ar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.sellipi.companion.domain.model.Inscription
import org.sellipi.companion.domain.model.TranscriptionLine
import org.sellipi.companion.domain.usecase.GetInscriptionDetailsUseCase
import org.sellipi.companion.engine.ar.ArCoreSessionManager
import org.sellipi.companion.engine.ar.ArTrackedTarget
import org.sellipi.companion.engine.ar.ArTrackingStatus

data class ArUiState(
    val inscription: Inscription? = null,
    val lines: List<TranscriptionLine> = emptyList(),
    val isArSupported: Boolean = true,
    val trackingStatus: ArTrackingStatus = ArTrackingStatus.SEARCHING,
    val trackedTarget: ArTrackedTarget? = null,
    val isLoading: Boolean = true
)

class ArViewModel(
    private val inscriptionId: String,
    private val getInscriptionDetailsUseCase: GetInscriptionDetailsUseCase,
    val arSessionManager: ArCoreSessionManager
) : ViewModel() {

    private val _inscription = MutableStateFlow<Inscription?>(null)
    private val _isArSupported = MutableStateFlow(true)

    val uiState: StateFlow<ArUiState> = combine(
        _inscription,
        getInscriptionDetailsUseCase.getTranscriptionWithGlyphs(inscriptionId),
        _isArSupported,
        arSessionManager.trackingStatus,
        arSessionManager.trackedTarget
    ) { insc, lines, isSupported, status, target ->
        ArUiState(
            inscription = insc,
            lines = lines,
            isArSupported = isSupported,
            trackingStatus = status,
            trackedTarget = target,
            isLoading = insc == null
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ArUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            _inscription.value = getInscriptionDetailsUseCase.getInscription(inscriptionId)
            arSessionManager.checkArCoreAvailability { supported ->
                _isArSupported.value = supported
            }
        }
    }

    class Factory(
        private val inscriptionId: String,
        private val getInscriptionDetailsUseCase: GetInscriptionDetailsUseCase,
        private val arSessionManager: ArCoreSessionManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ArViewModel(inscriptionId, getInscriptionDetailsUseCase, arSessionManager) as T
        }
    }
}
