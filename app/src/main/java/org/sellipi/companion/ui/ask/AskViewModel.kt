package org.sellipi.companion.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sellipi.companion.data.llm.LlmProvider
import org.sellipi.companion.data.llm.ModelState
import org.sellipi.companion.domain.knowledge.AskKnowledgeUseCase
import org.sellipi.companion.domain.knowledge.ContentLanguage
import org.sellipi.companion.domain.knowledge.KnowledgeAnswer
import org.sellipi.companion.domain.knowledge.KnowledgeRepository
import org.sellipi.companion.domain.knowledge.KnowledgeRetriever
import org.sellipi.companion.domain.knowledge.QueryContext
import org.sellipi.companion.domain.lessons.LessonRepository
import org.sellipi.companion.domain.repository.InscriptionRepository

data class AskUiState(
    val query: String = "",
    val isLoading: Boolean = true,
    val isAnswering: Boolean = false,
    val packIsEmpty: Boolean = false,
    val answer: KnowledgeAnswer? = null
)

/** Whether to show the "get on-device AI answers" prompt, and whether the model is in use. */
enum class ModelAvailability { READY, OFFER_DOWNLOAD, IN_PROGRESS, UNAVAILABLE }

class AskViewModel(
    private val inscriptionId: String?,
    private val knowledgeRepo: KnowledgeRepository,
    private val inscriptionRepo: InscriptionRepository,
    private val lessonRepo: LessonRepository,
    private val llmProvider: LlmProvider,
    private val includeDrafts: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(AskUiState())
    val uiState: StateFlow<AskUiState> = _uiState.asStateFlow()

    val modelAvailability: StateFlow<ModelAvailability> = llmProvider.modelManager.state
        .map { state ->
            when (state) {
                is ModelState.Ready -> ModelAvailability.READY
                is ModelState.NotDownloaded, is ModelState.Failed -> ModelAvailability.OFFER_DOWNLOAD
                is ModelState.Downloading, is ModelState.Verifying -> ModelAvailability.IN_PROGRESS
                is ModelState.NotConfigured, is ModelState.Ineligible -> ModelAvailability.UNAVAILABLE
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelAvailability.UNAVAILABLE)

    private var useCase: AskKnowledgeUseCase? = null
    private var context = QueryContext(inscriptionId = inscriptionId)

    init {
        llmProvider.warmUp()
        viewModelScope.launch {
            val siteId = inscriptionId?.let { inscriptionRepo.getInscriptionById(it)?.siteId }
            context = QueryContext(inscriptionId = inscriptionId, siteId = siteId)
            val pack = runCatching { knowledgeRepo.loadPack() }.getOrNull()
            val retriever = pack?.let { KnowledgeRetriever(it, includeDrafts) }
            useCase = retriever?.let { AskKnowledgeUseCase(it, llm = llmProvider::current) }
            _uiState.update { it.copy(isLoading = false, packIsEmpty = pack == null || pack.cards.isEmpty()) }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun submit(language: ContentLanguage) {
        val ask = useCase ?: return
        val query = _uiState.value.query
        if (query.isBlank() || _uiState.value.isAnswering) return
        _uiState.update { it.copy(isAnswering = true, answer = null) }
        viewModelScope.launch {
            val result = ask.ask(query, language, context)
            result.lessons.forEach { lessonRepo.record(it, inscriptionId) }
            _uiState.update { it.copy(answer = result.answer, isAnswering = false) }
        }
    }

    class Factory(
        private val inscriptionId: String?,
        private val knowledgeRepo: KnowledgeRepository,
        private val inscriptionRepo: InscriptionRepository,
        private val lessonRepo: LessonRepository,
        private val llmProvider: LlmProvider,
        private val includeDrafts: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AskViewModel(inscriptionId, knowledgeRepo, inscriptionRepo, lessonRepo, llmProvider, includeDrafts) as T
        }
    }
}
