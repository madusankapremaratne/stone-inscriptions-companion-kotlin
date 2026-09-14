package org.sellipi.companion.ui.overlay

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
import org.sellipi.companion.domain.model.GlyphOccurrence
import org.sellipi.companion.domain.model.Inscription
import org.sellipi.companion.domain.model.InscriptionQuad
import org.sellipi.companion.domain.model.QuadPoint
import org.sellipi.companion.domain.model.TranscriptionLine
import org.sellipi.companion.domain.usecase.GetInscriptionDetailsUseCase
import org.sellipi.companion.engine.homography.HomographyCalculator

data class CameraOverlayUiState(
    val inscription: Inscription? = null,
    val lines: List<TranscriptionLine> = emptyList(),
    val quad: InscriptionQuad = InscriptionQuad(
        topLeft = QuadPoint(100f, 250f),
        topRight = QuadPoint(900f, 250f),
        bottomRight = QuadPoint(900f, 800f),
        bottomLeft = QuadPoint(100f, 800f)
    ),
    val homographyMatrix: FloatArray = FloatArray(9),
    val selectedGlyph: GlyphOccurrence? = null,
    val isFrozen: Boolean = false,
    val isLoading: Boolean = true
)

class CameraOverlayViewModel(
    private val inscriptionId: String,
    private val getInscriptionDetailsUseCase: GetInscriptionDetailsUseCase,
    private val homographyCalculator: HomographyCalculator = HomographyCalculator()
) : ViewModel() {

    private val _inscription = MutableStateFlow<Inscription?>(null)
    private val _quad = MutableStateFlow(
        InscriptionQuad(
            topLeft = QuadPoint(100f, 250f),
            topRight = QuadPoint(900f, 250f),
            bottomRight = QuadPoint(900f, 800f),
            bottomLeft = QuadPoint(100f, 800f)
        )
    )
    private val _selectedGlyph = MutableStateFlow<GlyphOccurrence?>(null)
    private val _isFrozen = MutableStateFlow(false)

    val uiState: StateFlow<CameraOverlayUiState> = combine(
        _inscription,
        getInscriptionDetailsUseCase.getTranscriptionWithGlyphs(inscriptionId),
        _quad,
        _selectedGlyph,
        _isFrozen
    ) { insc, lines, quad, selectedGlyph, isFrozen ->
        val matrix = homographyCalculator.computeHomographyMatrix(quad)
        CameraOverlayUiState(
            inscription = insc,
            lines = lines,
            quad = quad,
            homographyMatrix = matrix,
            selectedGlyph = selectedGlyph,
            isFrozen = isFrozen,
            isLoading = insc == null
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CameraOverlayUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            _inscription.value = getInscriptionDetailsUseCase.getInscription(inscriptionId)
        }
    }

    fun updateCorner(cornerIndex: Int, newX: Float, newY: Float) {
        val current = _quad.value
        val point = QuadPoint(newX, newY)
        _quad.value = when (cornerIndex) {
            0 -> current.copy(topLeft = point)
            1 -> current.copy(topRight = point)
            2 -> current.copy(bottomRight = point)
            3 -> current.copy(bottomLeft = point)
            else -> current
        }
    }

    fun toggleFreezeFrame() {
        _isFrozen.value = !_isFrozen.value
    }

    fun onTouchScreen(touchX: Float, touchY: Float) {
        val currentState = uiState.value
        val invMatrix = homographyCalculator.invertMatrix(currentState.homographyMatrix) ?: return

        for (line in currentState.lines) {
            for (glyph in line.glyphs) {
                if (homographyCalculator.isTouchInsideNormalizedBbox(
                        invMatrix, touchX, touchY,
                        glyph.bboxX, glyph.bboxY, glyph.bboxW, glyph.bboxH
                    )
                ) {
                    _selectedGlyph.value = glyph
                    return
                }
            }
        }
        _selectedGlyph.value = null
    }

    fun dismissGlyphSheet() {
        _selectedGlyph.value = null
    }

    class Factory(
        private val inscriptionId: String,
        private val getInscriptionDetailsUseCase: GetInscriptionDetailsUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CameraOverlayViewModel(inscriptionId, getInscriptionDetailsUseCase) as T
        }
    }
}
