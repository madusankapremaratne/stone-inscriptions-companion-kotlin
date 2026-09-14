package org.sellipi.companion.ui.evolution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.sellipi.companion.domain.model.Letter
import org.sellipi.companion.domain.model.LetterForm
import org.sellipi.companion.domain.repository.EvolutionRepository
import org.sellipi.companion.domain.usecase.GetLetterEvolutionUseCase
import org.sellipi.companion.domain.usecase.LetterEvolutionData

data class LetterEvolutionUiState(
    val allLetters: List<Letter> = emptyList(),
    val currentLetterId: String = "L01",
    val evolutionData: LetterEvolutionData? = null,
    val selectedPeriodIndex: Int = 0,
    val morphProgress: Float = 0f,
    val isLoading: Boolean = true
)

class LetterEvolutionViewModel(
    initialLetterId: String = "L01",
    private val evolutionRepo: EvolutionRepository,
    private val getLetterEvolutionUseCase: GetLetterEvolutionUseCase
) : ViewModel() {

    private val _currentLetterId = MutableStateFlow(initialLetterId)
    private val _selectedPeriodIndex = MutableStateFlow(0)
    private val _morphProgress = MutableStateFlow(0f)
    private val _allLetters = MutableStateFlow<List<Letter>>(emptyList())

    val uiState: StateFlow<LetterEvolutionUiState> = _currentLetterId.flatMapLatest { letterId ->
        getLetterEvolutionUseCase.getEvolutionForLetter(letterId)
    }.flatMapLatest { data ->
        MutableStateFlow(
            LetterEvolutionUiState(
                allLetters = _allLetters.value,
                currentLetterId = _currentLetterId.value,
                evolutionData = data,
                selectedPeriodIndex = _selectedPeriodIndex.value,
                morphProgress = _morphProgress.value,
                isLoading = data == null
            )
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LetterEvolutionUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            evolutionRepo.getAllLetters().collect { letters ->
                _allLetters.value = letters
            }
        }
    }

    fun selectLetter(letterId: String) {
        _currentLetterId.value = letterId
    }

    fun selectPeriod(index: Int) {
        _selectedPeriodIndex.value = index
        _morphProgress.value = index.toFloat()
    }

    fun updateScrubberProgress(progress: Float) {
        _morphProgress.value = progress
        _selectedPeriodIndex.value = progress.toInt().coerceIn(0, 17)
    }

    class Factory(
        private val initialLetterId: String,
        private val evolutionRepo: EvolutionRepository,
        private val getLetterEvolutionUseCase: GetLetterEvolutionUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LetterEvolutionViewModel(initialLetterId, evolutionRepo, getLetterEvolutionUseCase) as T
        }
    }
}
