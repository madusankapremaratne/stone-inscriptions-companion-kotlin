package org.sellipi.companion.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val packIsEmpty: Boolean = false,
    val answer: KnowledgeAnswer? = null
)

class AskViewModel(
    private val inscriptionId: String?,
    private val knowledgeRepo: KnowledgeRepository,
    private val inscriptionRepo: InscriptionRepository,
    private val lessonRepo: LessonRepository,
    private val includeDrafts: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(AskUiState())
    val uiState: StateFlow<AskUiState> = _uiState.asStateFlow()

    private var useCase: AskKnowledgeUseCase? = null
    private var context = QueryContext(inscriptionId = inscriptionId)

    init {
        viewModelScope.launch {
            val siteId = inscriptionId?.let { inscriptionRepo.getInscriptionById(it)?.siteId }
            context = QueryContext(inscriptionId = inscriptionId, siteId = siteId)
            val pack = runCatching { knowledgeRepo.loadPack() }.getOrNull()
            val retriever = pack?.let { KnowledgeRetriever(it, includeDrafts) }
            useCase = retriever?.let { AskKnowledgeUseCase(it) }
            _uiState.update { it.copy(isLoading = false, packIsEmpty = pack == null || pack.cards.isEmpty()) }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun submit(language: ContentLanguage) {
        val ask = useCase ?: return
        val query = _uiState.value.query
        if (query.isBlank()) return
        val result = ask.ask(query, language, context)
        result.lessons.forEach { lessonRepo.record(it, inscriptionId) }
        _uiState.update { it.copy(answer = result.answer) }
    }

    class Factory(
        private val inscriptionId: String?,
        private val knowledgeRepo: KnowledgeRepository,
        private val inscriptionRepo: InscriptionRepository,
        private val lessonRepo: LessonRepository,
        private val includeDrafts: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AskViewModel(inscriptionId, knowledgeRepo, inscriptionRepo, lessonRepo, includeDrafts) as T
        }
    }
}
