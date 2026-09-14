package org.sellipi.companion.ui.inscription

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

data class InscriptionDetailUiState(
    val inscription: Inscription? = null,
    val lines: List<TranscriptionLine> = emptyList(),
    val isLoading: Boolean = true
)

class InscriptionDetailViewModel(
    private val inscriptionId: String,
    private val getInscriptionDetailsUseCase: GetInscriptionDetailsUseCase
) : ViewModel() {

    private val _inscription = MutableStateFlow<Inscription?>(null)

    val uiState: StateFlow<InscriptionDetailUiState> = combine(
        _inscription,
        getInscriptionDetailsUseCase.getTranscriptionWithGlyphs(inscriptionId)
    ) { insc, lines ->
        InscriptionDetailUiState(
            inscription = insc,
            lines = lines,
            isLoading = insc == null
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        InscriptionDetailUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            _inscription.value = getInscriptionDetailsUseCase.getInscription(inscriptionId)
        }
    }

    class Factory(
        private val inscriptionId: String,
        private val getInscriptionDetailsUseCase: GetInscriptionDetailsUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InscriptionDetailViewModel(inscriptionId, getInscriptionDetailsUseCase) as T
        }
    }
}
